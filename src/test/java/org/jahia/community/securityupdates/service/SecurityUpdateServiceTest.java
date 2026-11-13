package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.ForgeSettingsException;
import org.jahia.community.securityupdates.feed.HttpResult;
import org.jahia.community.securityupdates.feed.StoreClient;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jahia.community.securityupdates.service.ServiceFixture.ADVISORY_GRAPHQL;
import static org.jahia.community.securityupdates.service.ServiceFixture.BOTH_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.DEFAULT_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.EMPTY_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.FEED_WITH_FINDING;
import static org.jahia.community.securityupdates.service.ServiceFixture.JAHIA;
import static org.jahia.community.securityupdates.service.ServiceFixture.MIRROR_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.PRIVATE_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.SECOND_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.settings;
import static org.mockito.Mockito.when;

/**
 * What one check reports: store selection, failures, completeness, logging. Scheduling and concurrency are in
 * {@link SecurityUpdateServiceSchedulingTest}.
 */
@DisplayName("SecurityUpdateService checks")
class SecurityUpdateServiceTest {

    private ServiceFixture fixture;
    private SecurityUpdateService service;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        service = fixture.service();
    }

    @Test
    @DisplayName("reports NOT_CHECKED before the first check")
    void notChecked() {
        service.apply(Settings.defaults());

        SecurityReport report = service.getReport();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.NOT_CHECKED);
        assertThat(report.getJahiaVersion()).isEqualTo(JAHIA);
        assertThat(report.getCheckedAt()).isNull();
    }

    @Test
    @DisplayName("before activation, the report is NOT_CHECKED and no check can start")
    void beforeActivation() {
        assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.NOT_CHECKED);
        assertThat(service.refresh().isInProgress()).isFalse();
        assertThat(fixture.submitted).isEmpty();
    }

    @Nested
    @DisplayName("store selection")
    class StoreSelection {

        @Test
        @DisplayName("uses the default store when no forge is configured")
        void defaultStore() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(report.getCheckedAt()).isEqualTo(fixture.clock.instant());
            assertThat(report.getLastSuccess()).isEqualTo(fixture.clock.instant());
            assertThat(report.getFindings()).singleElement().satisfies(f -> {
                assertThat(f.getComponent()).isEqualTo("graphql-dxm-provider");
                assertThat(f.getRecommendedVersion()).isEqualTo("3.10.0");
                assertThat(f.getCompatibility()).isEqualTo(Compatibility.COMPATIBLE);
            });
            assertThat(report.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("uses the configured forges, with their credentials, instead of the default store")
        void forges() {
            fixture.forges.stores = List.of(StoreTarget.store(PRIVATE_STORE, "jdoe", "secret"));
            service.apply(Settings.defaults());
            fixture.storeServes(PRIVATE_STORE, FEED_WITH_FINDING);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(fixture.fetcher.requested).allMatch(url -> url.startsWith(PRIVATE_STORE));
            assertThat(fixture.fetcher.headers.get(PRIVATE_STORE + StoreClient.SECURITY_FEED_PATH)).containsKey("Authorization");
        }

        @Test
        @DisplayName("ignores the forges when useForgeSettings is false")
        void forgesDisabled() {
            service.apply(settings(true, false, "", DEFAULT_STORE));
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);

            assertThat(fixture.check(service).getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(fixture.forges.reads).hasValue(0);
        }

        @Test
        @DisplayName("falls back to the default store, and says so (FORGE_SETTINGS_UNREADABLE), when the forges cannot be read")
        void forgesUnreadable() {
            fixture.forges.failure = new ForgeSettingsException("unreadable", new IOException("boom"));
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);

            SecurityReport report = fixture.check(service);

            assertThat(fixture.fetcher.requested).contains(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH);
            assertThat(report.getErrors()).singleElement().satisfies(e -> {
                assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
                assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.FORGE_SETTINGS_UNREADABLE);
            });
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        }

        @Test
        @DisplayName("adds the extra feed URLs")
        void extraFeeds() {
            service.apply(settings(true, true, " " + MIRROR_FEED + " ,", DEFAULT_STORE));
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);
            fixture.fetcher.answer(MIRROR_FEED, new HttpResult(200, SECOND_FEED));

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(report.getFindings()).extracting(Finding::getComponent).containsExactly("jcontent");
        }

        @Test
        @DisplayName("without any store, the report is UNAVAILABLE, never OK")
        void noStore() {
            service.apply(settings(true, true, "", ""));

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NO_STORE);
        }

        @Test
        @DisplayName("a store listed twice is read once")
        void duplicateStore() {
            fixture.forges.stores = List.of(StoreTarget.store(PRIVATE_STORE, null, null), StoreTarget.store(PRIVATE_STORE + "/", null, null));
            service.apply(Settings.defaults());
            fixture.storeServes(PRIVATE_STORE, EMPTY_FEED);

            fixture.check(service);

            assertThat(fixture.fetcher.requested).hasSize(2);
        }

        @Test
        @DisplayName("an advisory of the Jahia Store wins over the same id from another store, whatever the order")
        void jahiaStoreAdvisoryWins() {
            String shadow = "{\"schemaVersion\":1,\"advisories\":[{\"id\":\"JSA-2026-0011\",\"severity\":\"low\","
                    + "\"fixes\":[{\"component\":\"other\",\"fixedVersions\":[\"1.0\"]}]}]}";
            fixture.forges.stores = List.of(StoreTarget.store(PRIVATE_STORE, null, null), StoreTarget.store(DEFAULT_STORE, null, null));
            service.apply(Settings.defaults());
            fixture.storeServes(PRIVATE_STORE, shadow);
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

            SecurityReport report = fixture.check(service);

            assertThat(fixture.fetcher.requested.get(0)).startsWith(DEFAULT_STORE);
            assertThat(report.getFindings()).singleElement().satisfies(f -> {
                assertThat(f.getComponent()).isEqualTo("graphql-dxm-provider");
                assertThat(f.getSeverity()).isEqualTo(Severity.HIGH);
            });
        }

        @Test
        @DisplayName("at most 20 stores and feeds are read, and the report says the others were not")
        void targetsAreCapped() {
            List<StoreTarget> stores = new ArrayList<>();
            for (int i = 0; i < StoreTargets.MAX_TARGETS + 2; i++) {
                stores.add(StoreTarget.store("https://s" + i + ".example.org", null, null));
            }
            fixture.forges.stores = stores;
            service.apply(Settings.defaults());

            SecurityReport report = fixture.check(service);

            assertThat(fixture.fetcher.requested).noneMatch(url -> url.startsWith("https://s20.") || url.startsWith("https://s21."));
            assertThat(report.getErrors()).anySatisfy(e -> assertThat(e.getMessage()).contains("20"));
        }
    }

    @Nested
    @DisplayName("feed failures")
    class FeedFailures {

        @Test
        @DisplayName("404 store: UNAVAILABLE with NOT_PUBLISHED, not an empty 'up to date' report")
        void notPublished() {
            service.apply(Settings.defaults());
            fixture.storeAnswers(DEFAULT_STORE, 404);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getFindings()).isEmpty();
            assertThat(report.getLastSuccess()).isNull();
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NOT_PUBLISHED);
        }

        @Test
        @DisplayName("503 store: UNAVAILABLE, the store's text is not repeated")
        void serviceUnavailable() {
            service.apply(Settings.defaults());
            fixture.storeAnswers(DEFAULT_STORE, 503);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).singleElement().satisfies(e -> {
                assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
                assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.HTTP_STATUS);
                assertThat(e.getMessage()).contains("503").doesNotContain("cannot build");
            });
        }

        @Test
        @DisplayName("unknown schemaVersion: UNAVAILABLE with NOT_UNDERSTOOD")
        void unknownSchema() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, "{\"schemaVersion\":2,\"advisories\":[]}");

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NOT_UNDERSTOOD);
        }

        @Test
        @DisplayName("keeps the last good data of a store whose feed fails, and says so")
        void keptDataOfAFailingStore() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            Instant firstCheck = fixture.clock.instant();
            fixture.check(service);

            fixture.clock.advance(Duration.ofHours(24));
            fixture.storeAnswers(DEFAULT_STORE, 503);
            fixture.fetcher.answer(DEFAULT_STORE + StoreClient.MODULE_LIST_PATH, new IOException("down"));
            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).extracting(Finding::getComponent).containsExactly("graphql-dxm-provider");
            assertThat(report.getFindings().get(0).getCompatibility()).isEqualTo(Compatibility.COMPATIBLE);
            assertThat(report.getCheckedAt()).isEqualTo(fixture.clock.instant());
            assertThat(report.getLastSuccess()).isEqualTo(firstCheck);
            assertThat(report.getErrors()).first().satisfies(e -> assertThat(e.getMessage()).contains("last successful check"));
        }

        @Test
        @DisplayName("one store failing among two gives PARTIAL with the findings of the other")
        void partial() {
            fixture.forges.stores = List.of(StoreTarget.store(PRIVATE_STORE, null, null), StoreTarget.store(DEFAULT_STORE, null, null));
            service.apply(Settings.defaults());
            fixture.storeAnswers(PRIVATE_STORE, 404);
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getLastSuccess()).isNull();
        }

        @Test
        @DisplayName("a plain http store gives INVALID_URL / NOT_HTTPS, is not fetched, and the report is not OK")
        void httpStore() {
            fixture.forges.stores = List.of(StoreTarget.store("http://store.example.org", "jdoe", "secret"));
            service.apply(Settings.defaults());

            SecurityReport report = fixture.check(service);

            assertThat(fixture.fetcher.requested).isEmpty();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.NOT_HTTPS);
        }

        @Test
        @DisplayName("a store removed from the configuration no longer contributes its kept data")
        void removedStoreForgotten() {
            fixture.forges.stores = List.of(StoreTarget.store(PRIVATE_STORE, null, null));
            service.apply(Settings.defaults());
            fixture.storeServes(PRIVATE_STORE, FEED_WITH_FINDING);
            fixture.check(service);

            fixture.forges.stores = List.of(StoreTarget.store(DEFAULT_STORE, null, null));
            fixture.storeAnswers(DEFAULT_STORE, 404);
            fixture.clock.advance(Duration.ofHours(1));
            SecurityReport report = fixture.check(service);

            assertThat(report.getFindings()).isEmpty();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("an unexpected failure keeps the previous findings and reports CHECK_FAILED, without its text")
        void unexpectedFailure() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            fixture.check(service);

            when(fixture.installed.installedModules()).thenThrow(new IllegalStateException("registry not ready"));
            fixture.clock.advance(Duration.ofHours(1));
            SecurityReport report = fixture.check(service);

            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getCheckedAt()).isEqualTo(fixture.clock.instant());
            assertThat(report.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.CHECK_FAILED);
            assertThat(report.getErrors()).allSatisfy(e -> assertThat(e.getMessage()).doesNotContain("registry not ready"));
        }
    }

    @Nested
    @DisplayName("completeness")
    class Completeness {

        @Test
        @DisplayName("a component whose version cannot be compared makes the report PARTIAL, never OK")
        void malformedInstalledVersion() {
            when(fixture.installed.installedModules()).thenReturn(List.of(new InstalledComponent("odd", "g", "not-a-version")));
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.MALFORMED_VERSION);
        }

        @Test
        @DisplayName("an unusable running Jahia version makes the report PARTIAL")
        void malformedJahiaVersion() {
            SecurityUpdateService odd = fixture.service(() -> "custom-build");
            odd.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);

            assertThat(fixture.check(odd).getStatus()).isEqualTo(ReportStatus.PARTIAL);
        }

        @Test
        @DisplayName("a fresh feed with ignored entries is used, PARTIAL, and not called kept data")
        void partlyUnderstoodFeed() {
            service.apply(Settings.defaults());
            String feed = FEED_WITH_FINDING.replace("\"advisories\":[", "\"advisories\":[\"not an object\",");
            fixture.storeServes(DEFAULT_STORE, feed);

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getErrors()).singleElement()
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("last successful check"));
        }

        @Test
        @DisplayName("a feed cut at the entry limit is used, and the report is PARTIAL, never OK")
        void truncatedFeed() {
            service.apply(Settings.defaults());
            StringBuilder feed = new StringBuilder("{\"schemaVersion\":1,\"advisories\":[").append(ServiceFixture.ADVISORY_GRAPHQL);
            for (int i = 0; i < org.jahia.community.securityupdates.feed.SecurityFeedParserAccess.maxEntries(); i++) {
                feed.append(",{\"id\":\"JSA-X\",\"fixes\":[{\"component\":\"not-installed\",\"fixedVersions\":[\"1.0\"]}]}");
            }
            fixture.storeServes(DEFAULT_STORE, feed.append("]}").toString());

            SecurityReport report = fixture.check(service);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getErrors()).singleElement().satisfies(e -> {
                assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_UNDERSTOOD);
                assertThat(e.getDetail()).isEqualTo(org.jahia.community.securityupdates.model.StoreErrorDetail.ENTRIES_SKIPPED);
            });
        }
    }

    @Nested
    @DisplayName("logging, settings and banner")
    class LoggingAndBanner {

        @Test
        @DisplayName("warns once per new finding, and again when a finding disappears then comes back")
        void warnsOncePerNewFinding() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            fixture.check(service);
            fixture.check(service);
            assertThat(fixture.warned).hasSize(1);

            fixture.fetcher.answer(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH, new HttpResult(200, BOTH_FEED));
            fixture.check(service);
            assertThat(fixture.warned).extracting(Finding::getComponent).containsExactly("graphql-dxm-provider", "jcontent");

            fixture.fetcher.answer(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH, new HttpResult(200, SECOND_FEED));
            fixture.check(service);
            fixture.fetcher.answer(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH, new HttpResult(200, BOTH_FEED));
            fixture.check(service);
            assertThat(fixture.warned).extracting(Finding::getComponent)
                    .containsExactly("graphql-dxm-provider", "jcontent", "graphql-dxm-provider");
        }

        @Test
        @DisplayName("the default WARN line gives the number of references, never their text")
        void defaultWarnLine() {
            Finding finding = new Finding("m", "g", "1.0", "1.1", Compatibility.UNKNOWN, null, Severity.HIGH,
                    org.jahia.community.securityupdates.model.FindingSource.OWNER_REPORTED,
                    List.of(new AdvisoryRef("https://evil.example.org/\n[ERROR] forged", null, null, List.of(), Severity.HIGH)));

            SecurityUpdateService.logFinding(finding);

            assertThat(finding.getAdvisories()).hasSize(1);
        }

        @Test
        @DisplayName("exposes the settings in effect, and the banner presentation follows them")
        void settingsInEffect() {
            Settings custom = new Settings(true, 6, false, "", DEFAULT_STORE, new BannerSettings(true, Severity.LOW, false,
                    "#0B3D91", "#FFFFFF", DismissPolicy.NEVER, 24));

            service.apply(custom);

            assertThat(service.getSettings()).isSameAs(custom);
            assertThat(service.getBanner().getBackgroundColor()).isEqualTo("#0B3D91");
            assertThat(service.getBanner().getDismissPolicy()).isEqualTo(DismissPolicy.NEVER);
        }

        @Test
        @DisplayName("the banner follows the current settings, without waiting for a check")
        void bannerFollowsSettings() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_GRAPHQL + "]}");
            fixture.check(service);
            assertThat(service.getBanner().isVisible()).isTrue();
            assertThat(service.getBanner().getCount()).isEqualTo(1);
            assertThat(service.getBanner().getComponents()).containsExactly("graphql-dxm-provider");
            assertThat(service.getBanner().getDismissalKeys()).containsExactly("graphql-dxm-provider|JSA-2026-0011");
            int fetches = fixture.fetcher.requested.size();

            service.apply(new Settings(true, 24, true, "", DEFAULT_STORE, new BannerSettings(true, Severity.CRITICAL, true)));

            assertThat(service.getBanner().isVisible()).isFalse();
            assertThat(service.getBanner().getCount()).isZero();
            assertThat(fixture.fetcher.requested).hasSize(fetches);
        }
    }
}
