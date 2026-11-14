package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.jahia.services.notification.SsrfProtectionDnsResolver;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        assertThat(result.getFeed().getStoreHost()).isEqualTo("store.example.org");
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
        verify(fetcher).get(eq(LIST_URL), anyMap(), eq(4 * 1024 * 1024));
    }

    @Test
    @DisplayName("404 means the store does not publish security data, not that nothing needs updating")
    void notPublished() throws IOException {
        feedAnswers(new HttpResult(404, "<html>Not found</html>"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getCatalog()).isNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_PUBLISHED);
            assertThat(e.getStoreUrl()).isEqualTo(BASE);
        });
        // no security data from this store: its catalogue is not needed
        verify(fetcher, never()).get(eq(LIST_URL), anyMap(), anyInt());
    }

    @Test
    @DisplayName("503 reports the status only, never the store's own text")
    void unavailableDoesNotEchoTheStoreText() throws IOException {
        feedAnswers(new HttpResult(503, "{\"error\": \"feed cannot be built " + "x".repeat(1000) + "\"}"));
        listAnswers(new HttpResult(200, "[]"));

        StoreError error = client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0);

        assertThat(error.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
        assertThat(error.getDetail()).isEqualTo(StoreErrorDetail.HTTP_STATUS);
        assertThat(error.getMessage()).contains("503").doesNotContain("feed cannot be built");
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
        assertThat(result.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.SCHEMA_UNSUPPORTED);
    }

    @Test
    @DisplayName("an HTML page served with 200 is 'feed not understood'")
    void htmlWith200() throws IOException {
        feedAnswers(new HttpResult(200, "<html>Maintenance</html>"));
        listAnswers(new HttpResult(200, "[]"));

        assertThat(client.fetch(StoreTarget.store(BASE, null, null)).getErrors())
                .extracting(StoreError::getKind, StoreError::getDetail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(StoreErrorKind.NOT_UNDERSTOOD, StoreErrorDetail.INVALID_JSON));
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
        assertThat(result.getErrors()).extracting(StoreError::getDetail).containsOnly(StoreErrorDetail.AUTH);
    }

    @Test
    @DisplayName("a redirect is not followed and is reported")
    void redirect() throws IOException {
        feedAnswers(new HttpResult(302, ""));
        listAnswers(new HttpResult(200, "[]"));

        StoreError error = client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0);

        assertThat(error.getMessage()).contains("redirect");
        assertThat(error.getDetail()).isEqualTo(StoreErrorDetail.REDIRECT);
    }

    @Test
    @DisplayName("another status is reported with its code")
    void otherStatus() throws IOException {
        feedAnswers(new HttpResult(500, "boom"));
        listAnswers(new HttpResult(200, "[]"));

        StoreError error = client.fetch(StoreTarget.store(BASE, null, null)).getErrors().get(0);

        assertThat(error.getMessage()).contains("500").doesNotContain("boom");
        assertThat(error.getDetail()).isEqualTo(StoreErrorDetail.HTTP_STATUS);
    }

    @Test
    @DisplayName("a feed timeout is reported without the exception details or credentials; the catalogue is not requested")
    void transportFailure() throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new SocketTimeoutException("Read timed out " + SECRET));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, "user", SECRET));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getCatalog()).isNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.TIMEOUT);
            assertThat(e.getMessage()).contains("timed out").doesNotContain(SECRET);
        });
        verify(fetcher, never()).get(eq(LIST_URL), anyMap(), anyInt());
    }

    @Test
    @DisplayName("a feed connection failure (or a fetcher defect) skips the catalogue too")
    void connectionFailureSkipsCatalogue() throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new IOException("refused"), new IllegalStateException("bug"));

        StoreFetchResult first = client.fetch(StoreTarget.store(BASE, null, null));
        StoreFetchResult second = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(first.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.CONNECTION);
        assertThat(second.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.CONNECTION);
        verify(fetcher, never()).get(eq(LIST_URL), anyMap(), anyInt());
    }

    @Test
    @DisplayName("a catalogue transport failure keeps the feed")
    void catalogueTransportFailure() throws IOException {
        feedAnswers(new HttpResult(200, FULL_FEED));
        when(fetcher.get(eq(LIST_URL), anyMap(), anyInt())).thenThrow(new IOException("refused"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNotNull();
        assertThat(result.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.CATALOGUE_UNAVAILABLE);
        assertThat(result.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.CONNECTION);
    }

    @Test
    @DisplayName("an unexpected parser failure is NOT_UNDERSTOOD / INVALID_JSON, without its message; the catalogue is read")
    void parserDefect() throws IOException {
        StoreClient defective = new StoreClient(fetcher, body -> {
            throw new IllegalArgumentException("remote text " + body);
        });
        feedAnswers(new HttpResult(200, FULL_FEED));
        listAnswers(new HttpResult(200, "[]"));

        StoreFetchResult result = defective.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getCatalog()).isNotNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_UNDERSTOOD);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.INVALID_JSON);
            assertThat(e.getMessage()).isEqualTo("Security feed not understood: unexpected parser failure "
                    + "(IllegalArgumentException)");
        });
    }

    @Test
    @DisplayName("a request refused by Jahia's outbound screening is reported as such")
    void ssrfBlocked() throws IOException {
        SsrfProtectionDnsResolver.SsrfBlockedException blocked = mock(SsrfProtectionDnsResolver.SsrfBlockedException.class);
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(blocked);

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.SSRF_BLOCKED);
            assertThat(e.getMessage()).contains("allowedInternalHosts");
        });
    }

    @Test
    @DisplayName("a screening refusal wrapped in another exception is still recognised")
    void ssrfBlockedAsCause() throws IOException {
        SsrfProtectionDnsResolver.SsrfBlockedException blocked = mock(SsrfProtectionDnsResolver.SsrfBlockedException.class);
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new IOException("wrapped", blocked));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.SSRF_BLOCKED);
    }

    @Test
    @DisplayName("a response over the limit is refused")
    void tooLarge() throws IOException {
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new ResponseTooLargeException(10));
        listAnswers(new HttpResult(200, "[]"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));
        StoreError error = result.getErrors().get(0);

        assertThat(error.getMessage()).contains("too large");
        assertThat(error.getDetail()).isEqualTo(StoreErrorDetail.TOO_LARGE);
        // the store answered: its catalogue is still read
        assertThat(result.getCatalog()).isNotNull();
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
        assertThat(result.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.INVALID_JSON);
    }

    @Test
    @DisplayName("a truncated catalogue is used and reported as not entirely read")
    void truncatedCatalogue() throws IOException {
        StringBuilder json = new StringBuilder("[{\"modules\":[");
        for (int i = 0; i <= ModuleCatalogParser.MAX_ENTRIES; i++) {
            json.append(i == 0 ? "" : ",").append("{\"groupId\":\"g\",\"name\":\"m").append(i).append("\",\"versions\":[]}");
        }
        feedAnswers(new HttpResult(200, Fixtures.read("empty-v1.json")));
        listAnswers(new HttpResult(200, json.append("]}]").toString()));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getCatalog()).isNotNull();
        assertThat(result.getErrors()).extracting(StoreError::getKind, StoreError::getDetail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(StoreErrorKind.CATALOGUE_UNAVAILABLE,
                        StoreErrorDetail.ENTRIES_SKIPPED));
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
        assertThat(result.getErrors().get(0).getDetail()).isNull();
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
    @DisplayName("a plain http store is refused (NOT_HTTPS) and never fetched, with or without credentials")
    void httpStoreIsRefused() throws IOException {
        StoreFetchResult result = client.fetch(StoreTarget.store("http://store.example.org", "jdoe", SECRET));

        assertThat(result.getFeed()).isNull();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.INVALID_URL);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.NOT_HTTPS);
            assertThat(e.getStoreUrl()).isEqualTo("http://store.example.org");
            assertThat(e.getMessage()).contains("https").doesNotContain(SECRET);
        });
        verify(fetcher, never()).get(anyString(), anyMap(), anyInt());
    }

    @Test
    @DisplayName("a plain http extra feed is refused (NOT_HTTPS) and never fetched")
    void httpFeedIsRefused() throws IOException {
        StoreFetchResult result = client.fetch(StoreTarget.feed("http://mirror.example.org/security.json"));

        assertThat(result.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.NOT_HTTPS);
        verify(fetcher, never()).get(anyString(), anyMap(), anyInt());
    }

    @Test
    @DisplayName("an unexpected runtime failure of one request becomes a store error")
    void runtimeFailure() throws IOException {
        feedAnswers(new HttpResult(200, FULL_FEED));
        when(fetcher.get(eq(LIST_URL), anyMap(), anyInt())).thenThrow(new IllegalStateException("bad " + SECRET));
        StoreFetchResult catalogueFailed = client.fetch(StoreTarget.store(BASE, null, null));
        when(fetcher.get(eq(FEED_URL), anyMap(), anyInt())).thenThrow(new IllegalArgumentException("bad " + SECRET));
        StoreFetchResult feedFailed = client.fetch(StoreTarget.store(BASE, null, null));

        List<StoreError> errors = new ArrayList<>(catalogueFailed.getErrors());
        errors.addAll(feedFailed.getErrors());
        assertThat(errors).extracting(StoreError::getKind)
                .containsExactly(StoreErrorKind.CATALOGUE_UNAVAILABLE, StoreErrorKind.UNAVAILABLE);
        assertThat(errors).allSatisfy(e -> assertThat(e.getMessage()).doesNotContain(SECRET));
        assertThat(errors).extracting(StoreError::getDetail).containsOnly(StoreErrorDetail.CONNECTION);
    }

    @Test
    @DisplayName("a null password is sent as an empty one")
    @SuppressWarnings("unchecked")
    void nullPassword() throws IOException {
        feedAnswers(new HttpResult(200, Fixtures.read("empty-v1.json")));
        listAnswers(new HttpResult(200, "[]"));

        client.fetch(StoreTarget.store(BASE, "jdoe", null));

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(fetcher).get(eq(FEED_URL), headers.capture(), anyInt());
        String expected = "Basic " + Base64.getEncoder().encodeToString("jdoe:".getBytes(StandardCharsets.UTF_8));
        assertThat(headers.getValue()).containsEntry("Authorization", expected);
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
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.ENTRIES_SKIPPED);
            assertThat(e.getMessage()).startsWith("7 entries");
        });
    }

    @Test
    @DisplayName("a feed cut at the entry limit is reported as not fully understood, the entries read are used")
    void truncatedFeedReported() throws IOException {
        feedAnswers(new HttpResult(200, SecurityFeedParserTest.manyEntries("flaggedVersions",
                "{\"name\":\"m\",\"version\":\"1.0\"}", SecurityFeedParser.MAX_ENTRIES + 1)));
        listAnswers(new HttpResult(200, "[]"));

        StoreFetchResult result = client.fetch(StoreTarget.store(BASE, null, null));

        assertThat(result.getFeed().getFlaggedVersions()).hasSize(SecurityFeedParser.MAX_ENTRIES);
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_UNDERSTOOD);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.ENTRIES_SKIPPED);
            assertThat(e.getMessage()).isEqualTo("Security feed: some lists are longer than this module reads (at most "
                    + "10000 advisories and 10000 flagged versions, 100 fixes per advisory, fixed versions per fix, CVEs per "
                    + "advisory and references per flagged version): the rest was ignored");
        });
    }
}
