package org.jahia.community.securityupdates.service;

import org.jahia.bin.Jahia;
import org.jahia.community.securityupdates.feed.ForgeSettingsReader;
import org.jahia.community.securityupdates.feed.HttpFetcher;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.ReportView;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Checks the stores' security feeds against the components running on this Jahia node, on a schedule and on
 * demand, and keeps the resulting report (in memory, per node).
 *
 * <ul>
 *     <li>One daemon thread runs the checks: the first one about a minute after activation (or after a change of
 *     the check settings), then every {@code refreshIntervalHours}. A change of the banner settings only does not
 *     restart the schedule. Disabling the check stops the thread.</li>
 *     <li>One check at a time: an on-demand check ({@link #refresh()}) is started in the background and the call
 *     returns at once; it is not started while a check runs, nor within {@code onDemandMinIntervalSeconds} of the
 *     start of the previous check. A scheduled check due while another runs is skipped.</li>
 *     <li>The report, the settings and the schedule state change under one lock; the network is never read under
 *     it. Deactivation aborts the requests in flight and the check stops before the next store.</li>
 *     <li>A report whose last check is older than twice the refresh interval is "stale": its status is at best
 *     {@link ReportStatus#PARTIAL} and it carries a {@link StoreErrorKind#STALE} error.</li>
 *     <li>Each new finding is logged once at WARN level (again if it disappears and comes back).</li>
 * </ul>
 */
@Component(service = SecurityUpdateService.class, configurationPid = SecurityUpdateService.PID, immediate = true)
@Designate(ocd = SecurityUpdatesConfig.class)
public class SecurityUpdateService {

    /** Configuration PID, also the name of the shipped default {@code .cfg} file. */
    public static final String PID = "org.jahia.community.securityupdates";

    /** Delay between activation (or a change of the check settings) and the first check. */
    static final long INITIAL_DELAY_SECONDS = 60;
    /** A report is stale when its last check is older than this many refresh intervals. */
    static final int STALE_INTERVALS = 2;

    private static final Logger logger = LoggerFactory.getLogger(SecurityUpdateService.class);
    private static final long SECONDS_PER_HOUR = 3600;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final Clock clock;
    private final Supplier<String> jahiaVersion;
    private final Supplier<ScheduledExecutorService> executorFactory;
    private final ReportAssembler assembler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object lock = new Object();

    // ------------------------------------------------ guarded by lock
    private Settings settings = Settings.defaults();
    private SecurityReport report;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduled;
    /** When the current schedule started: the staleness reference before the first check. */
    private Instant scheduledSince;
    private Instant nextCheckAt;
    private boolean running;
    private Instant lastStart;
    private Instant lastEnd;

    // ------------------------------------------------ static mandatory references, set before activation
    private HttpFetcher httpFetcher;
    private ForgeSettingsReader forgeSettingsReader;
    private InstalledComponentsProvider installedComponentsProvider;

    /** Declarative Services constructor: the system clock, the running Jahia version and the WARN log. */
    public SecurityUpdateService() {
        this(Clock.systemUTC(), () -> Jahia.VERSION, SecurityUpdateService::newExecutor, SecurityUpdateService::logFinding);
    }

    /** Test constructor. */
    SecurityUpdateService(Clock clock, Supplier<String> jahiaVersion, Supplier<ScheduledExecutorService> executorFactory,
                          Consumer<Finding> warnSink) {
        this.clock = clock;
        this.jahiaVersion = jahiaVersion;
        this.executorFactory = executorFactory;
        this.assembler = new ReportAssembler(warnSink);
    }

    @Reference
    void setHttpFetcher(HttpFetcher httpFetcher) {
        this.httpFetcher = httpFetcher;
    }

    @Reference
    void setForgeSettingsReader(ForgeSettingsReader forgeSettingsReader) {
        this.forgeSettingsReader = forgeSettingsReader;
    }

    @Reference
    void setInstalledComponentsProvider(InstalledComponentsProvider installedComponentsProvider) {
        this.installedComponentsProvider = installedComponentsProvider;
    }

    @Activate
    void activate(SecurityUpdatesConfig config) {
        closed.set(false);
        apply(Settings.from(config, logger::warn));
    }

    @Modified
    void modified(SecurityUpdatesConfig config) {
        apply(Settings.from(config, logger::warn));
    }

    /**
     * Applies settings. The schedule is restarted only when the check settings change (enabled, interval, stores).
     */
    void apply(Settings newSettings) {
        ScheduledExecutorService toStop = null;
        synchronized (lock) {
            Settings previous = settings;
            boolean scheduledBefore = scheduled != null;
            settings = newSettings;
            if (report == null || report.getStatus() == ReportStatus.DISABLED) {
                report = SecurityReport.notChecked(jahiaVersion.get());
            }
            if (!newSettings.isEnabled()) {
                cancelSchedule();
                report = disabledReport();
                toStop = executor;
                executor = null;
                logger.info("Security updates check disabled by configuration");
            } else if (!scheduledBefore || !previous.sameSchedule(newSettings)) {
                schedule(newSettings);
            }
        }
        if (toStop != null) {
            stop(toStop, false);
        }
    }

    @Deactivate
    void deactivate() {
        ScheduledExecutorService toStop;
        synchronized (lock) {
            closed.set(true);
            cancelSchedule();
            toStop = executor;
            executor = null;
        }
        if (toStop != null) {
            stop(toStop, true);
        }
    }

    /**
     * @return the current report, with the staleness rule applied; never null
     */
    public SecurityReport getReport() {
        return getReportView().getReport();
    }

    /**
     * @return the current report, whether a check is running and when the next one is due
     */
    public ReportView getReportView() {
        synchronized (lock) {
            SecurityReport current = report != null ? report : SecurityReport.notChecked(jahiaVersion.get());
            boolean stale = isStale(current);
            // a check still running after the check was disabled publishes nothing: it is not shown as in progress
            return new ReportView(stale ? staleReport(current) : current, running && settings.isEnabled(), nextCheckAt, stale);
        }
    }

    /**
     * @return the settings in effect (after the configuration fallbacks)
     */
    public Settings getSettings() {
        synchronized (lock) {
            return settings;
        }
    }

    /**
     * @return whether the banner is shown, decided from the current report and the current settings (a configuration
     * change applies at once, without waiting for a check)
     */
    public BannerDecision getBanner() {
        ReportView view = getReportView();
        return BannerPolicy.decide(view.getReport(), getSettings().getBanner());
    }

    /**
     * Starts a check in the background, unless the check is disabled, a check is running, or the previous check
     * started or ended less than {@code onDemandMinIntervalSeconds} ago. Never blocks on the network.
     *
     * @return the current report; {@code inProgress} is true when a check runs
     */
    public ReportView refresh() {
        ScheduledExecutorService target;
        synchronized (lock) {
            target = executor != null && begin(true) ? executor : null;
        }
        if (target != null) {
            try {
                target.execute(() -> runSafely(false));
            } catch (RejectedExecutionException e) {
                logger.warn("The security updates check could not be started: {}", e.getMessage());
                synchronized (lock) {
                    running = false;
                }
            }
        }
        return getReportView();
    }

    /**
     * Runs a check now, in the calling thread, unless one is running (tests and the scheduled task).
     *
     * @return the resulting (or current) report
     */
    SecurityReport refreshNow() {
        boolean started;
        synchronized (lock) {
            started = begin(false);
        }
        if (started) {
            runCheck(false);
        }
        return getReport();
    }

    static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "security-updates-check");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ------------------------------------------------------------------------------------------ internals

    /** Lock held. */
    private void schedule(Settings newSettings) {
        cancelSchedule();
        if (executor == null) {
            executor = executorFactory.get();
        }
        long interval = newSettings.getRefreshIntervalHours() * SECONDS_PER_HOUR;
        scheduled = executor.scheduleWithFixedDelay(this::scheduledCheck, INITIAL_DELAY_SECONDS, interval, TimeUnit.SECONDS);
        Instant now = clock.instant();
        scheduledSince = now;
        nextCheckAt = now.plusSeconds(INITIAL_DELAY_SECONDS);
        logger.info("Security updates check scheduled every {} hour(s)", newSettings.getRefreshIntervalHours());
    }

    /** Lock held. */
    private void cancelSchedule() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
        nextCheckAt = null;
        scheduledSince = null;
    }

    /** Aborts the requests in flight, then stops the thread; waits for it only on deactivation. */
    private void stop(ScheduledExecutorService toStop, boolean wait) {
        HttpFetcher fetcher = httpFetcher;
        if (fetcher != null) {
            fetcher.cancelAll();
        }
        toStop.shutdownNow();
        if (!wait) {
            return;
        }
        try {
            if (!toStop.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("The security updates check did not stop within {} seconds", SHUTDOWN_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lock held. Marks a check as started when one may start.
     *
     * @param onDemand true to apply the on-demand rate limit
     */
    private boolean begin(boolean onDemand) {
        if (closed.get() || !settings.isEnabled() || running) {
            return false;
        }
        Instant now = clock.instant();
        Instant previous = later(lastStart, lastEnd);
        if (onDemand && previous != null && previous.plus(settings.getOnDemandMinInterval()).isAfter(now)) {
            return false;
        }
        running = true;
        lastStart = now;
        return true;
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return a.isAfter(b) ? a : b;
    }

    private void scheduledCheck() {
        boolean started;
        synchronized (lock) {
            started = begin(false);
        }
        if (started) {
            runSafely(true);
        }
    }

    /** The task of the executor: nothing may escape it, or a scheduled task would never run again. */
    @SuppressWarnings("java:S1181") // catching Throwable keeps the schedule alive; the failure is logged and reported
    private void runSafely(boolean scheduledRun) {
        try {
            runCheck(scheduledRun);
        } catch (Throwable t) {
            logger.error("The security updates check failed", t);
        }
    }

    /** Runs one check started by {@link #begin}, then publishes its report and clears the running flag. */
    private void runCheck(boolean scheduledRun) {
        Settings current;
        SecurityReport previous;
        synchronized (lock) {
            current = settings;
            previous = report != null ? report : SecurityReport.notChecked(jahiaVersion.get());
        }
        Instant start = clock.instant();
        SecurityReport next = null;
        boolean stopped = false;
        try {
            next = assembler.assemble(current, sources(), start, previous, this::mustStop);
            stopped = next == null;
        } catch (RuntimeException e) {
            logger.error("The security updates check failed unexpectedly", e);
        } finally {
            publish(next != null || stopped ? next : failedReport(previous, start), scheduledRun);
        }
    }

    /** True once the module stops or the check is disabled: a running check then stops before the next store. */
    private boolean mustStop() {
        synchronized (lock) {
            return closed.get() || !settings.isEnabled();
        }
    }

    private ReportAssembler.Sources sources() {
        return new ReportAssembler.Sources(httpFetcher, forgeSettingsReader, installedComponentsProvider, jahiaVersion.get());
    }

    private void publish(SecurityReport next, boolean scheduledRun) {
        synchronized (lock) {
            running = false;
            lastEnd = clock.instant();
            // a check that was stopped, or that ends after the check was disabled, publishes nothing
            if (next != null && !closed.get() && settings.isEnabled()) {
                report = next;
            }
            if (scheduledRun && scheduled != null) {
                nextCheckAt = clock.instant().plusSeconds(settings.getRefreshIntervalHours() * SECONDS_PER_HOUR);
            }
        }
    }

    /** Lock held. */
    private boolean isStale(SecurityReport current) {
        if (!settings.isEnabled()) {
            return false;
        }
        Instant reference = current.getCheckedAt() != null ? current.getCheckedAt() : scheduledSince;
        if (reference == null) {
            return false;
        }
        Duration limit = Duration.ofHours((long) STALE_INTERVALS * settings.getRefreshIntervalHours());
        return reference.plus(limit).isBefore(clock.instant());
    }

    private static SecurityReport staleReport(SecurityReport current) {
        List<StoreError> errors = new ArrayList<>(current.getErrors());
        errors.add(new StoreError(null, StoreErrorKind.STALE, "The last check is older than twice the refresh interval: "
                + "the scheduled check may have stopped; see the Jahia log"));
        ReportStatus status = current.getStatus() == ReportStatus.OK ? ReportStatus.PARTIAL : current.getStatus();
        return new SecurityReport(status, current.getCheckedAt(), current.getLastSuccess(), current.getJahiaVersion(), errors,
                current.getFindings());
    }

    /** Lock held. */
    private SecurityReport disabledReport() {
        SecurityReport previous = report;
        return new SecurityReport(ReportStatus.DISABLED, null, previous == null ? null : previous.getLastSuccess(),
                jahiaVersion.get(), Collections.emptyList(), Collections.emptyList());
    }

    private static SecurityReport failedReport(SecurityReport previous, Instant now) {
        List<StoreError> errors = Collections.singletonList(new StoreError(null, StoreErrorKind.UNAVAILABLE,
                StoreErrorDetail.CHECK_FAILED, "The check failed unexpectedly; see the Jahia log. The previous findings are shown"));
        ReportStatus status = previous.getFindings().isEmpty() && previous.getStatus() != ReportStatus.OK
                ? ReportStatus.UNAVAILABLE : ReportStatus.PARTIAL;
        return new SecurityReport(status, now, previous.getLastSuccess(), previous.getJahiaVersion(), errors,
                previous.getFindings());
    }

    /** The default WARN line of a new finding: the number of references only, not their (remote) text. */
    static void logFinding(Finding finding) {
        if (logger.isWarnEnabled()) {
            logger.warn("Security update available for {} {}: install {} ({} severity, {}, compatibility {}, {} reference(s))",
                    finding.getComponent(), finding.getInstalledVersion(), finding.getRecommendedVersion(),
                    finding.getSeverity(), finding.getSource(), finding.getCompatibility(), finding.getAdvisories().size());
        }
    }
}
