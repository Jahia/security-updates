package org.jahia.community.securityupdates.feed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StoreUrls")
class StoreUrlsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "https://store.jahia.com, https://store.jahia.com",
            "https://store.jahia.com/, https://store.jahia.com",
            "' https://store.jahia.com// ', https://store.jahia.com",
            "HTTPS://store.example.org:8443/store, HTTPS://store.example.org:8443/store",
            "https://store.example.org/contents/x.json, https://store.example.org/contents/x.json",
            "https://[2001:db8::1]/store, https://[2001:db8::1]/store",
            "https://store.jahia.com/en/sites/private-app-store, https://store.jahia.com/en/sites/private-app-store"
    })
    void acceptsAbsoluteHttpsUrls(String url, String expected) {
        assertThat(StoreUrls.normalize(url)).contains(expected);
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @NullAndEmptySource
    @ValueSource(strings = {
            "store.jahia.com", "/contents/x", "ftp://store.jahia.com", "file:///etc/passwd", "javascript:alert(1)",
            "https://", "https:///contents/x", "https://user:secret@store.jahia.com", "https://@store.jahia.com",
            "https://store.jahia.com/a b", "jar:https://x!/y", "https://store.jahia.com/?q=1", "https://store.jahia.com/#frag",
            "https://störe.example.org", "https://store.example.org/café", "https://store.example.org/%",
            "http://store.jahia.com", "HTTP://store.example.org:8080/store"
    })
    void refusesEverythingElse(String url) {
        assertThat(StoreUrls.normalize(url)).isEmpty();
    }

    @Test
    void refusesOverlongUrl() {
        assertThat(StoreUrls.normalize("https://store.jahia.com/" + "a".repeat(3000))).isEmpty();
    }

    @ParameterizedTest(name = "[{0}] is a plain http URL")
    @ValueSource(strings = {"http://store.jahia.com", "HTTP://store.example.org:8080/store/", " http://x.org/feed.json "})
    void recognisesPlainHttp(String url) {
        assertThat(StoreUrls.isPlainHttp(url)).isTrue();
    }

    @ParameterizedTest(name = "[{0}] is not a usable plain http URL")
    @NullAndEmptySource
    @ValueSource(strings = {"https://store.jahia.com", "http://user:pw@x.org", "http://", "ftp://x.org", "http://x.org/?q"})
    void otherUrlsAreNotPlainHttp(String url) {
        assertThat(StoreUrls.isPlainHttp(url)).isFalse();
    }

    @ParameterizedTest(name = "{0} is shown as {1}")
    @CsvSource({
            "https://store.jahia.com/, https://store.jahia.com",
            "https://u:secret@store.jahia.com/x, https://store.jahia.com",
            "http://store.jahia.com/x, http://store.jahia.com",
            "not a url, (invalid URL)",
            "/relative, (invalid URL)"
    })
    void displayNeverShowsUserInfo(String url, String shown) {
        assertThat(StoreUrls.display(url)).isEqualTo(shown);
    }

    @Test
    void displayOfNull() {
        assertThat(StoreUrls.display(null)).isEqualTo("(invalid URL)");
    }

    @ParameterizedTest(name = "{0} official: {1}")
    @CsvSource({
            "https://store.jahia.com, true",
            "https://STORE.jahia.com/en/sites/private-app-store, true",
            "https://store.example.org, false",
            "https://evilstore.jahia.com.example.org, false",
            "not a url, false"
    })
    void recognisesTheJahiaStore(String url, boolean official) {
        assertThat(StoreUrls.isJahiaStore(url)).isEqualTo(official);
    }
}
