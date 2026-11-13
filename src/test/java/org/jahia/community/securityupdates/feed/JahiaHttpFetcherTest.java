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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
    void connectionRefused() throws IOException {
        server.stop(0);
        assertThatThrownBy(() -> fetcher.get(base + "/ok", Map.of(), 1000)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("fails cleanly when Jahia's HTTP client service is not available")
    void noService() {
        JahiaHttpFetcher unbound = new JahiaHttpFetcher();
        assertThatThrownBy(() -> unbound.get(base + "/ok", Map.of(), 1000)).isInstanceOf(IOException.class);
    }
}
