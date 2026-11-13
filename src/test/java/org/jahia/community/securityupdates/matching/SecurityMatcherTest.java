package org.jahia.community.securityupdates.matching;

import org.jahia.community.securityupdates.model.Advisory;
import org.jahia.community.securityupdates.model.AdvisoryFix;
import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.FlaggedVersion;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityMatcher")
class SecurityMatcherTest {

    private static final String JAHIA = "8.2.2.1";
    private static final String GROUP = "org.jahia.modules";

    // ---------------------------------------------------------------- recommendation rule (decision table)

    @Nested
    @DisplayName("advisory rule")
    class AdvisoryRule {

        @ParameterizedTest(name = "module {0} with fixes [{1}] -> {2}")
        @CsvSource(delimiter = ';', value = {
                // multi-line backports 2.24.0 / 3.10.0
                "2.20.0; 2.24.0,3.10.0; 2.24.0",
                "2.24.0; 2.24.0,3.10.0; -",
                "2.30.1; 2.24.0,3.10.0; -",
                "3.5.0;  2.24.0,3.10.0; 3.10.0",
                "3.9.9;  3.10.0,2.24.0; 3.10.0",
                "3.10.0; 2.24.0,3.10.0; -",
                "3.11.0; 2.24.0,3.10.0; -",
                // version before every fix: no fix on its line, smallest fix above it
                "1.4.0;  2.24.0,3.10.0; 2.24.0",
                // version after every fix
                "4.0.0;  2.24.0,3.10.0; -",
                // single-line fix
                "1.0.0;  1.2.0; 1.2.0",
                "1.2.0;  1.2.0; -",
                // snapshot of the fixed version is not the fixed version
                "3.10.0-SNAPSHOT; 3.10.0; 3.10.0",
                // the line has an older fix only, but a later line is fixed: on the line and above the fix -> fine
                "2.25.0; 2.24.0,3.10.0; -",
                // several fixes on the same line: the smallest above the installed version
                "3.1.0; 3.4.0,3.2.0,3.10.0; 3.2.0"
        })
        void moduleDecisionTable(String installed, String fixes, String expected) {
            Optional<String> result = SecurityMatcher.recommendForAdvisory(installed, Arrays.asList(fixes.split(",")), false);
            assertExpected(result, expected);
        }

        @ParameterizedTest(name = "core {0} with fixes [{1}] -> {2}")
        @CsvSource(delimiter = ';', value = {
                // the core line is major.minor.servicePack
                "8.2.3.0; 8.2.3.7,8.1.9.3; 8.2.3.7",
                "8.2.3.7; 8.2.3.7,8.1.9.3; -",
                "8.2.3.8; 8.2.3.7,8.1.9.3; -",
                "8.1.9.1; 8.2.3.7,8.1.9.3; 8.1.9.3",
                "8.1.9.3; 8.2.3.7,8.1.9.3; -",
                // line without a fix: next fix above
                "8.2.2.1; 8.2.3.7,8.1.9.3; 8.2.3.7",
                "8.1.8.0; 8.2.3.7,8.1.9.3; 8.1.9.3",
                "8.0.3.0; 8.2.3.7; 8.2.3.7",
                // after every fix
                "8.2.4.0; 8.2.3.7,8.1.9.3; -",
                // a fix on 8.2.3 does not cover 8.2.2: same major and minor is not the same core line
                "8.2.2.5; 8.2.3.7,8.2.2.6; 8.2.2.6"
        })
        void coreDecisionTable(String installed, String fixes, String expected) {
            Optional<String> result = SecurityMatcher.recommendForAdvisory(installed, Arrays.asList(fixes.split(",")), true);
            assertExpected(result, expected);
        }

