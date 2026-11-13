package org.jahia.community.securityupdates.feed;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jahia.services.notification.HttpClientService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Time bounds: the connection, then each read of the socket (response timeout), then the whole body (read
 * deadline, checked after each read): a response can take at most about read deadline + response timeout.
 */
@Component(service = HttpFetcher.class)
public class JahiaHttpFetcher implements HttpFetcher {

    /** Bytes kept of the body of a response whose status is not 200. */
    static final int MAX_ERROR_BODY_BYTES = 4096;

    private final Timeouts timeouts;
    /** Requests in flight, aborted by {@link #cancelAll()}. */
    private final Set<HttpGet> inFlight = ConcurrentHashMap.newKeySet();
    private HttpClientService httpClientService;

    public JahiaHttpFetcher() {
        this(Timeouts.DEFAULT);
    }

    JahiaHttpFetcher(Timeouts timeouts) {
        this.timeouts = timeouts;
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
        RequestConfig config = service.getRequestConfigBuilder(client)
                .setConnectTimeout(Timeout.of(timeouts.connect))
                .setConnectionRequestTimeout(Timeout.of(timeouts.connect))
                .setResponseTimeout(Timeout.of(timeouts.response))
                .setRedirectsEnabled(false)
                .build();
        request.setConfig(config);
        headers.forEach(request::addHeader);

        long deadline = System.nanoTime() + timeouts.readDeadline.toNanos();
        inFlight.add(request);
        try (CloseableHttpResponse response = client.execute(request)) {
            int status = response.getCode();
            HttpEntity entity = response.getEntity();
            boolean ok = status == HttpStatus.OK;
            if (ok && entity != null && entity.getContentLength() > maxBytes) {
                throw new ResponseTooLargeException(maxBytes);
            }
            InputStream body = entity == null ? null : entity.getContent();
            String text = ok
                    ? BoundedReader.read(body, maxBytes, deadline, false)
                    : BoundedReader.read(body, MAX_ERROR_BODY_BYTES, deadline, true);
            return new HttpResult(status, text);
        } finally {
            inFlight.remove(request);
        }
    }

    @Override
    @Deactivate
    public void cancelAll() {
        for (HttpGet request : inFlight) {
            request.cancel();
        }
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
