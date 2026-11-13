package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.ForgeSettingsReader;
import org.jahia.community.securityupdates.feed.HttpFetcher;
import org.jahia.community.securityupdates.feed.HttpResult;
import org.jahia.community.securityupdates.feed.StoreClient;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SecurityUpdateService")
class SecurityUpdateServiceTest {

    private static final String JAHIA = "8.2.2.1";
    private static final String DEFAULT_STORE = "https://store.jahia.com";
    private static final String PRIVATE_STORE = "https://store.example.org";
    private static final String MIRROR_FEED = "https://mirror.example.org/security.json";

    private static final String ADVISORY_GRAPHQL = "{\"id\":\"JSA-2026-0011\",\"title\":\"Injection\",\"severity\":\"high\","
            + "\"cves\":[\"CVE-2026-1234\"],\"url\":\"https://academy.jahia.com/jsa\",\"fixes\":[{\"component\":"
            + "\"graphql-dxm-provider\",\"groupId\":\"org.jahia.modules\",\"fixedVersions\":[\"3.10.0\",\"2.24.0\"]}]}";
    private static final String ADVISORY_JCONTENT = "{\"id\":\"JSA-2026-0020\",\"title\":\"Other\",\"severity\":\"critical\","
            + "\"fixes\":[{\"component\":\"jcontent\",\"fixedVersions\":[\"3.5.0\"]}]}";
    private static final String FEED_WITH_FINDING = "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_GRAPHQL + "]}";
    private static final String SECOND_FEED = "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_JCONTENT + "]}";
    private static final String BOTH_FEED = "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_GRAPHQL + ","
            + ADVISORY_JCONTENT + "]}";
    private static final String EMPTY_FEED = "{\"schemaVersion\":1,\"advisories\":[]}";
    private static final String MODULE_LIST = "[{\"modules\":[{\"groupId\":\"org.jahia.modules\",\"name\":\"graphql-dxm-provider\","
            + "\"versions\":[{\"version\":\"3.10.0\",\"requiredVersion\":\"version-8.2.0.0\"}]}]}]";

    private final FakeFetcher fetcher = new FakeFetcher();
    private final ForgeSettingsReader forges = mock(ForgeSettingsReader.class);
    private final InstalledComponentsProvider installed = mock(InstalledComponentsProvider.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final List<Finding> warned = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-13T10:00:00Z"));
    private SecurityUpdateService service;

    @BeforeEach
    void setUp() throws RepositoryException {
        when(forges.readForges()).thenReturn(Collections.emptyList());
        when(installed.installedModules()).thenReturn(List.of(
                new InstalledComponent("graphql-dxm-provider", "org.jahia.modules", "3.4.0"),
                new InstalledComponent("jcontent", "org.jahia.modules", "3.4.0")));
        service = new SecurityUpdateService(clock, () -> JAHIA, () -> executor, warned::add);
        service.setHttpFetcher(fetcher);
        service.setForgeSettingsReader(forges);
        service.setInstalledComponentsProvider(installed);
        doReturn(mock(ScheduledFuture.class)).when(executor)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
    }

    private void storeServes(String base, String feed) {
        fetcher.answer(base + StoreClient.SECURITY_FEED_PATH, new HttpResult(200, feed));
        fetcher.answer(base + StoreClient.MODULE_LIST_PATH, new HttpResult(200, MODULE_LIST));
    }

    private void storeAnswers(String base, int feedStatus) {
        fetcher.answer(base + StoreClient.SECURITY_FEED_PATH, new HttpResult(feedStatus, "{\"error\":\"cannot build\"}"));
        fetcher.answer(base + StoreClient.MODULE_LIST_PATH, new HttpResult(200, MODULE_LIST));
    }

    private static Settings settings(boolean enabled, boolean useForges, String extra, String defaultStore) {
        return new Settings(enabled, 24, useForges, extra, defaultStore);
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

    @Nested
    @DisplayName("store selection")
    class StoreSelection {

        @Test
        @DisplayName("uses the default store when no forge is configured")
        void defaultStore() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(report.getCheckedAt()).isEqualTo(clock.instant());
            assertThat(report.getLastSuccess()).isEqualTo(clock.instant());
            assertThat(report.getFindings()).singleElement().satisfies(f -> {
                assertThat(f.getComponent()).isEqualTo("graphql-dxm-provider");
                assertThat(f.getRecommendedVersion()).isEqualTo("3.10.0");
                assertThat(f.getCompatibility()).isEqualTo(Compatibility.COMPATIBLE);
            });
            assertThat(report.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("uses the configured forges, with their credentials, instead of the default store")
        void forges() throws RepositoryException {
            when(forges.readForges()).thenReturn(List.of(StoreTarget.store(PRIVATE_STORE, "jdoe", "secret")));
            service.apply(Settings.defaults());
            storeServes(PRIVATE_STORE, FEED_WITH_FINDING);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(fetcher.requested).allMatch(url -> url.startsWith(PRIVATE_STORE));
            assertThat(fetcher.headers.get(PRIVATE_STORE + StoreClient.SECURITY_FEED_PATH)).containsKey("Authorization");
        }

        @Test
        @DisplayName("ignores the forges when useForgeSettings is false")
        void forgesDisabled() throws RepositoryException {
            service.apply(settings(true, false, "", DEFAULT_STORE));
            storeServes(DEFAULT_STORE, EMPTY_FEED);

            assertThat(service.refreshNow().getStatus()).isEqualTo(ReportStatus.OK);
            verify(forges, never()).readForges();
        }

        @Test
        @DisplayName("falls back to the default store, and says so, when the forges cannot be read")
        void forgesUnreadable() throws RepositoryException {
            when(forges.readForges()).thenThrow(new RepositoryException("boom"));
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, EMPTY_FEED);

            SecurityReport report = service.refreshNow();

            assertThat(fetcher.requested).contains(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.UNAVAILABLE);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        }

        @Test
        @DisplayName("adds the extra feed URLs")
        void extraFeeds() {
            service.apply(settings(true, true, " " + MIRROR_FEED + " ,", DEFAULT_STORE));
            storeServes(DEFAULT_STORE, EMPTY_FEED);
            fetcher.answer(MIRROR_FEED, new HttpResult(200, SECOND_FEED));

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(report.getFindings()).extracting(Finding::getComponent).containsExactly("jcontent");
        }

        @Test
        @DisplayName("without any store, the report is UNAVAILABLE, never OK")
        void noStore() {
            service.apply(settings(true, true, "", ""));

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NO_STORE);
        }

        @Test
        @DisplayName("a store listed twice is read once")
        void duplicateStore() throws RepositoryException {
            when(forges.readForges()).thenReturn(List.of(StoreTarget.store(PRIVATE_STORE, null, null),
                    StoreTarget.store(PRIVATE_STORE + "/", null, null)));
            service.apply(Settings.defaults());
            storeServes(PRIVATE_STORE, EMPTY_FEED);

            service.refreshNow();

            assertThat(fetcher.requested).hasSize(2);
        }
    }

    @Nested
    @DisplayName("feed failures")
    class FeedFailures {

        @Test
        @DisplayName("404 store: UNAVAILABLE with NOT_PUBLISHED, not an empty 'up to date' report")
        void notPublished() {
            service.apply(Settings.defaults());
            storeAnswers(DEFAULT_STORE, 404);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getFindings()).isEmpty();
            assertThat(report.getLastSuccess()).isNull();
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NOT_PUBLISHED);
        }

        @Test
        @DisplayName("503 store: UNAVAILABLE")
        void serviceUnavailable() {
            service.apply(Settings.defaults());
            storeAnswers(DEFAULT_STORE, 503);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).singleElement().satisfies(e -> {
                assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
                assertThat(e.getMessage()).contains("503").contains("cannot build");
            });
        }

        @Test
        @DisplayName("unknown schemaVersion: UNAVAILABLE with NOT_UNDERSTOOD")
        void unknownSchema() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, "{\"schemaVersion\":2,\"advisories\":[]}");

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.NOT_UNDERSTOOD);
        }

        @Test
        @DisplayName("keeps the last good data of a store whose feed fails, and says it is stale")
        void staleReportRetention() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            Instant firstCheck = clock.instant();
            service.refreshNow();

            clock.advance(Duration.ofHours(24));
            storeAnswers(DEFAULT_STORE, 503);
            fetcher.answer(DEFAULT_STORE + StoreClient.MODULE_LIST_PATH, new IOException("down"));
            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).extracting(Finding::getComponent).containsExactly("graphql-dxm-provider");
            assertThat(report.getFindings().get(0).getCompatibility()).isEqualTo(Compatibility.COMPATIBLE);
            assertThat(report.getCheckedAt()).isEqualTo(clock.instant());
            assertThat(report.getLastSuccess()).isEqualTo(firstCheck);
            assertThat(report.getErrors()).first().satisfies(e -> assertThat(e.getMessage()).contains("last successful check"));
        }

        @Test
        @DisplayName("one store failing among two gives PARTIAL with the findings of the other")
        void partial() throws RepositoryException {
            when(forges.readForges()).thenReturn(List.of(StoreTarget.store(PRIVATE_STORE, null, null),
                    StoreTarget.store(DEFAULT_STORE, null, null)));
            service.apply(Settings.defaults());
            storeAnswers(PRIVATE_STORE, 404);
            storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getLastSuccess()).isNull();
        }

        @Test
        @DisplayName("a store removed from the configuration no longer contributes its kept data")
        void removedStoreForgotten() throws RepositoryException {
            when(forges.readForges()).thenReturn(List.of(StoreTarget.store(PRIVATE_STORE, null, null)));
            service.apply(Settings.defaults());
            storeServes(PRIVATE_STORE, FEED_WITH_FINDING);
            service.refreshNow();

            when(forges.readForges()).thenReturn(List.of(StoreTarget.store(DEFAULT_STORE, null, null)));
            storeAnswers(DEFAULT_STORE, 404);
            clock.advance(Duration.ofHours(1));
            SecurityReport report = service.refreshNow();

            assertThat(report.getFindings()).isEmpty();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("an unexpected failure keeps the previous findings and reports the failure")
        void unexpectedFailure() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            service.refreshNow();

            when(installed.installedModules()).thenThrow(new IllegalStateException("registry not ready"));
            clock.advance(Duration.ofHours(1));
            SecurityReport report = service.refreshNow();

            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getCheckedAt()).isEqualTo(clock.instant());
            assertThat(report.getErrors()).extracting(StoreError::getKind).contains(StoreErrorKind.UNAVAILABLE);
            assertThat(report.getErrors()).allSatisfy(e -> assertThat(e.getMessage()).doesNotContain("registry not ready"));
        }
    }

    @Test
    @DisplayName("the banner follows the current settings, without waiting for a check")
    void bannerFollowsSettings() {
        service.apply(Settings.defaults());
        storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        service.refreshNow();
        assertThat(service.getBanner().isVisible()).isTrue();
        assertThat(service.getBanner().getCount()).isEqualTo(1);
        assertThat(service.getBanner().getComponents()).containsExactly("graphql-dxm-provider");
        int fetches = fetcher.requested.size();

        service.apply(new Settings(true, 24, true, "", DEFAULT_STORE,
                new BannerSettings(true, org.jahia.community.securityupdates.model.Severity.CRITICAL, true)));

        assertThat(service.getBanner().isVisible()).isFalse();
        assertThat(service.getBanner().getCount()).isZero();
        assertThat(fetcher.requested).hasSize(fetches);
    }

    @Nested
    @DisplayName("completeness")
    class Completeness {

        @Test
        @DisplayName("a component whose version cannot be compared makes the report PARTIAL, never OK")
        void malformedInstalledVersion() {
            when(installed.installedModules()).thenReturn(List.of(new InstalledComponent("odd", "g", "not-a-version")));
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, EMPTY_FEED);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.MALFORMED_VERSION);
        }

        @Test
        @DisplayName("an unusable running Jahia version makes the report PARTIAL")
        void malformedJahiaVersion() {
            SecurityUpdateService odd = new SecurityUpdateService(clock, () -> "custom-build", () -> executor, warned::add);
            odd.setHttpFetcher(fetcher);
            odd.setForgeSettingsReader(forges);
            odd.setInstalledComponentsProvider(installed);
            odd.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, EMPTY_FEED);

            assertThat(odd.refreshNow().getStatus()).isEqualTo(ReportStatus.PARTIAL);
        }

        @Test
        @DisplayName("a fresh feed with ignored entries is used, PARTIAL, and not called stale")
        void partlyUnderstoodFeed() {
            service.apply(Settings.defaults());
            String feed = FEED_WITH_FINDING.replace("\"advisories\":[", "\"advisories\":[\"not an object\",");
            storeServes(DEFAULT_STORE, feed);

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getErrors()).singleElement()
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("last successful check"));
        }

        @Test
        @DisplayName("disabling the check while a check runs leaves the report DISABLED")
        void disabledDuringCheck() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            fetcher.onGet(() -> service.apply(settings(false, true, "", DEFAULT_STORE)));

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.DISABLED);
            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.DISABLED);
        }

        @Test
        @DisplayName("an Error such as a stack overflow does not stop the schedule")
        void scheduledTaskSurvivesStackOverflow() {
            service.apply(Settings.defaults());
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(task.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            fetcher.onGet(() -> {
                throw new StackOverflowError();
            });

            task.getValue().run();

            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("logging and refresh")
    class LoggingAndRefresh {

        @Test
        @DisplayName("warns once per new finding, not on every refresh")
        void warnsOncePerNewFinding() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            service.refreshNow();
            service.refreshNow();
            assertThat(warned).hasSize(1);

            fetcher.answer(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH, new HttpResult(200, BOTH_FEED));
            service.refreshNow();
            assertThat(warned).extracting(Finding::getComponent).containsExactly("graphql-dxm-provider", "jcontent");
        }

        @Test
        @DisplayName("a disabled module reports DISABLED and fetches nothing")
        void disabled() {
            service.apply(settings(false, true, "", DEFAULT_STORE));

            SecurityReport report = service.refresh();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.DISABLED);
            assertThat(fetcher.requested).isEmpty();
            verify(executor, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("an on-demand refresh within the minimum interval returns the current report")
        void onDemandRateLimit() {
            service.apply(Settings.defaults());
            storeServes(DEFAULT_STORE, EMPTY_FEED);

            service.refresh();
            clock.advance(Duration.ofSeconds(5));
            SecurityReport second = service.refresh();
            assertThat(fetcher.requested).hasSize(2);
            assertThat(second.getCheckedAt()).isEqualTo(clock.instant().minusSeconds(5));

            clock.advance(SecurityUpdateService.MIN_ON_DEMAND_INTERVAL);
            service.refresh();
            assertThat(fetcher.requested).hasSize(4);
        }

        @Test
        @DisplayName("schedules the first check shortly after activation, then at the configured interval")
        void schedules() {
            service.apply(new Settings(true, 6, true, "", DEFAULT_STORE));

            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(task.capture(), eq(SecurityUpdateService.INITIAL_DELAY_SECONDS),
                    eq(6L * 3600), eq(TimeUnit.SECONDS));

            storeServes(DEFAULT_STORE, EMPTY_FEED);
            task.getValue().run();
            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.OK);
        }

        @Test
        @DisplayName("the scheduled task never lets an exception escape (it would stop the schedule)")
        void scheduledTaskSwallowsFailures() {
            service.apply(Settings.defaults());
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(task.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            fetcher.failWith(new IllegalStateException("unexpected"));

            task.getValue().run();

            assertThat(service.getReport().getStatus()).isNotEqualTo(ReportStatus.OK);
        }

        @Test
        @DisplayName("a configuration change reschedules, deactivation shuts the thread down")
        void rescheduleAndShutdown() {
            ScheduledFuture<?> first = mock(ScheduledFuture.class);
            doReturn(first).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            service.apply(Settings.defaults());

            service.apply(new Settings(true, 12, true, "", DEFAULT_STORE));
            verify(first).cancel(false);

            service.deactivate();
            verify(executor).shutdownNow();
        }

        @Test
        @DisplayName("the default executor runs on a daemon thread")
        void defaultExecutorIsDaemon() throws Exception {
            ScheduledExecutorService real = SecurityUpdateService.newExecutor();
            try {
                Boolean daemon = real.submit(() -> Thread.currentThread().isDaemon()).get(5, TimeUnit.SECONDS);
                assertThat(daemon).isTrue();
            } finally {
                real.shutdownNow();
            }
        }
    }

    // ---------------------------------------------------------------- test doubles

    /** Answers by URL; records requested URLs and headers. */
    static final class FakeFetcher implements HttpFetcher {
        private final Map<String, Object> answers = new HashMap<>();
        final List<String> requested = new ArrayList<>();
        final Map<String, Map<String, String>> headers = new HashMap<>();
        private RuntimeException failure;
        private Runnable onGet;

        void onGet(Runnable hook) {
            onGet = hook;
        }

        void answer(String url, Object answer) {
            answers.put(url, answer);
        }

        void failWith(RuntimeException e) {
            failure = e;
        }

        @Override
        public HttpResult get(String url, Map<String, String> requestHeaders, int maxBytes) throws IOException {
            if (failure != null) {
                throw failure;
            }
            if (onGet != null) {
                Runnable hook = onGet;
                onGet = null;
                hook.run();
            }
            requested.add(url);
            headers.put(url, requestHeaders);
            Object answer = answers.get(url);
            if (answer instanceof IOException) {
                throw (IOException) answer;
            }
            return answer == null ? new HttpResult(404, "") : (HttpResult) answer;
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