        @ParameterizedTest(name = "module {0}, introduced in {1}, fixes [{2}] -> {3}")
        @CsvSource(delimiter = ';', value = {
                // installed below the version that introduced the flaw: not affected
                "1.4.0; 2.0.0; 2.24.0,3.10.0; -",
                "1.99.99; 2.0.0; 2.24.0,3.10.0; -",
                // from the introduced version on, the advisory rule
                "2.0.0; 2.0.0; 2.24.0,3.10.0; 2.24.0",
                "2.30.0; 2.0.0; 2.24.0,3.10.0; -",
                "3.0.0; 2.0.0; 2.24.0,3.10.0; 3.10.0",
                "3.10.0; 2.0.0; 2.24.0,3.10.0; -",
                // a malformed introduced version is ignored: the advisory rule alone (over-report rather than hide)
                "1.4.0; garbage; 2.24.0,3.10.0; 2.24.0"
        })
        void moduleWithIntroducedVersion(String installed, String introduced, String fixes, String expected) {
            assertExpected(SecurityMatcher.recommendForAdvisory(installed, Arrays.asList(fixes.split(",")), introduced, false),
                    expected);
        }

        @ParameterizedTest(name = "core {0}, introduced in {1}, fixes [{2}] -> {3}")
        @CsvSource(delimiter = ';', value = {
                "8.2.0.5; 8.2.1.0; 8.2.3.7; -",
                "8.1.9.0; 8.2.1.0; 8.2.3.7; -",
                "8.2.1.0; 8.2.1.0; 8.2.3.7; 8.2.3.7",
                "8.2.2.0; 8.2.1.0; 8.2.3.7; 8.2.3.7",
                "8.2.3.7; 8.2.1.0; 8.2.3.7; -"
        })
        void coreWithIntroducedVersion(String installed, String introduced, String fixes, String expected) {
            assertExpected(SecurityMatcher.recommendForAdvisory(installed, Arrays.asList(fixes.split(",")), introduced, true),
                    expected);
        }

        @Test
        @DisplayName("match applies the introduced version of a fix entry")
        void matchUsesTheIntroducedVersion() {
            AdvisoryFix introduced = new AdvisoryFix("m", GROUP, List.of("2.24.0", "3.10.0"), "2.0.0");
            SecurityFeed feed = feed(advisory("JSA-1", Severity.HIGH, introduced));

            MatchResult before = new SecurityMatcher(JAHIA).match(List.of(module("m", "1.4.0")), List.of(feed), catalog("m",
                    GROUP, "2.24.0", "version-8.2.0.0"));
            MatchResult after = new SecurityMatcher(JAHIA).match(List.of(module("m", "2.1.0")), List.of(feed), catalog("m",
                    GROUP, "2.24.0", "version-8.2.0.0"));

            assertThat(before.getFindings()).isEmpty();
            assertThat(after.getFindings()).singleElement().satisfies(f -> assertThat(f.getRecommendedVersion()).isEqualTo("2.24.0"));
        }

        @Test
        @DisplayName("without an introduced version, the row 1.4.0 / [2.24.0, 3.10.0] still recommends 2.24.0")
        void noIntroducedVersion() {
            assertThat(SecurityMatcher.recommendForAdvisory("1.4.0", List.of("2.24.0", "3.10.0"), null, false)).contains("2.24.0");
        }

        @Test
        @DisplayName("malformed fixed versions are ignored, well-formed ones still apply (the parser drops and counts them first)")
        void malformedFixedVersionsAreIgnored() {
            assertThat(SecurityMatcher.recommendForAdvisory("1.0.0", List.of("garbage", "1.1.0"), false)).contains("1.1.0");
            assertThat(SecurityMatcher.recommendForAdvisory("1.0.0", List.of("garbage"), false)).isEmpty();
            assertThat(SecurityMatcher.recommendForAdvisory("1.0.0", Collections.emptyList(), false)).isEmpty();
        }
    }

    @Nested
    @DisplayName("owner-flagged rule")
    class FlagRule {

        @ParameterizedTest(name = "{0} with flags [{1}] -> {2}")
        @CsvSource(delimiter = ';', value = {
                "1.4.0; 1.4.2; 1.4.2",
                "1.4.2; 1.4.2; -",
                "1.5.0; 1.4.2; -",
                // another major is not concerned
                "2.0.0; 1.4.2; -",
                "1.0.0; 2.0.1; -",
                // several flags: smallest above
                "1.0.0; 1.6.0,1.4.2; 1.4.2",
                "1.4.5; 1.6.0,1.4.2; 1.6.0"
        })
        void flagDecisionTable(String installed, String flags, String expected) {
            assertExpected(SecurityMatcher.recommendForFlags(installed, Arrays.asList(flags.split(","))), expected);
        }
    }

