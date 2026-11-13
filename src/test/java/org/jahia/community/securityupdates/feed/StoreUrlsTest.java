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
            "HTTP://store.example.org:8080/store, HTTP://store.example.org:8080/store",
            "https://store.example.org/contents/x.json, https://store.example.org/contents/x.json"
    })
    void acceptsAbsoluteHttpUrls(String url, String expected) {
        assertThat(StoreUrls.normalize(url)).contains(expected);
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @NullAndEmptySource
    @ValueSource(strings = {
            "store.jahia.com", "/contents/x", "ftp://store.jahia.com", "file:///etc/passwd", "javascript:alert(1)",
            "https://", "https://user:secret@store.jahia.com", "https://store.jahia.com/a b", "jar:https://x!/y",
            "https://store.jahia.com/?q=1", "https://store.jahia.com/#frag"
    })
    void refusesEverythingElse(String url) {
        assertThat(StoreUrls.normalize(url)).isEmpty();
    }

    @Test
    void refusesOverlongUrl() {
        assertThat(StoreUrls.normalize("https://store.jahia.com/" + "a".repeat(3000))).isEmpty();
    }
}
