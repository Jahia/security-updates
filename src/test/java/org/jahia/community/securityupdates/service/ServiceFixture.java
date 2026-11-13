package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.ForgeSettingsException;
import org.jahia.community.securityupdates.feed.ForgeSettingsReader;
import org.jahia.community.securityupdates.feed.HttpFetcher;
import org.jahia.community.securityupdates.feed.HttpResult;
import org.jahia.community.securityupdates.feed.StoreClient;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.SecurityReport;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test doubles of {@link SecurityUpdateService}: a fake fetcher answering by URL, a clock moved by hand, an executor
 * that keeps the submitted tasks until the test runs them, and the feeds used by the tests.
 */
final class ServiceFixture {

    static final String JAHIA = "8.2.2.1";
    static final String DEFAULT_STORE = "https://store.jahia.com";
    static final String PRIVATE_STORE = "https://store.example.org";
    static final String MIRROR_FEED = "https://mirror.example.org/security.json";

    static final String ADVISORY_GRAPHQL = "{\"id\":\"JSA-2026-0011\",\"title\":\"Injection\",\"severity\":\"high\","
            + "\"cves\":[\"CVE-2026-1234\"],\"url\":\"https://academy.jahia.com/jsa\",\"fixes\":[{\"component\":"
            + "\"graphql-dxm-provider\",\"groupId\":\"org.jahia.modules\",\"fixedVersions\":[\"3.10.0\",\"2.24.0\"]}]}";
    static final String ADVISORY_JCONTENT = "{\"id\":\"JSA-2026-0020\",\"title\":\"Other\",\"severity\":\"critical\","
            + "\"fixes\":[{\"component\":\"jcontent\",\"fixedVersions\":[\"3.5.0\"]}]}";
    static final String FEED_WITH_FINDING = "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_GRAPHQL + "]}";
    static final String SECOND_FEED = "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_JCONTENT + "]}";
    static final String BOTH_FEED = "{\"schemaVersion\":1,\"advisories\":[" + ADVISORY_GRAPHQL + "," + ADVISORY_JCONTENT + "]}";
    static final String EMPTY_FEED = "{\"schemaVersion\":1,\"advisories\":[]}";
    static final String MODULE_LIST = "[{\"modules\":[{\"groupId\":\"org.jahia.modules\",\"name\":\"graphql-dxm-provider\","
            + "\"versions\":[{\"version\":\"3.10.0\",\"requiredVersion\":\"version-8.2.0.0\"}]}]}]";

    final FakeFetcher fetcher = new FakeFetcher();
    final FakeForges forges = new FakeForges();
    final InstalledComponentsProvider installed = mock(InstalledComponentsProvider.class);
    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    final List<Runnable> submitted = new CopyOnWriteArrayList<>();
    final List<Finding> warned = new CopyOnWriteArrayList<>();
    /** The task of the last schedule, run by {@link #check}. */
    final AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
    final MutableClock clock = new MutableClock(Instant.parse("2026-11-13T10:00:00Z"));

    ServiceFixture() {
        when(installed.installedModules()).thenReturn(List.of(
                new InstalledComponent("graphql-dxm-provider", "org.jahia.modules", "3.4.0"),
                new InstalledComponent("jcontent", "org.jahia.modules", "3.4.0")));
        doAnswer(invocation -> {
            scheduledTask.set(invocation.getArgument(0));
            return future;
        }).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        doAnswer(invocation -> submitted.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
    }

    SecurityUpdateService service() {
        return service(() -> JAHIA);
    }

    SecurityUpdateService service(java.util.function.Supplier<String> jahiaVersion) {
        SecurityUpdateService service = new SecurityUpdateService(clock, jahiaVersion, () -> executor, warned::add);
        service.setHttpFetcher(fetcher);
        service.setForgeSettingsReader(forges);
        service.setInstalledComponentsProvider(installed);
        return service;
    }

    /**
     * Runs the scheduled task now, in the calling thread, as the executor would when the check is due (no on-demand
     * rate limit applies), unless nothing is scheduled.
     *
     * @return the report after the check
     */
    SecurityReport check(SecurityUpdateService service) {
        Runnable task = scheduledTask.get();
        if (task != null) {
            task.run();
        }
        return service.getReport();
    }

    /** Runs the tasks submitted to the executor (on-demand checks). */
    void runSubmitted() {
        List<Runnable> tasks = new ArrayList<>(submitted);
        submitted.clear();
        tasks.forEach(Runnable::run);
    }

    void storeServes(String base, String feed) {
        fetcher.answer(base + StoreClient.SECURITY_FEED_PATH, new HttpResult(200, feed));
        fetcher.answer(base + StoreClient.MODULE_LIST_PATH, new HttpResult(200, MODULE_LIST));
    }

    void storeAnswers(String base, int feedStatus) {
        fetcher.answer(base + StoreClient.SECURITY_FEED_PATH, new HttpResult(feedStatus, "{\"error\":\"cannot build\"}"));
        fetcher.answer(base + StoreClient.MODULE_LIST_PATH, new HttpResult(200, MODULE_LIST));
    }

    static Settings settings(boolean enabled, boolean useForges, String extra, String defaultStore) {
        return new Settings(enabled, 24, useForges, extra, defaultStore);
    }

    /** Settings with the given on-demand interval, other values as {@link Settings#defaults()}. */
    static Settings onDemandInterval(int seconds) {
        return Settings.fromProperties(Map.of(Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, String.valueOf(seconds)), w -> { });
    }

    /** Answers by URL; records requested URLs and headers; can run a hook on the next request. */
    static final class FakeFetcher implements HttpFetcher {
        private final Map<String, Object> answers = new ConcurrentHashMap<>();
        final List<String> requested = new CopyOnWriteArrayList<>();
        final Map<String, Map<String, String>> headers = new ConcurrentHashMap<>();
        final AtomicInteger cancelled = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<Runnable> onGet = new AtomicReference<>();

        void onGet(Runnable hook) {
            onGet.set(hook);
        }

        void answer(String url, Object answer) {
            answers.put(url, answer);
        }

        void failWith(Throwable e) {
            failure.set(e);
        }

        @Override
        public HttpResult get(String url, Map<String, String> requestHeaders, int maxBytes) throws IOException {
            Runnable hook = onGet.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
            Throwable error = failure.get();
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            requested.add(url);
            headers.put(url, requestHeaders);
            Object answer = answers.get(url);
            if (answer instanceof IOException) {
                throw (IOException) answer;
            }
            return answer == null ? new HttpResult(404, "") : (HttpResult) answer;
        }

        @Override
        public void cancelAll() {
            cancelled.incrementAndGet();
        }
    }

    /** The stores of the module manager. */
    static final class FakeForges implements ForgeSettingsReader {
        List<org.jahia.community.securityupdates.feed.StoreTarget> stores = Collections.emptyList();
        ForgeSettingsException failure;
        final AtomicInteger reads = new AtomicInteger();

        @Override
        public List<org.jahia.community.securityupdates.feed.StoreTarget> readForges() throws ForgeSettingsException {
            reads.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return stores;
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
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
            return now.get();
        }
    }
}