    private static void assertExpected(Optional<String> result, String expected) {
        if ("-".equals(expected)) {
            assertThat(result).as("not affected").isEmpty();
        } else {
            assertThat(result).as("affected").contains(expected);
        }
    }

    // ---------------------------------------------------------------- full match

    @Nested
    @DisplayName("match")
    class Match {

        @Test
        @DisplayName("an affected module gives one finding with the recommended version and its compatibility")
        void affectedModule() {
            SecurityFeed feed = feed(advisory("JSA-2026-0011", Severity.HIGH,
                    fix("graphql-dxm-provider", GROUP, "3.10.0", "2.24.0")));
            ModuleCatalog catalog = catalog("graphql-dxm-provider", GROUP, "3.10.0", "8.2.0.0");

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(module("graphql-dxm-provider", "3.4.0")), List.of(feed), catalog);

            assertThat(result.getFindings()).hasSize(1);
            Finding finding = result.getFindings().get(0);
            assertThat(finding.getComponent()).isEqualTo("graphql-dxm-provider");
            assertThat(finding.getGroupId()).isEqualTo(GROUP);
            assertThat(finding.getInstalledVersion()).isEqualTo("3.4.0");
            assertThat(finding.getRecommendedVersion()).isEqualTo("3.10.0");
            assertThat(finding.getCompatibility()).isEqualTo(Compatibility.COMPATIBLE);
            assertThat(finding.getRequiredJahiaVersion()).isEqualTo("8.2.0.0");
            assertThat(finding.getSeverity()).isEqualTo(Severity.HIGH);
            assertThat(finding.getSource()).isEqualTo(FindingSource.ADVISORY);
            assertThat(finding.getAdvisories()).extracting(AdvisoryRef::getId).containsExactly("JSA-2026-0011");
            assertThat(finding.getAdvisories().get(0).getCves()).containsExactly("CVE-2026-1234");
            assertThat(result.getProblems()).isEmpty();
        }

