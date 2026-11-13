package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreClient")
class StoreClientTest {

    private static final String BASE = "https://store.example.org";
    private static final String FEED_URL = BASE + StoreClient.SECURITY_FEED_PATH;
    private static final String LIST_URL = BASE + StoreClient.MODULE_LIST_PATH;
    private static final String SECRET = "s3cr3t-Pa55";
    private static final String FULL_FEED = "{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-1\",\"fixes\":"
            + "[{\"component\":\"m\",\"fixedVersions\":[\"1.0.0\"]}]}]}";

    @Mock
    private HttpFetcher fetcher;

    private StoreClient client;

    @BeforeEach
    void setUp() {
        client = new StoreClient(fetcher);
    }

    private void feedAnswers(HttpResult result) throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenReturn(result);
    }

    private void listAnswers(HttpResult result) throws IOException {
        when(fetcher.get(eq(LIST_URL), anyMap(), anyInt())).thenReturn(result);
    }

    @Test
    @DisplayName("reads the feed and the catalogue of a store")
    void happyPath() throws IOException {
        feedAnswers(new HttpResult(200, FULL_FEED));
        listAnswers(new HttpResult(200, Fixtures.read("module-list.json")));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE + "/", null, null));

        assertThat(result.getStoreUrl()).isEqualTo(BASE);
        assertThat(result.getFeed()).isNotNull();
        assertThat(result.getFeed().getAdvisories()).isNotEmpty();
        assertThat(result.getCatalog()).isNotNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("bounds the size of both documents")
    void boundsSizes() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("empty-v1.json")));
        listAnswers(new HttpResult(200, "[]"));

        client.fetch(StoreTarget.store(BASE, null, null));

        verify(fetcher).get(eq(FEED_URL), anyMap(), eq(StoreClient.MAX_FEED_BYTES));
        verify(fetcher).get(eq(LIST_URL), anyMap(), eq(StoreClient.MAX_CATALOG_BYTES));
    }

    @Test
    @DisplayName("404 means the store does not publish security data, not that nothing needs updating")
    void notPublished() throws IOException {
        feedAnswers(new HttpResult(404, "<html>Not found</html>"));
        listAnswers(new HttpResult(200, "[]"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getCatalog()).isNotNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_PUBLISHED);
            assertThat(e.getStoreUrl()).isEqualTo(BASE);
        });
    }

    @Test
    @DisplayName("503 reports the store's own error, cleaned and bounded")
    void unavailableWithStoreMessage() throws IOException {
        feedAnswers(new HttpResult(503, "{\"error\": \"feed cannot be built\\u0007 " + "x".repeat(1000) + "\"}"));
        listAnswers(new HttpResult(200, "[]"));

        StoreError error = client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0);

        assertThat(error.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
        assertThat(error.getMessage()).contains("503").contains("feed cannot be built").doesNotContain("\u0007");
        assertThat(error.getMessage().length()).isLessThan(400);
    }

    @Test
    @DisplayName("503 without a JSON body still reports the status")
    void unavailableWithoutJson() throws IOException {
        feedAnswers(new HttpResult(503, "<html>down</html>"));
        listAnswers(new HttpResult(200, "[]"));

        StoreError error = client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0);

        assertThat(error.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
        assertThat(error.getMessage()).contains("503").doesNotContain("<html>");
    }

    @Test
    @DisplayName("an unknown schemaVersion is 'feed not understood'")
    void unknownSchema() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("unknown-schema.json")));
        listAnswers(new HttpResult(200, "[]"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NOT_UNDERSTOOD);
    }

    @Test
    @DisplayName("an HTML page served with 200 is 'feed not understood'")
    void htmlWith200() throws IOException {
        feedAnswers(new HttpResult(200, "<html>Maintenance</html>"));
        listAnswers(new HttpResult(200, "[]"));

        assertThat(client.fetch(StoreTarget.store(BASE, null, null)).getErrors())
                .extracting(StoreError::getKind).containsExactly(StoreErrorKind.NOT_UNDERSTOOD);
    }

    @Test
    @DisplayName("401 and 403 point at the store credentials")
    void accessDenied() throws IOException {
        feedAnswers(new HttpResult(401, ""));
        listAnswers(new HttpResult(403, ""));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, "user", SECRET));

        assertThat(result.getErrors()).extracting(StoreError::getKind)
                .containsExactly(StoreErrorKind.UNAVAILABLE, StoreErrorKind.CATALOGUE_UNAVAILABLE);
        assertThat(result.getErrors()).allSatisfy(e -> assertThat(e.getMessage()).contains("credentials").doesNotContain(SECRET));
    }

    @Test
    @DisplayName("a redirect is not followed and is reported")
    void redirect() throws IOException {
        feedAnswers(new HttpResult(302, ""));
        listAnswers(new HttpResult(200, "[]"));

        assertThat(client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0).getMessage()).contains("redirect");
    }

    @Test
    @DisplayName("another status is reported with its code")
    void otherStatus() throws IOException {
        feedAnswers(new HttpResult(500, "boom"));
        listAnswers(new HttpResult(200, "[]"));

        assertThat(client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0).getMessage()).contains("500");
    }

    @Test
    @DisplayName("a transport failure is reported without the exception details or credentials")
    void transportFailure() throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new SocketTimeoutException("Read timed out " + SECRET));
        when(fetcher.get(eq(LIST_URL), anyMap(), anyInt())).thenThrow(new IOException("refused"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, "user", SECRET));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getCatalog()).isNull();
        assertThat(result.getErrors()).extracting(StoreError::getKind)
                .containsExactly(StoreErrorKind.UNAVAILABLE, StoreErrorKind.CATALOGUE_UNAVAILABLE);
        assertThat(result.getErrors().get(0).getMessage()).contains("SocketTimeoutException").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("a response over the limit is refused")
    void tooLarge() throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new ResponseTooLargeException(10));
        listAnswers(new HttpResult(200, "[]"));

        assertThat(client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0).getMessage()).contains("too large");
    }

    @Test
    @DisplayName("a catalogue failure keeps the feed and only makes compatibility unknown")
    void catalogueFailure() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("empty-v1.json")));
        listAnswers(new HttpResult(200, "{\"not\": \"a list\"}"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNotNull();
        assertThat(result.getCatalog()).isNull();
        assertThat(result.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.CATALOGUE_UNAVAILABLE);
    }

    @Test
    @DisplayName("sends Basic credentials to the store only when a user is configured")
    @SuppressWarnings("unchecked")
    void authenticated() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("empty-v1.json")));
        listAnswers(new HttpResult(200, "[]"));

        client.fetch(StoreTarget.store(BASE, "jdoe", SECRET));

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(fetcher).get(eq(FEED_URL), headers.capture(), anyInt());
        String expected = "Basic " + Base64.getEncoder().encodeToString(("jdoe:" + SECRET).getBytes(StandardCharsets.UTF_8));
        assertThat(headers.getValue()).containsEntry("Authorization", expected).containsEntry("Accept", "application/json");
    }

    @Test
    @DisplayName("sends no Authorization header for an anonymous store")
    @SuppressWarnings("unchecked")
    void anonymous() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("empty-v1.json")));
        listAnswers(new HttpResult(200, "[]"));

        client.fetch(StoreTarget.store(BASE, "", "ignored"));

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(fetcher).get(eq(FEED_URL), headers.capture(), anyInt());
        assertThat(headers.getValue()).doesNotContainKey("Authorization");
    }

    @Test
    @DisplayName("an extra feed URL is read as is, without catalogue")
    void feedOnly() throws IOException {
        String url = "https://mirror.example.org/security.json";
        when(fetcher.get(eq(url), anyMap(), anyInt())).thenReturn(new HttpResult(200, Fixtures.read("empty-v1.json")));

        StoreFetchResult result = client.fetch(StoreTarget.feed(url));

        assertThat(result.getFeed()).isNotNull();
        assertThat(result.getCatalog()).isNull();
        assertThat(result.getErrors()).isEmpty();
        verify(fetcher, never()).get(eq(url + StoreClient.MODULE_LIST_PATH), anyMap(), anyInt());
    }

    @Test
    @DisplayName("an invalid URL is reported and never fetched")
    void invalidUrl() throws IOException {
        StoreFetchResult result = client.fetch(StoreTarget.store("file:///etc/passwd", "u", SECRET));

        assertThat(result.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.INVALID_URL);
        assertThat(result.getFeed()).isNull();
        verify(fetcher, never()).get(anyString(), anyMap(), anyInt());
    }

    @Test
    @DisplayName("an invalid URL with credentials is not echoed with its user info")
    void invalidUrlWithUserInfo() {
        StoreFetchResult result = client.fetch(StoreTarget.feed("https://u:" + SECRET + "@x.org/feed.json"));

        assertThat(result.getErrors().get(0).getStoreUrl()).doesNotContain(SECRET);
        assertThat(result.getErrors().get(0).getMessage()).doesNotContain(SECRET);
    }

    @Test
    void targetToStringHidesPassword() {
        assertThat(StoreTarget.store(BASE, "u", SECRET).toString()).doesNotContain(SECRET).contains("authenticated");
    }

    @Test
    @DisplayName("credentials are never sent over plain http, and that is reported")
    @SuppressWarnings("unchecked")
    void noCredentialsOverHttp() throws IOException {
        String base = "http://store.example.org";
        when(fetcher.get(eq(base + StoreClient.SECURITY_FEED_PATH), anyMap(), anyInt()))
                .thenReturn(new HttpResult(200, Fixtures.read("empty-v1.json")));
        when(fetcher.get(eq(base + StoreClient.MODULE_LIST_PATH), anyMap(), anyInt())).thenReturn(new HttpResult(200, "[]"));

        StoreFetchResult result = client.fetch(StoreTarget.store(base, "jdoe", SECRET));

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(fetcher).get(eq(base + StoreClient.SECURITY_FEED_PATH), headers.capture(), anyInt());
        assertThat(headers.getValue()).doesNotContainKey("Authorization");
        assertThat(result.getFeed()).isNotNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.INVALID_URL);
            assertThat(e.getMessage()).contains("https").doesNotContain(SECRET);
        });
    }

    @Test
    @DisplayName("an unexpected runtime failure of one request becomes a store error")
    void runtimeFailure() throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new IllegalArgumentException("bad " + SECRET));
        when(fetcher.get(eq(LIST_URL), anyMap(), anyInt())).thenThrow(new IllegalStateException("x"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getErrors()).extracting(StoreError::getKind)
                .containsExactly(StoreErrorKind.UNAVAILABLE, StoreErrorKind.CATALOGUE_UNAVAILABLE);
        assertThat(result.getErrors()).allSatisfy(e -> assertThat(e.getMessage()).doesNotContain(SECRET));
    }

    @Test
    @DisplayName("feed entries that could not be understood are reported, the rest of the feed is used")
    void skippedEntriesReported() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("security-v1.json")));
        listAnswers(new HttpResult(200, "[]"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNotNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_UNDERSTOOD);
            assertThat(e.getMessage()).contains("5");
        });
    }
}
