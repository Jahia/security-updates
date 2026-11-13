package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.StoreClient;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.ReportView;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jahia.community.securityupdates.service.ServiceFixture.DEFAULT_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.EMPTY_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.FEED_WITH_FINDING;
import static org.jahia.community.securityupdates.service.ServiceFixture.PRIVATE_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.onDemandInterval;
import static org.jahia.community.securityupdates.service.ServiceFixture.settings;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Schedule, on-demand checks, staleness, concurrency and shutdown of {@link SecurityUpdateService}.
 */
@DisplayName("SecurityUpdateService scheduling")
class SecurityUpdateServiceSchedulingTest {

    private ServiceFixture fixture;
    private SecurityUpdateService service;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        service = fixture.service();
    }

    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.executor).scheduleWithFixedDelay(task.capture(), anyLong(), anyLong(), any(TimeUnit.class));
        return task.getValue();
    }

    @Nested
    @DisplayName("schedule")
    class Schedule {

        @Test
        @DisplayName("schedules the first check shortly after activation, then at the configured interval")
        void schedules() {
            service.apply(new Settings(true, 6, true, "", DEFAULT_STORE));

            verify(fixture.executor).scheduleWithFixedDelay(any(Runnable.class), eq(SecurityUpdateService.INITIAL_DELAY_SECONDS),
                    eq(6L * 3600), eq(TimeUnit.SECONDS));
            assertThat(service.getReportView().getNextCheckAt())
                    .isEqualTo(fixture.clock.instant().plusSeconds(SecurityUpdateService.INITIAL_DELAY_SECONDS));

            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);
            fixture.clock.advance(Duration.ofMinutes(1));
            scheduledTask().run();

            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.OK);
            assertThat(service.getReportView().getNextCheckAt()).isEqualTo(fixture.clock.instant().plus(Duration.ofHours(6)));
        }

        @Test
        @DisplayName("activate and modified read the OSGi configuration")
        void activateAndModified() {
            SecurityUpdatesConfig enabled = mock(SecurityUpdatesConfig.class);
            when(enabled.enabled()).thenReturn(true);
            when(enabled.refreshIntervalHours()).thenReturn(12);
            when(enabled.useForgeSettings()).thenReturn(true);
            when(enabled.defaultStoreUrl()).thenReturn(DEFAULT_STORE);
            when(enabled.onDemandMinIntervalSeconds()).thenReturn(30);
            service.activate(enabled);
            verify(fixture.executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), eq(12L * 3600), any(TimeUnit.class));

            service.modified(mock(SecurityUpdatesConfig.class));

            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.DISABLED);
            verify(fixture.future).cancel(false);
            verify(fixture.executor).shutdownNow();
            assertThat(service.getReportView().getNextCheckAt()).isNull();
        }

        @Test
        @DisplayName("a change of the banner settings only does not restart the schedule; a change of the stores does")
        void reschedulesOnlyForCheckSettings() {
            service.apply(Settings.defaults());

            service.apply(new Settings(true, 24, true, "", Settings.DEFAULT_STORE_URL, new BannerSettings(true,
                    org.jahia.community.securityupdates.model.Severity.LOW, false)));
            service.apply(onDemandInterval(0));
            verify(fixture.executor, times(1)).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            verify(fixture.future, never()).cancel(false);

            service.apply(settings(true, true, "", PRIVATE_STORE));
            verify(fixture.future).cancel(false);
            verify(fixture.executor, times(2)).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("a disabled module reports DISABLED, fetches nothing and runs no thread")
        void disabled() {
            service.apply(settings(false, true, "", DEFAULT_STORE));

            ReportView view = service.refresh();

            assertThat(view.getReport().getStatus()).isEqualTo(ReportStatus.DISABLED);
            assertThat(view.isInProgress()).isFalse();
            assertThat(view.getNextCheckAt()).isNull();
            assertThat(fixture.fetcher.requested).isEmpty();
            assertThat(service.refreshNow().getStatus()).isEqualTo(ReportStatus.DISABLED);
            verify(fixture.executor, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("re-enabling after a disabled period starts a new thread and schedule")
        void reEnable() {
            service.apply(Settings.defaults());
            service.apply(settings(false, true, "", DEFAULT_STORE));
            service.apply(Settings.defaults());

            verify(fixture.executor, times(2)).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.NOT_CHECKED);
        }

        @Test
        @DisplayName("the scheduled task never lets anything escape, an Error included, and keeps the previous findings")
        void scheduledTaskSwallowsFailures() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            scheduledTask().run();
            when(fixture.installed.installedModules()).thenThrow(new LinkageError("class missing"));
            fixture.clock.advance(Duration.ofHours(24));

            scheduledTask().run();

            SecurityReport report = service.getReport();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(report.getFindings()).hasSize(1);
            assertThat(report.getErrors()).extracting(StoreError::getDetail).containsExactly(StoreErrorDetail.CHECK_FAILED);
            assertThat(service.getReportView().isInProgress()).isFalse();
        }

        @Test
        @DisplayName("a stack overflow during the first check gives UNAVAILABLE and does not stop the schedule")
        void stackOverflow() {
            service.apply(Settings.defaults());
            fixture.fetcher.failWith(new StackOverflowError());

            scheduledTask().run();

            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
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

    @Nested
    @DisplayName("on-demand checks")
    class OnDemand {

        @Test
        @DisplayName("refresh starts the check in the background and returns at once with inProgress")
        void refreshIsNonBlocking() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

            ReportView started = service.refresh();

            assertThat(started.isInProgress()).isTrue();
            assertThat(started.getReport().getStatus()).isEqualTo(ReportStatus.NOT_CHECKED);
            assertThat(fixture.fetcher.requested).isEmpty();

            fixture.runSubmitted();

            ReportView done = service.getReportView();
            assertThat(done.isInProgress()).isFalse();
            assertThat(done.getReport().getStatus()).isEqualTo(ReportStatus.OK);
        }

        @Test
        @DisplayName("an on-demand check within the minimum interval of the last start is not started")
        void onDemandRateLimit() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);

            service.refresh();
            fixture.runSubmitted();
            fixture.clock.advance(Duration.ofSeconds(5));
            ReportView second = service.refresh();
            assertThat(second.isInProgress()).isFalse();
            assertThat(fixture.submitted).isEmpty();
            assertThat(second.getReport().getCheckedAt()).isEqualTo(fixture.clock.instant().minusSeconds(5));

            fixture.clock.advance(Duration.ofSeconds(25));
            assertThat(service.refresh().isInProgress()).isTrue();
            fixture.runSubmitted();
            assertThat(fixture.fetcher.requested).hasSize(4);
        }

        @Test
        @DisplayName("onDemandMinIntervalSeconds=0 removes the rate limit")
        void noRateLimit() {
            service.apply(onDemandInterval(0));
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);

            service.refresh();
            fixture.runSubmitted();
            assertThat(service.refresh().isInProgress()).isTrue();
        }

        @Test
        @DisplayName("a refresh while a check runs starts nothing and says a check is in progress")
        void refreshWhileRunningIsCoalesced() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);
            ReportView[] during = new ReportView[1];
            fixture.fetcher.onGet(() -> during[0] = service.refresh());

            service.refreshNow();

            assertThat(during[0].isInProgress()).isTrue();
            assertThat(fixture.submitted).isEmpty();
            assertThat(fixture.fetcher.requested).hasSize(2);
        }

        @Test
        @DisplayName("a scheduled check due while an on-demand check runs is skipped")
        void scheduledSkippedWhileRunning() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);
            Runnable scheduled = scheduledTask();
            fixture.fetcher.onGet(scheduled);

            service.refreshNow();

            assertThat(fixture.fetcher.requested).hasSize(2);
        }

        @Test
        @DisplayName("a rejected start leaves no check marked as running")
        void rejectedStart() {
            service.apply(Settings.defaults());
            org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("shut down"))
                    .when(fixture.executor).execute(any(Runnable.class));

            ReportView view = service.refresh();

            assertThat(view.isInProgress()).isFalse();
        }
    }

    @Nested
    @DisplayName("staleness")
    class Staleness {

        @Test
        @DisplayName("a report older than twice the interval is stale: PARTIAL with a STALE error; younger is not")
        void staleReport() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);
            service.refreshNow();

            fixture.clock.advance(Duration.ofHours(48));
            assertThat(service.getReportView().isStale()).isFalse();
            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.OK);

            fixture.clock.advance(Duration.ofMinutes(1));
            ReportView view = service.getReportView();
            assertThat(view.isStale()).isTrue();
            assertThat(view.getReport().getStatus()).isEqualTo(ReportStatus.PARTIAL);
            assertThat(view.getReport().getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.STALE);
            assertThat(service.getBanner()).isNotNull();
        }

        @Test
        @DisplayName("without any check since the schedule started twice the interval ago, the report is stale")
        void neverChecked() {
            service.apply(Settings.defaults());
            fixture.clock.advance(Duration.ofHours(49));

            ReportView view = service.getReportView();

            assertThat(view.isStale()).isTrue();
            assertThat(view.getReport().getStatus()).isEqualTo(ReportStatus.NOT_CHECKED);
        }

        @Test
        @DisplayName("an UNAVAILABLE report stays UNAVAILABLE when stale; a disabled module is never stale")
        void staleKeepsWorseStatus() {
            service.apply(Settings.defaults());
            fixture.storeAnswers(DEFAULT_STORE, 404);
            service.refreshNow();
            fixture.clock.advance(Duration.ofDays(3));

            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);

            service.apply(settings(false, true, "", DEFAULT_STORE));
            assertThat(service.getReportView().isStale()).isFalse();
        }
    }

    @Nested
    @DisplayName("concurrency and shutdown")
    class ConcurrencyAndShutdown {

        @Test
        @DisplayName("disabling the check while a check runs leaves the report DISABLED")
        void disabledDuringCheck() {
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            fixture.fetcher.onGet(() -> service.apply(settings(false, true, "", DEFAULT_STORE)));

            SecurityReport report = service.refreshNow();

            assertThat(report.getStatus()).isEqualTo(ReportStatus.DISABLED);
            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.DISABLED);
            assertThat(fixture.fetcher.cancelled).hasPositiveValue();
        }

        @Test
        @DisplayName("deactivation during a check aborts the requests, stops before the next store and publishes nothing")
        void deactivateDuringCheck() {
            fixture.forges.stores = List.of(StoreTarget.store(DEFAULT_STORE, null, null), StoreTarget.store(PRIVATE_STORE, null, null));
            service.apply(Settings.defaults());
            fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            fixture.storeServes(PRIVATE_STORE, EMPTY_FEED);
            fixture.fetcher.onGet(service::deactivate);

            service.refreshNow();

            assertThat(fixture.fetcher.requested).noneMatch(url -> url.startsWith(PRIVATE_STORE));
            assertThat(fixture.fetcher.cancelled).hasValue(1);
            assertThat(service.getReport().getStatus()).isEqualTo(ReportStatus.NOT_CHECKED);
            verify(fixture.executor).shutdownNow();
        }

        @Test
        @DisplayName("deactivation waits for the thread, warns when it does not stop, and keeps an interrupt")
        void shutdown() throws InterruptedException {
            service.apply(Settings.defaults());
            when(fixture.executor.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(false);
            service.deactivate();
            verify(fixture.executor).awaitTermination(5, TimeUnit.SECONDS);

            ServiceFixture interrupted = new ServiceFixture();
            SecurityUpdateService other = interrupted.service();
            other.apply(Settings.defaults());
            when(interrupted.executor.awaitTermination(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
            try {
                other.deactivate();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        @Test
        @DisplayName("after deactivation, nothing starts")
        void nothingAfterDeactivation() {
            service.apply(Settings.defaults());
            service.deactivate();

            assertThat(service.refresh().isInProgress()).isFalse();
            assertThat(service.getReportView().getNextCheckAt()).isNull();
        }

        @Test
        @DisplayName("concurrent refreshes and setting changes on a real thread end in a consistent state")
        void concurrentRefreshAndApply() throws Exception {
            ServiceFixture real = new ServiceFixture();
            List<ScheduledExecutorService> executors = new java.util.concurrent.CopyOnWriteArrayList<>();
            SecurityUpdateService concurrent = new SecurityUpdateService(real.clock, () -> ServiceFixture.JAHIA, () -> {
                ScheduledExecutorService created = SecurityUpdateService.newExecutor();
                executors.add(created);
                return created;
            }, real.warned::add);
            concurrent.setHttpFetcher(real.fetcher);
            concurrent.setForgeSettingsReader(real.forges);
            concurrent.setInstalledComponentsProvider(real.installed);
            real.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
            concurrent.apply(onDemandInterval(0));
            ExecutorService callers = Executors.newFixedThreadPool(4);
            CountDownLatch go = new CountDownLatch(1);
            try {
                List<Future<?>> calls = new java.util.ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    int caller = i;
                    calls.add(callers.submit(() -> {
                        go.await();
                        for (int j = 0; j < 50; j++) {
                            if (caller == 0) {
                                concurrent.apply(j % 2 == 0 ? onDemandInterval(0) : settings(false, true, "", DEFAULT_STORE));
                            } else {
                                concurrent.refresh();
                                concurrent.getBanner();
                            }
                        }
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> call : calls) {
                    call.get(30, TimeUnit.SECONDS);
                }
                concurrent.apply(settings(false, true, "", DEFAULT_STORE));
                assertThat(concurrent.getReport().getStatus()).isEqualTo(ReportStatus.DISABLED);
                assertThat(concurrent.getReportView().isInProgress()).isFalse();
            } finally {
                callers.shutdownNow();
                concurrent.deactivate();
                executors.forEach(ScheduledExecutorService::shutdownNow);
            }
        }
    }

    @Test
    @DisplayName("a finding found by an on-demand check is logged once")
    void onDemandLogs() {
        service.apply(Settings.defaults());
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        service.refresh();
        fixture.runSubmitted();

        assertThat(fixture.warned).extracting(Finding::getComponent).containsExactly("graphql-dxm-provider");
        assertThat(fixture.fetcher.requested).contains(DEFAULT_STORE + StoreClient.SECURITY_FEED_PATH);
    }
}