        @Test
        @DisplayName("an up-to-date module gives no finding")
        void upToDateModule() {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.HIGH, fix("graphql-dxm-provider", GROUP, "3.10.0", "2.24.0")));
            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(module("graphql-dxm-provider", "2.24.0")), List.of(feed), ModuleCatalog.empty());
            assertThat(result.getFindings()).isEmpty();
        }

        @Test
        @DisplayName("a fix for a component that is not installed gives no finding")
        void unknownComponent() {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.CRITICAL, fix("not-installed", GROUP, "1.0.0")));
            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(module("graphql-dxm-provider", "1.0.0")), List.of(feed), ModuleCatalog.empty());
            assertThat(result.getFindings()).isEmpty();
            assertThat(result.getProblems()).isEmpty();
        }

        @Test
        @DisplayName("a groupId mismatch is not a match, an absent groupId matches any")
        void groupIdMatching() {
            SecurityFeed feed = feed(
                    advisory("JSA-1", Severity.HIGH, fix("my-module", "org.other", "2.0.0")),
                    advisory("JSA-2", Severity.LOW, fix("my-module", null, "2.0.0")));
            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(module("my-module", "1.0.0")), List.of(feed), ModuleCatalog.empty());
            assertThat(result.getFindings()).hasSize(1);
            assertThat(result.getFindings().get(0).getAdvisories()).extracting(AdvisoryRef::getId).containsExactly("JSA-2");
        }

        @Test
        @DisplayName("jahia-core findings use the running Jahia version and require a Jahia upgrade")
        void coreFinding() {
            SecurityFeed feed = feed(advisory("JSA-2026-0012", Severity.CRITICAL,
                    fix(InstalledComponent.JAHIA_CORE, null, "8.2.3.7", "8.1.9.3")));

            MatchResult result = new SecurityMatcher(JAHIA).match(Collections.emptyList(), List.of(feed), ModuleCatalog.empty());

            assertThat(result.getFindings()).hasSize(1);
            Finding finding = result.getFindings().get(0);
            assertThat(finding.getComponent()).isEqualTo(InstalledComponent.JAHIA_CORE);
            assertThat(finding.getInstalledVersion()).isEqualTo(JAHIA);
            assertThat(finding.getRecommendedVersion()).isEqualTo("8.2.3.7");
            assertThat(finding.getCompatibility()).isEqualTo(Compatibility.JAHIA_UPGRADE);
            assertThat(finding.getRequiredJahiaVersion()).isEqualTo("8.2.3.7");
        }

        @Test
        @DisplayName("jahia-core is not affected when the running version is on a fixed line")
        void coreNotAffected() {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.CRITICAL, fix(InstalledComponent.JAHIA_CORE, null, "8.2.2.1")));
            assertThat(new SecurityMatcher(JAHIA).match(Collections.emptyList(), List.of(feed), ModuleCatalog.empty())
                    .getFindings()).isEmpty();
        }

        @Test
        @DisplayName("several advisories on one component are grouped in one row with the highest severity")
        void groupsPerComponent() {
            SecurityFeed feed = feed(
                    advisory("JSA-A", Severity.MEDIUM, fix("graphql-dxm-provider", GROUP, "3.8.0")),
                    advisory("JSA-B", Severity.CRITICAL, fix("graphql-dxm-provider", GROUP, "3.10.0")),
                    advisory("JSA-C", Severity.LOW, fix("graphql-dxm-provider", GROUP, "3.2.0")));

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(module("graphql-dxm-provider", "3.4.0")), List.of(feed), ModuleCatalog.empty());

            assertThat(result.getFindings()).hasSize(1);
            Finding finding = result.getFindings().get(0);
            assertThat(finding.getSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(finding.getRecommendedVersion()).isEqualTo("3.10.0");
            assertThat(finding.getAdvisories()).extracting(AdvisoryRef::getId).containsExactly("JSA-B", "JSA-A");
        }

        @Test
        @DisplayName("the grouped recommendation is a version that fixes every advisory of the row")
        void groupedRecommendationFixesEverything() {
            // JSA-A alone recommends 3.0.0 (no fix on line 2); at 3.0.0 JSA-B (fixed in 3.1.0 on line 3) still applies
            SecurityFeed feed = feed(
                    advisory("JSA-A", Severity.HIGH, fix("m", GROUP, "3.0.0")),
                    advisory("JSA-B", Severity.HIGH, fix("m", GROUP, "2.5.0", "3.1.0")));

            MatchResult result = new SecurityMatcher(JAHIA).match(List.of(module("m", "2.4.0")), List.of(feed),
                    ModuleCatalog.empty());

            assertThat(result.getFindings()).singleElement()
                    .extracting(Finding::getRecommendedVersion).isEqualTo("3.1.0");
        }

        @Test
        @DisplayName("the same advisory published by two stores is listed once")
        void deduplicatesAdvisoriesAcrossStores() {
            SecurityFeed first = feed(advisory("JSA-1", Severity.HIGH, fix("m", GROUP, "2.0.0")));
            SecurityFeed second = feed(advisory("JSA-1", Severity.HIGH, fix("m", GROUP, "2.0.0")));
            MatchResult result = new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")), List.of(first, second),
                    ModuleCatalog.empty());
            assertThat(result.getFindings()).singleElement().satisfies(f -> assertThat(f.getAdvisories()).hasSize(1));
        }

        @Test
        @DisplayName("findings are sorted by severity, critical first, then by component")
        void sortedBySeverity() {
            SecurityFeed feed = feed(
                    advisory("JSA-1", Severity.LOW, fix("a-module", GROUP, "2.0.0")),
                    advisory("JSA-2", Severity.CRITICAL, fix("z-module", GROUP, "2.0.0")),
                    advisory("JSA-3", Severity.HIGH, fix("b-module", GROUP, "2.0.0")),
                    advisory("JSA-4", Severity.HIGH, fix("a-module2", GROUP, "2.0.0")));
            MatchResult result = new SecurityMatcher(JAHIA).match(List.of(
                    module("a-module", "1.0.0"), module("z-module", "1.0.0"), module("b-module", "1.0.0"),
                    module("a-module2", "1.0.0")), List.of(feed), ModuleCatalog.empty());
            assertThat(result.getFindings()).extracting(Finding::getComponent)
                    .containsExactly("z-module", "a-module2", "b-module", "a-module");
        }

        @Test
        @DisplayName("a malformed installed version is reported as a problem and does not stop the report")
        void malformedInstalledVersion() {
            SecurityFeed feed = feed(
                    advisory("JSA-1", Severity.HIGH, fix("broken", GROUP, "2.0.0")),
                    advisory("JSA-2", Severity.HIGH, fix("fine", GROUP, "2.0.0")));
            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(module("broken", "not-a-version"), module("fine", "1.0.0")), List.of(feed), ModuleCatalog.empty());

            assertThat(result.getFindings()).extracting(Finding::getComponent).containsExactly("fine");
            assertThat(result.getProblems()).singleElement().satisfies(p -> {
                assertThat(p.getKind()).isEqualTo(StoreErrorKind.MALFORMED_VERSION);
                assertThat(p.getMessage()).contains("broken");
            });
        }

        @Test
        @DisplayName("a malformed running Jahia version only disables the core and compatibility checks")
        void malformedJahiaVersion() {
            SecurityFeed feed = feed(
                    advisory("JSA-1", Severity.HIGH, fix(InstalledComponent.JAHIA_CORE, null, "8.2.3.7")),
                    advisory("JSA-2", Severity.HIGH, fix("fine", GROUP, "2.0.0")));
            MatchResult result = new SecurityMatcher("unknown").match(List.of(module("fine", "1.0.0")), List.of(feed),
                    catalog("fine", GROUP, "2.0.0", "8.2.0.0"));
            assertThat(result.getFindings()).singleElement().satisfies(f -> {
                assertThat(f.getComponent()).isEqualTo("fine");
                assertThat(f.getCompatibility()).isEqualTo(Compatibility.UNKNOWN);
            });
            assertThat(result.getProblems()).extracting(p -> p.getKind()).containsExactly(StoreErrorKind.MALFORMED_VERSION);
        }

        @Test
        @DisplayName("no feed means no finding")
        void noFeed() {
            assertThat(new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")), Collections.emptyList(),
                    ModuleCatalog.empty()).getFindings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("owner-flagged versions")
    class OwnerFlagged {

        @Test
        @DisplayName("are reported separately from Jahia advisories, labelled as owner reports")
        void ownerReportedFinding() {
            SecurityFeed feed = new SecurityFeed(
                    List.of(advisory("JSA-1", Severity.LOW, fix("my-module", null, "1.5.0"))),
                    List.of(new FlaggedVersion("org.jahia.community", "my-module", "1.4.2", Severity.MEDIUM,
                            List.of("GHSA-xxxx-yyyy", "CVE-2026-9999", "https://example.org/report"), "Fixes an XSS")),
                    0);

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(new InstalledComponent("my-module", "org.jahia.community", "1.4.0")), List.of(feed),
                    ModuleCatalog.empty());

            assertThat(result.getFindings()).hasSize(2);
            Finding owner = result.getFindings().stream().filter(f -> f.getSource() == FindingSource.OWNER_REPORTED)
                    .findFirst().orElseThrow();
            assertThat(owner.getRecommendedVersion()).isEqualTo("1.4.2");
            assertThat(owner.getSeverity()).isEqualTo(Severity.MEDIUM);
            AdvisoryRef ref = owner.getAdvisories().get(0);
            assertThat(ref.getId()).isEqualTo("GHSA-xxxx-yyyy, CVE-2026-9999, https://example.org/report");
            assertThat(ref.getTitle()).isEqualTo("Fixes an XSS");
            assertThat(ref.getCves()).containsExactly("CVE-2026-9999");
            assertThat(ref.getUrl()).isEqualTo("https://example.org/report");
            Finding advisory = result.getFindings().stream().filter(f -> f.getSource() == FindingSource.ADVISORY)
                    .findFirst().orElseThrow();
            assertThat(advisory.getRecommendedVersion()).isEqualTo("1.5.0");
        }

        @Test
        @DisplayName("keep Jahia vulnerability identifiers (JSA-YYYY-NNNN.NNNN) with the CVEs of an owner report")
        void ownerReportKeepsJahiaVulnerabilityIdentifiers() {
            SecurityFeed feed = new SecurityFeed(List.of(),
                    List.of(new FlaggedVersion("org.jahia.community", "my-module", "1.4.2", Severity.HIGH,
                            List.of("JSA-2026-0036.1066", "CVE-2026-9999", "JSA-2026-0036.", "GHSA-xxxx-yyyy"), null)),
                    0);

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(new InstalledComponent("my-module", "org.jahia.community", "1.4.0")), List.of(feed),
                    ModuleCatalog.empty());

            AdvisoryRef ref = result.getFindings().get(0).getAdvisories().get(0);
            assertThat(ref.getCves()).containsExactly("JSA-2026-0036.1066", "CVE-2026-9999");
        }

        @Test
        @DisplayName("the id of an owner report joining many long references is capped")
        void ownerReportIdIsCapped() {
            List<String> references = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                references.add("https://example.org/" + "r".repeat(2000) + i);
            }
            SecurityFeed feed = new SecurityFeed(List.of(),
                    List.of(new FlaggedVersion(null, "my-module", "1.4.2", Severity.HIGH, references, null)), 0);

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(new InstalledComponent("my-module", "org.jahia.community", "1.4.0")), List.of(feed),
                    ModuleCatalog.empty());

            AdvisoryRef ref = result.getFindings().get(0).getAdvisories().get(0);
            assertThat(ref.getId()).hasSizeLessThanOrEqualTo(SecurityMatcher.MAX_OWNER_ID).startsWith("https://example.org/");
            assertThat(ref.getUrl()).isEqualTo(references.get(0));
        }

        @Test
        @DisplayName("several flags: the smallest flagged version above the installed one")
        void severalFlags() {
            SecurityFeed feed = new SecurityFeed(Collections.emptyList(), List.of(
                    new FlaggedVersion(null, "my-module", "1.6.0", Severity.HIGH, Collections.emptyList(), null),
                    new FlaggedVersion(null, "my-module", "1.4.2", null, Collections.emptyList(), null)), 0);

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(new InstalledComponent("my-module", "org.jahia.community", "1.4.0")), List.of(feed),
                    ModuleCatalog.empty());

            assertThat(result.getFindings()).singleElement().satisfies(f -> {
                assertThat(f.getRecommendedVersion()).isEqualTo("1.6.0");
                assertThat(f.getSeverity()).isEqualTo(Severity.HIGH);
                assertThat(f.getAdvisories()).extracting(AdvisoryRef::getId)
                        .containsExactly("owner:my-module@1.6.0", "owner:my-module@1.4.2");
            });
        }

        @Test
        @DisplayName("are ignored for another groupId, another major or a newer installed version")
        void notApplicable() {
            SecurityFeed feed = new SecurityFeed(Collections.emptyList(), List.of(
                    new FlaggedVersion("org.other", "my-module", "1.9.0", Severity.HIGH, Collections.emptyList(), null),
                    new FlaggedVersion(null, "my-module", "2.1.0", Severity.HIGH, Collections.emptyList(), null),
                    new FlaggedVersion(null, "my-module", "1.0.0", Severity.HIGH, Collections.emptyList(), null),
                    new FlaggedVersion(null, "my-module", "bogus", Severity.HIGH, Collections.emptyList(), null)), 0);

            MatchResult result = new SecurityMatcher(JAHIA).match(
                    List.of(new InstalledComponent("my-module", "org.jahia.community", "1.4.0")), List.of(feed),
                    ModuleCatalog.empty());

            assertThat(result.getFindings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("bounded report")
    class BoundedReport {

        private SecurityFeed manyAdvisories(String component, int count) {
            Advisory[] advisories = new Advisory[count];
            for (int i = 0; i < count; i++) {
                advisories[i] = advisory(component + String.format("-JSA-%05d", i), i == count - 1 ? Severity.CRITICAL : Severity.LOW,
                        fix(component, GROUP, "9.0.0"));
            }
            return feed(advisories);
        }

        @Test
        @DisplayName("a finding lists at most MAX_REFS_PER_FINDING advisories, the most severe first, and says so")
        void referencesPerFindingAreCapped() {
            MatchResult result = new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")),
                    List.of(manyAdvisories("m", SecurityMatcher.MAX_REFS_PER_FINDING + 10)), ModuleCatalog.empty());

            Finding finding = result.getFindings().get(0);
            assertThat(finding.getAdvisories()).hasSize(SecurityMatcher.MAX_REFS_PER_FINDING);
            assertThat(finding.getAdvisories().get(0).getSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(finding.getSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(result.getProblems()).singleElement().satisfies(p -> {
                assertThat(p.getKind()).isEqualTo(StoreErrorKind.NOT_UNDERSTOOD);
                assertThat(p.getDetail()).isEqualTo(org.jahia.community.securityupdates.model.StoreErrorDetail.ENTRIES_SKIPPED);
            });
        }

        @Test
        @DisplayName("the whole report lists about MAX_REPORT_REFS advisory references, at least one per finding")
        void referencesPerReportAreCapped() {
            int modules = SecurityMatcher.MAX_REPORT_REFS / SecurityMatcher.MAX_REFS_PER_FINDING + 2;
            List<InstalledComponent> installed = new java.util.ArrayList<>();
            List<SecurityFeed> feeds = new java.util.ArrayList<>();
            for (int i = 0; i < modules; i++) {
                installed.add(module("m" + i, "1.0.0"));
                feeds.add(manyAdvisories("m" + i, SecurityMatcher.MAX_REFS_PER_FINDING));
            }

            MatchResult result = new SecurityMatcher(JAHIA).match(installed, feeds, ModuleCatalog.empty());

            assertThat(result.getFindings()).hasSize(modules);
            assertThat(result.getFindings().stream().mapToInt(f -> f.getAdvisories().size()).sum())
                    .isEqualTo(SecurityMatcher.MAX_REPORT_REFS + 2);
            assertThat(result.getFindings()).allSatisfy(f -> assertThat(f.getAdvisories()).isNotEmpty());
            assertThat(result.getProblems()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("bounded matching work")
    class BoundedWork {

        @Test
        @DisplayName("a crafted feed with thousands of climbing fixed versions for one component matches within a second")
        void craftedFeedMatchesQuickly() {
            int advisories = 5_000;
            Advisory[] crafted = new Advisory[advisories];
            for (int i = 0; i < advisories; i++) {
                String[] versions = new String[20];
                for (int j = 0; j < versions.length; j++) {
                    versions[j] = "1." + (i * versions.length + j + 1) + ".0";
                }
                crafted[i] = advisory("JSA-C" + i, Severity.HIGH, fix("m", GROUP, versions));
            }
            SecurityFeed feed = feed(crafted);

            MatchResult result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                    () -> new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")), List.of(feed), ModuleCatalog.empty()));

            assertThat(result.getFindings()).singleElement().satisfies(f -> assertThat(f.getComponent()).isEqualTo("m"));
            assertThat(result.getProblems()).anySatisfy(p -> assertThat(p.getMessage()).contains("fixed versions"));
        }

        @Test
        @DisplayName("fixed versions up to the limit are all used, without problem")
        void underTheLimit() {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.HIGH, fix("m", GROUP, "1.5.0")),
                    advisory("JSA-2", Severity.HIGH, fix("m", GROUP, "1.6.0")));

            MatchResult result = new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")), List.of(feed), ModuleCatalog.empty());

            assertThat(result.getProblems()).isEmpty();
            assertThat(result.getFindings().get(0).getRecommendedVersion()).isEqualTo("1.6.0");
        }
    }

    @Nested
    @DisplayName("advisories published by several stores")
    class SeveralStores {

        @Test
        @DisplayName("the first feed wins for an advisory id: the service puts the Jahia Store first")
        void firstFeedWins() {
            SecurityFeed official = feed(advisory("JSA-1", Severity.CRITICAL, fix("graphql-dxm-provider", GROUP, "3.10.0")));
            SecurityFeed shadow = feed(advisory("JSA-1", Severity.LOW, fix("other-module", GROUP, "9.0.0")));

            MatchResult result = new SecurityMatcher(JAHIA).match(List.of(module("graphql-dxm-provider", "3.4.0")),
                    List.of(official, shadow), ModuleCatalog.empty());

            assertThat(result.getFindings()).singleElement().satisfies(f -> {
                assertThat(f.getComponent()).isEqualTo("graphql-dxm-provider");
                assertThat(f.getSeverity()).isEqualTo(Severity.CRITICAL);
            });
        }
    }

    @Nested
    @DisplayName("compatibility lookup")
    class CompatibilityLookup {

        @ParameterizedTest(name = "requires {0} on Jahia {1} -> {2}")
        @CsvSource({
                "version-8.2.0.0, 8.2.2.1, COMPATIBLE",
                "8.2.2.1, 8.2.2.1, COMPATIBLE",
                "version-8.2.3.0, 8.2.2.1, INCOMPATIBLE",
                "version-7.2.0.0, 8.2.2.1, INCOMPATIBLE",
                "version-9.0.0.0, 8.2.2.1, INCOMPATIBLE",
                "version-garbage, 8.2.2.1, UNKNOWN"
        })
        void compatibility(String required, String jahia, Compatibility expected) {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.HIGH, fix("m", GROUP, "2.0.0")));
            ModuleCatalog catalog = catalog("m", GROUP, "2.0.0", required);

            Finding finding = new SecurityMatcher(jahia).match(List.of(module("m", "1.0.0")), List.of(feed), catalog)
                    .getFindings().get(0);

            assertThat(finding.getCompatibility()).isEqualTo(expected);
        }

        @Test
        @DisplayName("is UNKNOWN when the recommended version is not in the catalogue")
        void versionNotInCatalogue() {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.HIGH, fix("m", GROUP, "2.0.0")));
            ModuleCatalog catalog = catalog("m", GROUP, "1.9.0", "version-8.0.0.0");

            Finding finding = new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")), List.of(feed), catalog)
                    .getFindings().get(0);

            assertThat(finding.getCompatibility()).isEqualTo(Compatibility.UNKNOWN);
            assertThat(finding.getRequiredJahiaVersion()).isNull();
        }

        @Test
        @DisplayName("the required version is shown without the version- prefix")
        void requiredVersionWithoutPrefix() {
            SecurityFeed feed = feed(advisory("JSA-1", Severity.HIGH, fix("m", GROUP, "2.0.0")));
            Finding finding = new SecurityMatcher(JAHIA).match(List.of(module("m", "1.0.0")), List.of(feed),
                    catalog("m", GROUP, "2.0.0", "version-8.2.3.0")).getFindings().get(0);
            assertThat(finding.getRequiredJahiaVersion()).isEqualTo("8.2.3.0");
        }
    }

    // ---------------------------------------------------------------- fixtures

    static InstalledComponent module(String name, String version) {
        return new InstalledComponent(name, GROUP, version);
    }

    static AdvisoryFix fix(String component, String groupId, String... fixedVersions) {
        return new AdvisoryFix(component, groupId, Arrays.asList(fixedVersions));
    }

    static Advisory advisory(String id, Severity severity, AdvisoryFix... fixes) {
        return new Advisory(id, "Title of " + id, severity, 7.5, List.of("CVE-2026-1234"),
                "https://academy.jahia.com/" + id, "2026-11-03", Arrays.asList(fixes));
    }

    static SecurityFeed feed(Advisory... advisories) {
        return new SecurityFeed(Arrays.asList(advisories), Collections.emptyList(), 0);
    }

    static ModuleCatalog catalog(String name, String groupId, String version, String requiredVersion) {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put(version, requiredVersion);
        Map<String, Map<String, String>> byGroup = new LinkedHashMap<>();
        byGroup.put(groupId, versions);
        Map<String, Map<String, Map<String, String>>> entries = new LinkedHashMap<>();
        entries.put(name, byGroup);
        return new ModuleCatalog(entries);
    }
}
