package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.Advisory;
import org.jahia.community.securityupdates.model.AdvisoryFix;
import org.jahia.community.securityupdates.model.FlaggedVersion;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        // no id, no fixes, not an object, fix without component, flagged without version, and the two fixed
        // versions of ok-module that are not version strings (3 and null)
        assertThat(feed.getSkippedEntries()).isEqualTo(7);
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
                .hasMessageContaining("schemaVersion")
                .satisfies(e -> assertThat(((FeedFormatException) e).getDetail()).isEqualTo(StoreErrorDetail.SCHEMA_UNSUPPORTED));
    }

    @Test
    @DisplayName("the refusal of a schema never echoes the remote value")
    void schemaRefusalDoesNotEchoTheRemoteValue() {
        assertThatThrownBy(() -> SecurityFeedParser.parse("{\"schemaVersion\":\"<script>evil-9137</script>\"}"))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageNotContaining("evil-9137");
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"not-an-object.json", "advisories-not-array.json"})
    void wrongStructureIsRefused(String fixture) {
        assertThatThrownBy(() -> SecurityFeedParser.parse(Fixtures.read(fixture)))
                .isInstanceOf(FeedFormatException.class)
                .satisfies(e -> assertThat(((FeedFormatException) e).getDetail()).isEqualTo(StoreErrorDetail.INVALID_JSON));
    }

    @ParameterizedTest(name = "[{0}] is an entry of the wrong type, skipped and counted")
    @ValueSource(strings = {
            "{\"id\":7,\"fixes\":[{\"component\":\"m\",\"fixedVersions\":[\"1.0\"]}]}",
            "{\"id\":\"JSA-1\",\"fixes\":\"none\"}",
            "{\"id\":\"JSA-1\",\"fixes\":[]}",
            "{\"id\":\"JSA-1\",\"fixes\":[{\"component\":3,\"fixedVersions\":[\"1.0\"]}]}",
            "{\"id\":\"JSA-1\",\"fixes\":[{\"component\":\"m\",\"fixedVersions\":\"1.0\"}]}",
            "{\"id\":\"JSA-1\",\"fixes\":[{\"component\":\"m\",\"fixedVersions\":[1.0]}]}",
            "[]",
            "\"JSA-1\""
    })
    void entriesOfTheWrongTypeAreSkipped(String advisory) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"advisories\":[" + advisory + "]}");

        assertThat(feed.getAdvisories()).isEmpty();
        assertThat(feed.getSkippedEntries()).isPositive();
    }

    @ParameterizedTest(name = "[{0}] is a flag of the wrong type, skipped and counted")
    @ValueSource(strings = {"{\"name\":\"m\"}", "{\"version\":\"1.0\"}", "{\"name\":1,\"version\":\"1.0\"}", "3"})
    void flagsOfTheWrongTypeAreSkipped(String flag) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"flaggedVersions\":[" + flag + "]}");

        assertThat(feed.getFlaggedVersions()).isEmpty();
        assertThat(feed.getSkippedEntries()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @NullAndEmptySource
    @ValueSource(strings = {"<html>Maintenance</html>", "{\"schemaVersion\": 1, ", "   "})
    void invalidJsonIsRefused(String body) {
        assertThatThrownBy(() -> SecurityFeedParser.parse(body))
                .isInstanceOf(FeedFormatException.class)
                .satisfies(e -> assertThat(((FeedFormatException) e).getDetail()).isEqualTo(StoreErrorDetail.INVALID_JSON));
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
    @DisplayName("a fix whose fixed versions are all malformed is dropped and counted; a malformed version of a kept fix is counted too")
    void malformedFixedVersionsAreSkipped() throws Exception {
        String json = "{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-1\",\"severity\":\"high\",\"fixes\":["
                + "{\"component\":\"a\",\"fixedVersions\":[\"latest\",\"v2\"]},"
                + "{\"component\":\"b\",\"fixedVersions\":[\"bogus\",\"2.0.0\"]}]}]}";

        SecurityFeed feed = SecurityFeedParser.parse(json);

        assertThat(feed.getAdvisories()).singleElement().satisfies(a -> assertThat(a.getFixes())
                .singleElement().satisfies(f -> assertThat(f.getFixedVersions()).containsExactly("2.0.0")));
        // the fix of "a" is dropped (counted once); the malformed version of "b" is dropped and counted, its fix is kept
        assertThat(feed.getSkippedEntries()).isEqualTo(2);
    }

    @Test
    @DisplayName("one malformed fixed version next to a well-formed one is counted: it may be the installed version's line")
    void malformedFixedVersionNextToAGoodOneIsCounted() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"2.24.0\",\"3..5\"]}"));

        assertThat(feed.getAdvisories().get(0).getFixes().get(0).getFixedVersions()).containsExactly("2.24.0");
        assertThat(feed.getSkippedEntries()).isEqualTo(1);
    }

    /** A JSON array of {@code count} times {@code item}. */
    private static String array(String item, int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "" : ",").append(item);
        }
        return sb.append(']').toString();
    }

    @ParameterizedTest(name = "{0} fixes in one advisory: truncated {1}")
    @CsvSource({"100, false", "101, true"})
    void fixesPerAdvisoryCap(int count, boolean truncated) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-1\",\"fixes\":"
                + array("{\"component\":\"m\",\"fixedVersions\":[\"1.0\"]}", count) + "}]}");

        assertThat(feed.getAdvisories().get(0).getFixes()).hasSize(SecurityFeedParser.MAX_ITEMS);
        assertThat(feed.isTruncated()).isEqualTo(truncated);
        assertThat(feed.getSkippedEntries()).isZero();
    }

    @ParameterizedTest(name = "{0} fixed versions in one fix: truncated {1}")
    @CsvSource({"100, false", "101, true"})
    void fixedVersionsPerFixCap(int count, boolean truncated) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":" + array("\"1.0\"", count) + "}"));

        assertThat(feed.getAdvisories().get(0).getFixes().get(0).getFixedVersions()).hasSize(SecurityFeedParser.MAX_ITEMS);
        assertThat(feed.isTruncated()).isEqualTo(truncated);
    }

    @ParameterizedTest(name = "{0} CVEs in one advisory: truncated {1}")
    @CsvSource({"100, false", "101, true"})
    void cvesCap(int count, boolean truncated) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-1\",\"cves\":"
                + array("\"CVE-2026-1234\"", count) + ",\"fixes\":[{\"component\":\"m\",\"fixedVersions\":[\"1.0\"]}]}]}");

        assertThat(feed.getAdvisories().get(0).getCves()).hasSize(SecurityFeedParser.MAX_ITEMS);
        assertThat(feed.isTruncated()).isEqualTo(truncated);
    }

    @ParameterizedTest(name = "{0} references in one flagged version: truncated {1}")
    @CsvSource({"100, false", "101, true"})
    void referencesCap(int count, boolean truncated) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"flaggedVersions\":[{\"name\":\"m\","
                + "\"version\":\"1.0\",\"references\":" + array("\"CVE-2026-1234\"", count) + "}]}");

        assertThat(feed.getFlaggedVersions().get(0).getReferences()).hasSize(SecurityFeedParser.MAX_ITEMS);
        assertThat(feed.isTruncated()).isEqualTo(truncated);
    }

    @Test
    @DisplayName("items of another type or empty do not count towards the cap and do not truncate")
    void ignoredItemsDoNotTruncate() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"flaggedVersions\":[{\"name\":\"m\","
                + "\"version\":\"1.0\",\"references\":[1,\"\",\"CVE-2026-1234\"]}]}");

        assertThat(feed.getFlaggedVersions().get(0).getReferences()).containsExactly("CVE-2026-1234");
        assertThat(feed.isTruncated()).isFalse();
    }

    @Test
    @DisplayName("deeply nested JSON is refused, not a StackOverflowError")
    void deepNestingIsRefused() {
        String json = "{\"schemaVersion\":1,\"advisories\":" + "[".repeat(200_000);
        assertThatThrownBy(() -> SecurityFeedParser.parse(json))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("nested")
                .satisfies(e -> assertThat(((FeedFormatException) e).getDetail()).isEqualTo(StoreErrorDetail.INVALID_JSON));
        assertThatThrownBy(() -> ModuleCatalogParser.parse("[".repeat(200_000))).isInstanceOf(FeedFormatException.class);
    }

    @Test
    @DisplayName("nesting up to the limit is accepted, one level more is refused")
    void nestingLimit() throws Exception {
        // the root object is level 1, so the 'extra' value adds MAX_DEPTH - 1 levels
        String atLimit = "{\"schemaVersion\":1,\"extra\":" + "[".repeat(JsonNesting.MAX_DEPTH - 1)
                + "]".repeat(JsonNesting.MAX_DEPTH - 1) + "}";
        String overLimit = "{\"schemaVersion\":1,\"extra\":" + "[".repeat(JsonNesting.MAX_DEPTH)
                + "]".repeat(JsonNesting.MAX_DEPTH) + "}";

        assertThat(SecurityFeedParser.parse(atLimit).getAdvisories()).isEmpty();
        assertThatThrownBy(() -> SecurityFeedParser.parse(overLimit)).isInstanceOf(FeedFormatException.class);
    }

    @Test
    @DisplayName("brackets inside strings, escaped quotes and backslashes included, do not count as nesting")
    void bracketsInStringsDoNotCount() throws Exception {
        String brackets = "[{".repeat(100);
        // the note is: \"[{[{...\\  (an escaped quote, the brackets, an escaped backslash)
        String json = "{\"schemaVersion\":1,\"note\":\"\\\"" + brackets + "\\\\\",\"advisories\":[]}";

        assertThat(SecurityFeedParser.parse(json).getAdvisories()).isEmpty();
    }

    @Test
    @DisplayName("a single-quoted string (accepted by org.json) cannot hide deep nesting from the pre-scan")
    void singleQuotedStringCannotHideNesting() {
        int depth = 100_000;
        String json = "{\"schemaVersion\":1,\"x\":'\"',\"y\":" + "[".repeat(depth) + "]".repeat(depth) + "}";

        assertThatThrownBy(() -> SecurityFeedParser.parse(json))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("nested");
    }

    @Test
    @DisplayName("brackets inside a single-quoted string do not count; an apostrophe inside a double-quoted string opens nothing")
    void singleQuotes() throws Exception {
        String brackets = "[{".repeat(100);
        String inSingleQuotes = "{\"schemaVersion\":1,\"note\":'\\'" + brackets + "\"',\"advisories\":[]}";
        String apostrophe = "{\"schemaVersion\":1,\"note\":\"it's\",\"extra\":" + "[".repeat(JsonNesting.MAX_DEPTH)
                + "]".repeat(JsonNesting.MAX_DEPTH) + "}";

        assertThat(SecurityFeedParser.parse(inSingleQuotes).getAdvisories()).isEmpty();
        assertThatThrownBy(() -> SecurityFeedParser.parse(apostrophe)).isInstanceOf(FeedFormatException.class);
    }

    @ParameterizedTest(name = "a quote inside an unquoted token ({0}) opens no string and cannot hide deep nesting")
    @ValueSource(strings = {"a'", "a\"", "a'b'"})
    void quoteInsideATokenCannotHideNesting(String key) {
        int depth = 100_000;
        String json = "{" + key + ":" + "{a:".repeat(depth) + "1" + "}".repeat(depth + 1);

        assertThatThrownBy(() -> SecurityFeedParser.parse(json))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("levels deep")
                .satisfies(e -> assertThat(((FeedFormatException) e).getDetail()).isEqualTo(StoreErrorDetail.INVALID_JSON));
        assertThatThrownBy(() -> ModuleCatalogParser.parse("[" + json + "]"))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("levels deep");
    }

    @Test
    @DisplayName("a quote after whitespace that follows a key or value start still opens a string")
    void quoteAfterWhitespaceOpensAString() throws Exception {
        String brackets = "[{".repeat(100);
        String json = "{ \"schemaVersion\" :\t1 ,\n 'note' : '" + brackets + "' ; \"advisories\": [ ]}";

        assertThat(SecurityFeedParser.parse(json).getAdvisories()).isEmpty();
    }

    private static String advisoryWithFix(String fix) {
        return "{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-1\",\"fixes\":[" + fix + "]}]}";
    }

    @Test
    @DisplayName("a version longer than the limit is malformed, never cut to a valid prefix")
    void overlongVersionsAreMalformed() throws Exception {
        String overlong = "1." + "1".repeat(SecurityFeedParser.MAX_VERSION + 4);
        String overlongQualifier = "1.0-" + "a".repeat(SecurityFeedParser.MAX_VERSION);

        SecurityFeed onlyOverlong = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"" + overlongQualifier + "\"]}"));
        SecurityFeed mixed = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"" + overlongQualifier + "\",\" 2.0.0 \"]}"));
        SecurityFeed flag = SecurityFeedParser.parse("{\"schemaVersion\":1,\"flaggedVersions\":["
                + "{\"name\":\"m\",\"version\":\"" + overlong + "\"},{\"name\":\"n\",\"version\":\"1.4.2\"}]}");

        assertThat(onlyOverlong.getAdvisories()).isEmpty();
        assertThat(onlyOverlong.getSkippedEntries()).isEqualTo(2);
        assertThat(mixed.getAdvisories().get(0).getFixes().get(0).getFixedVersions()).containsExactly("2.0.0");
        assertThat(flag.getFlaggedVersions()).extracting(FlaggedVersion::getName).containsExactly("n");
        assertThat(flag.getSkippedEntries()).isEqualTo(1);
    }

    @ParameterizedTest(name = "flagged version [{0}] is malformed: the flag is skipped and counted")
    @ValueSource(strings = {"latest", "v1.2", "1..2", ""})
    void malformedFlaggedVersionsAreSkipped(String version) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse("{\"schemaVersion\":1,\"flaggedVersions\":["
                + "{\"name\":\"m\",\"version\":\"" + version + "\"}]}");

        assertThat(feed.getFlaggedVersions()).isEmpty();
        assertThat(feed.getSkippedEntries()).isEqualTo(1);
    }

    @Test
    @DisplayName("more than MAX_ENTRIES advisories: the first MAX_ENTRIES are read and the feed is marked truncated")
    void advisoriesCap() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(manyEntries("advisories",
                "{\"id\":\"JSA-1\",\"fixes\":[{\"component\":\"m\",\"fixedVersions\":[\"1.0\"]}]}",
                SecurityFeedParser.MAX_ENTRIES + 1));

        assertThat(feed.getAdvisories()).hasSize(SecurityFeedParser.MAX_ENTRIES);
        assertThat(feed.isTruncated()).isTrue();
        assertThat(feed.getSkippedEntries()).isZero();
    }

    @Test
    @DisplayName("more than MAX_ENTRIES flagged versions: the first MAX_ENTRIES are read and the feed is marked truncated")
    void flagsCap() throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(manyEntries("flaggedVersions", "{\"name\":\"m\",\"version\":\"1.0\"}",
                SecurityFeedParser.MAX_ENTRIES + 1));

        assertThat(feed.getFlaggedVersions()).hasSize(SecurityFeedParser.MAX_ENTRIES);
        assertThat(feed.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("exactly MAX_ENTRIES entries, or skipped entries beyond them, is not truncated")
    void capNotReached() throws Exception {
        SecurityFeed atCap = SecurityFeedParser.parse(manyEntries("flaggedVersions", "{\"name\":\"m\",\"version\":\"1.0\"}",
                SecurityFeedParser.MAX_ENTRIES));

        assertThat(atCap.getFlaggedVersions()).hasSize(SecurityFeedParser.MAX_ENTRIES);
        assertThat(atCap.isTruncated()).isFalse();
        assertThat(SecurityFeedParser.parse(Fixtures.read("security-v1.json")).isTruncated()).isFalse();
    }

    /** A feed whose {@code key} array holds {@code count} times {@code entry}. */
    static String manyEntries(String key, String entry, int count) {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"").append(key).append("\":[");
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "" : ",").append(entry);
        }
        return sb.append("]}").toString();
    }

    @Test
    @DisplayName("introducedVersion: read when present, null when absent")
    void introducedVersion() throws Exception {
        SecurityFeed present = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"2.24.0\"],\"introducedVersion\":\"2.0.0\"}"));
        SecurityFeed absent = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"2.24.0\"]}"));
        SecurityFeed jsonNull = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"2.24.0\"],\"introducedVersion\":null}"));

        assertThat(present.getAdvisories().get(0).getFixes().get(0).getIntroducedVersion()).isEqualTo("2.0.0");
        assertThat(present.getSkippedEntries()).isZero();
        assertThat(absent.getAdvisories().get(0).getFixes().get(0).getIntroducedVersion()).isNull();
        assertThat(jsonNull.getAdvisories().get(0).getFixes().get(0).getIntroducedVersion()).isNull();
        assertThat(jsonNull.getSkippedEntries()).isZero();
    }

    @ParameterizedTest(name = "introducedVersion {0} is malformed: ignored (the fix is kept) and counted")
    @ValueSource(strings = {"\"latest\"", "2", "[\"2.0.0\"]", "\"2.0.0-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\""})
    void malformedIntroducedVersion(String value) throws Exception {
        SecurityFeed feed = SecurityFeedParser.parse(advisoryWithFix(
                "{\"component\":\"m\",\"fixedVersions\":[\"2.24.0\"],\"introducedVersion\":" + value + "}"));

        AdvisoryFix fix = feed.getAdvisories().get(0).getFixes().get(0);
        assertThat(fix.getFixedVersions()).containsExactly("2.24.0");
        assertThat(fix.getIntroducedVersion()).isNull();
        assertThat(feed.getSkippedEntries()).isEqualTo(1);
    }
}
