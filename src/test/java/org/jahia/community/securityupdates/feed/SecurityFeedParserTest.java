package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.Advisory;
import org.jahia.community.securityupdates.model.AdvisoryFix;
import org.jahia.community.securityupdates.model.FlaggedVersion;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecurityFeedParser")
class SecurityFeedParserTest {

    @Test
    @DisplayName("parses a schema 1 feed with every optional key")
    void parsesFullAdvisory() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(Fixtures.read("security-v1.json"));

        Advisory advisory = feed.getAdvisories().get(0);
        assertThat(advisory.getId()).isEqualTo("JSA-2026-0011");
        assertThat(advisory.getTitle()).isEqualTo("Injection in GraphQL provider");
        assertThat(advisory.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(advisory.getCvss()).isEqualTo(7.5);
        assertThat(advisory.getCves()).containsExactly("CVE-2026-1234");
        assertThat(advisory.getUrl()).isEqualTo("https://academy.jahia.com/jsa-2026-0011");
        assertThat(advisory.getPublished()).isEqualTo("2026-11-03");
        assertThat(advisory.getFixes()).extracting(AdvisoryFix::getComponent).containsExactly("graphql-dxm-provider", "jahia-core");
        assertThat(advisory.getFixes().get(0).getGroupId()).isEqualTo("org.jahia.modules");
        assertThat(advisory.getFixes().get(0).getFixedVersions()).containsExactly("3.10.0", "2.24.0");
        assertThat(advisory.getFixes().get(1).getGroupId()).isNull();
    }

    @Test
    @DisplayName("absent optional keys are null or empty; an unknown severity is UNKNOWN")
    void optionalKeys() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(Fixtures.read("security-v1.json"));

        Advisory minimal = feed.getAdvisories().get(1);
        assertThat(minimal.getCvss()).isNull();
        assertThat(minimal.getUrl()).isNull();
        assertThat(minimal.getPublished()).isNull();
        assertThat(minimal.getCves()).isEmpty();
        assertThat(minimal.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(feed.getAdvisories().get(2).getSeverity()).isEqualTo(Severity.UNKNOWN);
    }

    @Test
    @DisplayName("incomplete entries are skipped and counted, valid fixes of an entry are kept")
    void skipsIncompleteEntries() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(Fixtures.read("security-v1.json"));

        assertThat(feed.getAdvisories()).extracting(Advisory::getId)
                .containsExactly("JSA-2026-0011", "JSA-2026-0012", "JSA-2026-0013", "JSA-BADFIX");
        Advisory badFix = feed.getAdvisories().get(3);
        assertThat(badFix.getFixes()).singleElement().satisfies(fix -> {
            assertThat(fix.getComponent()).isEqualTo("ok-module");
            assertThat(fix.getFixedVersions()).containsExactly("2.0");
        });
        // no id, no fixes, not an object, fix without component, flagged without version
        assertThat(feed.getSkippedEntries()).isEqualTo(5);
    }

    @Test
    @DisplayName("parses owner-flagged versions")
    void parsesFlaggedVersions() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(Fixtures.read("security-v1.json"));

        assertThat(feed.getFlaggedVersions()).extracting(FlaggedVersion::getName).containsExactly("my-module", "minimal-module");
        FlaggedVersion flag = feed.getFlaggedVersions().get(0);
        assertThat(flag.getGroupId()).isEqualTo("org.jahia.community");
        assertThat(flag.getVersion()).isEqualTo("1.4.2");
        assertThat(flag.getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(flag.getReferences()).containsExactly("GHSA-aaaa-bbbb-cccc");
        assertThat(flag.getNote()).isEqualTo("Fixes an XSS");
        FlaggedVersion minimal = feed.getFlaggedVersions().get(1);
        assertThat(minimal.getGroupId()).isNull();
        assertThat(minimal.getSeverity()).isEqualTo(Severity.UNKNOWN);
        assertThat(minimal.getReferences()).isEmpty();
        assertThat(minimal.getNote()).isNull();
    }

    @Test
    @DisplayName("a schema 1 feed without lists is an empty feed")
    void emptyFeed() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(Fixtures.read("empty-v1.json"));
        assertThat(feed.getAdvisories()).isEmpty();
        assertThat(feed.getFlaggedVersions()).isEmpty();
    }

    @ParameterizedTest(name = "{0} fails closed")
    @ValueSource(strings = {"unknown-schema.json", "missing-schema.json", "string-schema.json"})
    @DisplayName("an unknown or missing schemaVersion fails closed")
    void unknownSchemaFailsClosed(String fixture) {
        assertThatThrownBy(() -> SecurityFeedParser.parse(Fixtures.read(fixture)))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("schemaVersion");
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"not-an-object.json", "advisories-not-array.json"})
    void wrongStructureIsRefused(String fixture) {
        assertThatThrownBy(() -> SecurityFeedParser.parse(Fixtures.read(fixture))).isInstanceOf(FeedFormatException.class);
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @NullAndEmptySource
    @ValueSource(strings = {"<html>Maintenance</html>", "{\"schemaVersion\": 1, ", "   "})
    void invalidJsonIsRefused(String body) {
        assertThatThrownBy(() -> SecurityFeedParser.parse(body)).isInstanceOf(FeedFormatException.class);
    }

    @Test
    @DisplayName("feed text is cleaned of control characters and truncated; a non-http url is dropped")
    void untrustedTextIsBounded() throws Exception {
        String longTitle = "x".repeat(5000);
        String json = "{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA\\u0000-1\",\"title\":\"" + longTitle
                + "\",\"severity\":\"low\",\"url\":\"javascript:alert(1)\",\"fixes\":[{\"component\":\"m\",\"fixedVersions\":[\"1.0\"]}]}]}";

        Advisory advisory = SecurityFeedParser.parse(json).getAdvisories().get(0);

        assertThat(advisory.getId()).isEqualTo("JSA-1");
        assertThat(advisory.getTitle()).hasSizeLessThanOrEqualTo(SecurityFeedParser.MAX_TEXT);
        assertThat(advisory.getUrl()).isNull();
    }

    @Test
    @DisplayName("a fix whose fixed versions are all malformed is dropped and counted, not read as 'not affected'")
    void malformedFixedVersionsAreSkipped() throws Exception {
        String json = "{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-1\",\"severity\":\"high\",\"fixes\":["
                + "{\"component\":\"a\",\"fixedVersions\":[\"latest\",\"v2\"]},"
                + "{\"component\":\"b\",\"fixedVersions\":[\"bogus\",\"2.0.0\"]}]}]}";

        SecurityFeed feed = SecurityFeedParser.parse(json);

        assertThat(feed.getAdvisories()).singleElement().satisfies(a -> assertThat(a.getFixes())
                .singleElement().satisfies(f -> assertThat(f.getFixedVersions()).containsExactly("2.0.0")));
        // the fix of "a" is dropped; the malformed version of "b" is ignored but its fix entry is kept
        assertThat(feed.getSkippedEntries()).isEqualTo(1);
    }

    @Test
    @DisplayName("deeply nested JSON is refused, not a StackOverflowError")
    void deepNestingIsRefused() {
        String json = "{\"schemaVersion\":1,\"advisories\":" + "[".repeat(200_000);
        assertThatThrownBy(() -> SecurityFeedParser.parse(json)).isInstanceOf(FeedFormatException.class);
        assertThatThrownBy(() -> ModuleCatalogParser.parse("[".repeat(200_000))).isInstanceOf(FeedFormatException.class);
    }
}
