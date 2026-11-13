package org.jahia.community.securityupdates.feed;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jahia.services.notification.HttpClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real HTTP client wiring against a loopback JDK {@link HttpServer}; Jahia's
 * {@link HttpClientService} is mocked to hand out a plain client, as it would after proxy selection.
 */
@DisplayName("JahiaHttpFetcher")
class JahiaHttpFetcherTest {

    private HttpServer server;
    private String base;
    private CloseableHttpClient client;
    private HttpClientService httpClientService;
    private JahiaHttpFetcher fetcher;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final CountDownLatch slowRequestReceived = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/ok", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"schemaVersion\":1}");
        });
        server.createContext("/big", exchange -> respond(exchange, 200, "x".repeat(5000)));
        server.createContext("/chunked", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("y".repeat(5000).getBytes(StandardCharsets.UTF_8));
            }
        });
        server.createContext("/unavailable", exchange -> respond(exchange, 503, "{\"error\":\"" + "e".repeat(10_000) + "\"}"));
        server.createContext("/slow", exchange -> {
            slowRequestReceived.countDown();
            try {
                // Never released: the handler holds the response for at most 5 s, like a slow store.
                if (!new CountDownLatch(1).await(5, TimeUnit.SECONDS)) {
                    respond(exchange, 200, "late");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "late");
        });
        // A hostile store: a body declared huge, sent one byte at a time, each byte within the response timeout
        server.createContext("/trickle-404", exchange -> trickle(exchange, 404, HUGE, MAX_ERROR_PREFIX));
        server.createContext("/trickle-404-from-start", exchange -> trickle(exchange, 404, HUGE, 0));
        server.createContext("/declared-huge", exchange -> trickle(exchange, 200, HUGE, 0));
        server.createContext("/trickle-200", exchange -> trickle(exchange, 200, 0, 0));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/ok");
            respond(exchange, 302, "");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        client = HttpClients.createDefault();
        httpClientService = mock(HttpClientService.class);
        when(httpClientService.getHttpClient(anyString())).thenReturn(client);
        when(httpClientService.getRequestConfigBuilder(any())).thenReturn(RequestConfig.custom());
        fetcher = new JahiaHttpFetcher();
        fetcher.setHttpClientService(httpClientService);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.stop(0);
        client.close();
    }

    /** 10 GB: a declared body length no one reads. */
    private static final long HUGE = 10_000_000_000L;
    /** More than the bytes kept of an error body: the client then stops reading. */
    private static final int MAX_ERROR_PREFIX = 2 * JahiaHttpFetcher.MAX_ERROR_BODY_BYTES;
    private static final long TRICKLE_MILLIS = 50;
    private static final long TRICKLE_FOR_NANOS = TimeUnit.SECONDS.toNanos(15);
    /** Short bounds, so that a test of a hostile store ends in about a second. */
    private static final JahiaHttpFetcher.Timeouts SHORT = new JahiaHttpFetcher.Timeouts(Duration.ofSeconds(2),
            Duration.ofSeconds(1), Duration.ofSeconds(1));
    private static final Duration WITHIN = Duration.ofSeconds(4);

    /**
     * Sends {@code prefix} bytes at once, then one byte every {@value #TRICKLE_MILLIS} ms (so no read ever waits for
     * the response timeout) until the client goes away or {@link #TRICKLE_FOR_NANOS} passes.
     *
     * @param length the declared body length, 0 for chunked
     */
    private static void trickle(HttpExchange exchange, int status, long length, int prefix) throws IOException {
        exchange.sendResponseHeaders(status, length);
        long end = System.nanoTime() + TRICKLE_FOR_NANOS;
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(new byte[prefix]);
            os.flush();
            while (System.nanoTime() < end) {
                os.write('x');
                os.flush();
                // Paces the trickle; not a wait for a condition, so no latch fits here.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(TRICKLE_MILLIS));
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (IOException e) {
            // the client aborted the request: what is expected
        }
        exchange.close();
    }

    private HttpResult timed(JahiaHttpFetcher target, String path, int maxBytes, Duration[] took) throws IOException {
        long start = System.nanoTime();
        try {
            return target.get(base + path, Map.of(), maxBytes);
        } finally {
            took[0] = Duration.ofNanos(System.nanoTime() - start);
        }
    }

    private JahiaHttpFetcher shortFetcher() {
        JahiaHttpFetcher quick = new JahiaHttpFetcher(SHORT);
        quick.setHttpClientService(httpClientService);
        return quick;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    @Test
    @DisplayName("returns status and body, sending the given headers, through Jahia's client for that URL")
    void ok() throws IOException {
        HttpResult result = fetcher.get(base + "/ok", Map.of("Authorization", "Basic abc"), 1000);

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo("{\"schemaVersion\":1}");
        assertThat(lastAuthorization.get()).isEqualTo("Basic abc");
        verify(httpClientService).getHttpClient(base + "/ok");
    }

    @Test
    @DisplayName("always takes the screened client of the URL (SSRF screening at connect time), never an unscreened one")
    void usesTheScreenedClientOnly() throws IOException {
        fetcher.get(base + "/ok", Map.of(), 1000);
        fetcher.get(base + "/unavailable", Map.of(), 1000);

        verify(httpClientService).getHttpClient(base + "/ok");
        verify(httpClientService).getHttpClient(base + "/unavailable");
        verify(httpClientService, org.mockito.Mockito.never()).getHttpClientForConfiguredTarget(anyString());
    }

    @Test
    @DisplayName("refuses a body declared larger than the limit")
    void declaredTooLarge() {
        assertThatThrownBy(() -> fetcher.get(base + "/big", Map.of(), 1000)).isInstanceOf(ResponseTooLargeException.class);
    }

    @Test
    @DisplayName("refuses a streamed body larger than the limit")
    void streamedTooLarge() {
        assertThatThrownBy(() -> fetcher.get(base + "/chunked", Map.of(), 1000)).isInstanceOf(ResponseTooLargeException.class);
    }

    @Test
    @DisplayName("truncates the body of an error status")
    void errorBodyTruncated() throws IOException {
        HttpResult result = fetcher.get(base + "/unavailable", Map.of(), 1000);

        assertThat(result.getStatus()).isEqualTo(503);
        assertThat(result.getBody()).hasSize(JahiaHttpFetcher.MAX_ERROR_BODY_BYTES);
    }

    @Test
    @DisplayName("does not follow redirects, so credentials never reach another location")
    void noRedirect() throws IOException {
        HttpResult result = fetcher.get(base + "/redirect", Map.of("Authorization", "Basic abc"), 1000);

        assertThat(result.getStatus()).isEqualTo(302);
        assertThat(lastAuthorization.get()).isNull();
    }

    @Test
    @DisplayName("a closed port is an IOException")
    void connectionRefused() {
        server.stop(0);
        assertThatThrownBy(() -> fetcher.get(base + "/ok", Map.of(), 1000)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("fails cleanly when Jahia's HTTP client service is not available")
    void noService() {
        JahiaHttpFetcher unbound = new JahiaHttpFetcher();
        assertThatThrownBy(() -> unbound.get(base + "/ok", Map.of(), 1000)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("a response that does not come within the response timeout fails as a timeout")
    void responseTimeoutIsEnforced() {
        JahiaHttpFetcher quick = new JahiaHttpFetcher(new JahiaHttpFetcher.Timeouts(Duration.ofSeconds(2),
                Duration.ofMillis(300), Duration.ofSeconds(10)));
        quick.setHttpClientService(httpClientService);
        long start = System.nanoTime();

        assertThatThrownBy(() -> quick.get(base + "/slow", Map.of(), 1000)).isInstanceOf(java.io.InterruptedIOException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("cancelAll aborts a request in flight, so that deactivation does not wait for the network")
    void cancelAllAbortsInFlightRequests() throws Exception {
        CompletableFuture<HttpResult> call = CompletableFuture.supplyAsync(() -> {
            try {
                return fetcher.get(base + "/slow", Map.of(), 1000);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertThat(slowRequestReceived.await(5, TimeUnit.SECONDS)).isTrue();
        long start = System.nanoTime();

        fetcher.cancelAll();

        assertThatThrownBy(() -> call.get(4, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(java.io.UncheckedIOException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("an error status with a huge trickled body returns its status and first bytes at once: the rest is never read")
    void errorBodyIsNotDrained() throws IOException {
        Duration[] took = new Duration[1];

        HttpResult result = timed(shortFetcher(), "/trickle-404", 1000, took);

        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getBody()).hasSize(JahiaHttpFetcher.MAX_ERROR_BODY_BYTES);
        assertThat(took[0]).isLessThan(WITHIN);
    }

    @Test
    @DisplayName("an error status whose body trickles from the first byte returns its status at the read deadline")
    void trickledErrorBodyStopsAtTheDeadline() throws IOException {
        Duration[] took = new Duration[1];

        HttpResult result = timed(shortFetcher(), "/trickle-404-from-start", 1000, took);

        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getBody().length()).isLessThan(JahiaHttpFetcher.MAX_ERROR_BODY_BYTES);
        assertThat(took[0]).isLessThan(WITHIN);
    }

    @Test
    @DisplayName("a 200 that declares a body larger than the limit fails at once, without reading the body")
    void declaredHugeFailsFast() {
        Duration[] took = new Duration[1];

        assertThatThrownBy(() -> timed(shortFetcher(), "/declared-huge", 1000, took)).isInstanceOf(ResponseTooLargeException.class);
        assertThat(took[0]).isLessThan(WITHIN);
    }

    @Test
    @DisplayName("a 200 body that trickles, each byte within the response timeout, fails as a timeout at the read deadline")
    void trickledBodyStopsAtTheDeadline() {
        Duration[] took = new Duration[1];

        assertThatThrownBy(() -> timed(shortFetcher(), "/trickle-200", 1000, took))
                .isInstanceOf(java.net.SocketTimeoutException.class).hasMessage("Response read deadline exceeded");
        assertThat(took[0]).isLessThan(WITHIN);
    }

    @Test
    @DisplayName("after a hostile response, the same fetcher still reads a normal 200")
    void normalResponseAfterAHostileOne() throws IOException {
        JahiaHttpFetcher quick = shortFetcher();
        timed(quick, "/trickle-404", 1000, new Duration[1]);

        HttpResult result = quick.get(base + "/ok", Map.of(), 1000);

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo("{\"schemaVersion\":1}");
    }

    @Test
    @DisplayName("once deactivated, the fetcher refuses new requests")
    void deactivated() {
        JahiaHttpFetcher quick = shortFetcher();
        quick.deactivate();

        assertThatThrownBy(() -> quick.get(base + "/ok", Map.of(), 1000)).isInstanceOf(IOException.class)
                .hasMessage("The HTTP fetcher is stopped");
    }

    @Test
    @DisplayName("the default timeouts bound connection, response and the whole read")
    void defaultTimeouts() {
        assertThat(JahiaHttpFetcher.Timeouts.DEFAULT.getConnect()).isEqualTo(Duration.ofSeconds(10));
        assertThat(JahiaHttpFetcher.Timeouts.DEFAULT.getResponse()).isEqualTo(Duration.ofSeconds(30));
        assertThat(JahiaHttpFetcher.Timeouts.DEFAULT.getReadDeadline()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void resultWithoutBodyHasAnEmptyBody() {
        assertThat(new HttpResult(204, null).getBody()).isEmpty();
    }
}
