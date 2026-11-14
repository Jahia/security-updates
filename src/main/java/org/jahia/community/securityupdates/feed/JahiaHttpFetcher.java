package org.jahia.community.securityupdates.feed;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jahia.services.notification.HttpClientService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link HttpFetcher} over Jahia's {@link HttpClientService}, so that Jahia's proxy settings and its outbound
 * request screening ({@code SsrfProtectionDnsResolver}, Jahia 8.2.3.7 and later) apply. Every request goes through
 * the screened client of {@link HttpClientService#getHttpClient(String)}: its DNS resolver refuses internal
 * addresses when the connection is opened, which also covers DNS rebinding. This class never uses an unscreened
 * client ({@code getHttpClientForConfiguredTarget}): the URLs come from settings an {@code adminTemplates} user
 * can change. The status code is kept
 * for every response, which {@code HttpClientService.executeGet} does not do. Redirects are not followed and
 * every request is bounded in time and size.
 *
 * <p>Time bounds: the connection, then each read of the socket (response timeout), and the whole request (read
 * deadline, counted from the start of the request): a watchdog thread aborts the request when the read deadline
 * passes, so a body that trickles in, each byte within the response timeout, cannot hold the check thread.
 *
 * <p>The HTTP client reads the rest of a body that its handler did not read to the end (to reuse the connection),
 * with no size or time bound. So a body that is not read to the end (an error status, a body too large, the
 * deadline) is never left to the client: the request is aborted, which closes the connection instead.
 */
@Component(service = HttpFetcher.class)
public class JahiaHttpFetcher implements HttpFetcher {

    /** Bytes kept of the body of a response whose status is not 200. */
    static final int MAX_ERROR_BODY_BYTES = 4096;
    static final String DEADLINE_EXCEEDED = "Response read deadline exceeded";
    private static final long WATCHDOG_KEEP_ALIVE_SECONDS = 30;

    private final Timeouts timeouts;
    /** Requests in flight, aborted by {@link #cancelAll()}. */
    private final Set<HttpGet> inFlight = ConcurrentHashMap.newKeySet();
    /** Aborts each request at its read deadline; its daemon thread ends when no request is in flight. */
    private final ScheduledThreadPoolExecutor watchdog;
    private HttpClientService httpClientService;

    public JahiaHttpFetcher() {
        this(Timeouts.DEFAULT);
    }

    JahiaHttpFetcher(Timeouts timeouts) {
        this.timeouts = timeouts;
        watchdog = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "security-updates-http-deadline");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.setRemoveOnCancelPolicy(true);
        watchdog.setKeepAliveTime(WATCHDOG_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
        watchdog.allowCoreThreadTimeOut(true);
    }

    /** Static mandatory reference, set by Declarative Services before the component is used. */
    @Reference
    void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers, int maxBytes) throws IOException {
        HttpClientService service = httpClientService;
        if (service == null) {
            throw new IOException("Jahia HTTP client service not available");
        }
        CloseableHttpClient client = service.getHttpClient(url);
        HttpGet request = new HttpGet(url);
        RequestConfig config = connectTimeout(service.getRequestConfigBuilder(client), timeouts.connect)
                .setConnectionRequestTimeout(Timeout.of(timeouts.connect))
                .setResponseTimeout(Timeout.of(timeouts.response))
                .setRedirectsEnabled(false)
                .build();
        request.setConfig(config);
        headers.forEach(request::addHeader);

        long deadlineNanos = timeouts.readDeadline.toNanos();
        long deadline = System.nanoTime() + deadlineNanos;
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> abortAtDeadline;
        try {
            abortAtDeadline = watchdog.schedule(() -> {
                timedOut.set(true);
                request.cancel();
            }, deadlineNanos, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
            throw new IOException("The HTTP fetcher is stopped", e);
        }
        inFlight.add(request);
        AtomicReference<HttpResult> handled = new AtomicReference<>();
        try {
            return client.execute(request, response -> {
                HttpResult result = read(request, response, maxBytes, deadline);
                handled.set(result);
                return result;
            });
        } catch (IOException e) {
            HttpResult result = handled.get();
            if (result != null) {
                // the response was read; only the release of the aborted connection failed
                return result;
            }
            if (timedOut.get()) {
                throw new SocketTimeoutException(DEADLINE_EXCEEDED);
            }
            throw e;
        } finally {
            abortAtDeadline.cancel(false);
            inFlight.remove(request);
        }
    }

    /**
     * Jahia owns and shares the HTTP client, so its connection manager cannot be given a per-request connect
     * timeout; the request-level setting, deprecated in HttpClient 5.2 in favour of the connection manager, is the
     * only way to bound the connection of one request.
     */
    @SuppressWarnings({"deprecation", "java:S1874"})
    private static RequestConfig.Builder connectTimeout(RequestConfig.Builder builder, Duration connect) {
        return builder.setConnectTimeout(Timeout.of(connect));
    }

    /**
     * Reads a 200 body to its end, or aborts the request. Reads at most {@link #MAX_ERROR_BODY_BYTES} of any other
     * status, then aborts the request: the status is what matters, and the rest of the body is never read.
     */
    private static HttpResult read(HttpGet request, ClassicHttpResponse response, int maxBytes, long deadline)
            throws IOException {
        int status = response.getCode();
        HttpEntity entity = response.getEntity();
        if (status != HttpStatus.OK) {
            return new HttpResult(status, readErrorBody(request, entity, deadline));
        }
        try {
            if (entity != null && entity.getContentLength() > maxBytes) {
                throw new ResponseTooLargeException(maxBytes);
            }
            return new HttpResult(status, BoundedReader.read(entity == null ? null : entity.getContent(), maxBytes,
                    deadline, false));
        } catch (IOException | RuntimeException e) {
            request.cancel();
            throw e;
        }
    }

    /** The first bytes of an error body, or what came of them before the deadline or a failure; then aborts. */
    private static String readErrorBody(HttpGet request, HttpEntity entity, long deadline) {
        try {
            InputStream body = entity == null ? null : entity.getContent();
            return BoundedReader.read(body, MAX_ERROR_BODY_BYTES, deadline, true);
        } catch (IOException e) {
            return "";
        } finally {
            request.cancel();
        }
    }

    @Override
    public void cancelAll() {
        for (HttpGet request : inFlight) {
            request.cancel();
        }
    }

    /** Aborts the requests in flight and stops the watchdog thread; later requests fail. */
    @Deactivate
    void deactivate() {
        cancelAll();
        watchdog.shutdownNow();
    }

    /** Time bounds of one request. */
    static final class Timeouts {
        static final Timeouts DEFAULT = new Timeouts(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60));

        private final Duration connect;
        private final Duration response;
        private final Duration readDeadline;

        /**
         * @param connect      to connect, and to get a connection from the pool
         * @param response     between two packets of the response
         * @param readDeadline to read the whole body
         */
        Timeouts(Duration connect, Duration response, Duration readDeadline) {
            this.connect = connect;
            this.response = response;
            this.readDeadline = readDeadline;
        }

        Duration getConnect() {
            return connect;
        }

        Duration getResponse() {
            return response;
        }

        Duration getReadDeadline() {
            return readDeadline;
        }
    }
}
