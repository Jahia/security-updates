package org.jahia.community.securityupdates.service;

import org.jahia.bin.Jahia;
import org.jahia.community.securityupdates.feed.ForgeSettingsReader;
import org.jahia.community.securityupdates.feed.HttpFetcher;
import org.jahia.community.securityupdates.feed.StoreClient;
import org.jahia.community.securityupdates.feed.StoreFetchResult;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.feed.StoreUrls;
import org.jahia.community.securityupdates.matching.MatchResult;
import org.jahia.community.securityupdates.matching.SecurityMatcher;
import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Checks the stores' security feeds against the components running on this Jahia, on a schedule and on
 * demand, and keeps the resulting report.
 *
 * <ul>
 *     <li>One daemon thread refreshes the report shortly after activation, then every
 *     {@code refreshIntervalHours}.</li>
 *     <li>When a store fails, the last data read from it is kept (and the error says so), so a transient
 *     outage does not empty the report; the status is then {@link ReportStatus#PARTIAL}.</li>
 *     <li>The status is {@link ReportStatus#OK} only when every configured store returned a feed this module
 *     understands: a missing, unreachable or unknown feed never reads as "up to date".</li>
 *     <li>Each new finding is logged once at WARN level.</li>
 * </ul>
 */
@Component(service = SecurityUpdateService.class, configurationPid = SecurityUpdateService.PID, immediate = true)
@Designate(ocd = SecurityUpdatesConfig.class)
public class SecurityUpdateService {

    /** Configuration PID, also the name of the shipped default {@code .cfg} file. */
    public static final String PID = "org.jahia.community.securityupdates";

    /** Delay between activation (or a configuration change) and the first check. */
    static final long INITIAL_DELAY_SECONDS = 60;
    /** On-demand refreshes closer than this to the previous check return the current report. */
    static final Duration MIN_ON_DEMAND_INTERVAL = Duration.ofSeconds(30);

    private static final Logger logger = LoggerFactory.getLogger(SecurityUpdateService.class);
    private static final long SECONDS_PER_HOUR = 3600;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;
    private static final String STALE_SUFFIX = " (showing the data of the last successful check of this store)";
    /** Error kinds that do not make the report incomplete. */
    private static final Set<StoreErrorKind> NON_BLOCKING = EnumSet.of(StoreErrorKind.CATALOGUE_UNAVAILABLE);

    private final Clock clock;
    private final Supplier<String> jahiaVersion;
    private final Supplier<ScheduledExecutorService> executorFactory;
    private final Consumer<Finding> warnSink;
    private final ReentrantLock refreshLock = new ReentrantLock();
    /** Last feed / catalogue read from each store, keyed by store URL. Guarded by {@link #refreshLock}. */
    private final Map<String, SecurityFeed> lastGoodFeeds = new HashMap<>();
    private final Map<String, ModuleCatalog> lastGoodCatalogs = new HashMap<>();
    /** Keys of the findings already logged. Guarded by {@link #refreshLock}. */
    private final Set<String> loggedFindings = new HashSet<>();

    private volatile HttpFetcher httpFetcher;
    private volatile ForgeSettingsReader forgeSettingsReader;
    private volatile InstalledComponentsProvider installedComponentsProvider;
    private volatile Settings settings = Settings.defaults();
    private volatile SecurityReport report;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduled;

    public SecurityUpdateService() {
        this(Clock.systemUTC(), () -> Jahia.VERSION, SecurityUpdateService::newExecutor, SecurityUpdateService::logFinding);
    }

    SecurityUpdateService(Clock clock, Supplier<String> jahiaVersion, Supplier<ScheduledExecutorService> executorFactory,
                          Consumer<Finding> warnSink) {
        this.clock = clock;
        this.jahiaVersion = jahiaVersion;
        this.executorFactory = executorFactory;
        this.warnSink = warnSink;
    }

    @Reference
    public void setHttpFetcher(HttpFetcher httpFetcher) {
        this.httpFetcher = httpFetcher;
    }

    @Reference
    public void setForgeSettingsReader(ForgeSettingsReader forgeSettingsReader) {
        this.forgeSettingsReader = forgeSettingsReader;
    }

    @Reference
    public void setInstalledComponentsProvider(InstalledComponentsProvider installedComponentsProvider) {
        this.installedComponentsProvider = installedComponentsProvider;
    }

    @Activate
    public void activate(SecurityUpdatesConfig config) {
        apply(Settings.from(config, logger::warn));
    }

    @Modified
    public void modified(SecurityUpdatesConfig config) {
        apply(Settings.from(config, logger::warn));
    }

    /**
     * Applies settings and (re)schedules the periodic check.
     */
    synchronized void apply(Settings newSettings) {
        this.settings = newSettings;
        if (report == null || report.getStatus() == ReportStatus.DISABLED) {
            report = SecurityReport.notChecked(jahiaVersion.get());
        }
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
        if (!newSettings.isEnabled()) {
            report = disabledReport();
            logger.info("Security updates check disabled by configuration");
            return;
        }
        if (executor == null) {
            executor = executorFactory.get();
        }
        long interval = newSettings.getRefreshIntervalHours() * SECONDS_PER_HOUR;
        scheduled = executor.scheduleWithFixedDelay(this::scheduledRefresh, INITIAL_DELAY_SECONDS, interval, TimeUnit.SECONDS);
        logger.info("Security updates check scheduled every {} hour(s)", newSettings.getRefreshIntervalHours());
    }

    @Deactivate
    public synchronized void deactivate() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn("The security updates check did not stop within {} seconds", SHUTDOWN_WAIT_SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    /**
     * @return the current report; never null
     */
    public SecurityReport getReport() {
        SecurityReport current = report;
        return current != null ? current : SecurityReport.notChecked(jahiaVersion.get());
    }

    /**
     * @return whether the notice is shown, decided from the current report and the current settings (a
     * configuration change applies at once, without waiting for a check)
     */
    public BannerDecision getBanner() {
        return BannerPolicy.decide(getReport(), settings.getBanner());
    }

    /**
     * Runs a check now, unless the previous check is more recent than {@link #MIN_ON_DEMAND_INTERVAL}.
     *
     * @return the resulting (or current) report
     */
    public SecurityReport refresh() {
        refreshLock.lock();
        try {
            if (!settings.isEnabled()) {
                return getReport();
            }
            Instant checkedAt = getReport().getCheckedAt();
            if (checkedAt != null && checkedAt.plus(MIN_ON_DEMAND_INTERVAL).isAfter(clock.instant())) {
                return getReport();
            }
            return refreshNow();
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Runs a check now. Never throws: an unexpected failure keeps the previous findings and is reported.
     */
    SecurityReport refreshNow() {
        refreshLock.lock();
        try {
            if (!settings.isEnabled()) {
                report = disabledReport();
                return report;
            }
            SecurityReport next;
            try {
                next = check(settings, clock.instant());
            } catch (RuntimeException | StackOverflowError e) {
                logger.error("The security updates check failed unexpectedly", e);
                next = failedReport(clock.instant());
            }
            if (!settings.isEnabled()) {
                // disabled while the check ran: the configuration wins
                next = disabledReport();
            }
            report = next;
            return next;
        } finally {
            refreshLock.unlock();
        }
    }

    static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "security-updates-check");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ------------------------------------------------------------------------------------------ internals

    private void scheduledRefresh() {
        try {
            refreshNow();
        } catch (RuntimeException | StackOverflowError e) {
            // never let an exception escape: it would cancel the periodic schedule
            logger.error("The scheduled security updates check failed", e);
        }
    }

    private SecurityReport check(Settings current, Instant now) {
        String version = jahiaVersion.get();
        List<StoreError> errors = new ArrayList<>();
        boolean complete = true;

        List<StoreTarget> targets = targets(current, errors);
        if (!errors.isEmpty()) {
            complete = false;
        }
        if (targets.isEmpty()) {
            errors.add(new StoreError(null, StoreErrorKind.NO_STORE, "No store is configured"));
            complete = false;
        }

        StoreClient client = new StoreClient(httpFetcher);
        List<SecurityFeed> feeds = new ArrayList<>();
        List<ModuleCatalog> catalogs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (StoreTarget target : targets) {
            String key = StoreUrls.normalize(target.getUrl()).orElse(StoreUrls.display(target.getUrl()));
            if (!seen.add(key)) {
                continue;
            }
            StoreFetchResult result = client.fetch(target);
            complete &= collect(result, feeds, catalogs, errors);
        }
        lastGoodFeeds.keySet().retainAll(seen);
        lastGoodCatalogs.keySet().retainAll(seen);

        List<InstalledComponent> modules = installedComponentsProvider.installedModules();
        MatchResult match = new SecurityMatcher(version).match(modules, feeds, ModuleCatalog.merge(catalogs));
        errors.addAll(match.getProblems());
        // a component that could not be compared was not checked: the report is not complete
        complete &= match.getProblems().isEmpty();
        logNewFindings(match.getFindings());

        ReportStatus status;
        if (feeds.isEmpty()) {
            status = ReportStatus.UNAVAILABLE;
        } else {
            status = complete ? ReportStatus.OK : ReportStatus.PARTIAL;
        }
        Instant lastSuccess = status == ReportStatus.OK ? now : getReport().getLastSuccess();
        return new SecurityReport(status, now, lastSuccess, version, errors, match.getFindings());
    }

    /**
     * Adds what one store returned (or its last good data) to the lists.
     *
     * @return true when the store returned a usable feed and no blocking error
     */
    private boolean collect(StoreFetchResult result, List<SecurityFeed> feeds, List<ModuleCatalog> catalogs,
                            List<StoreError> errors) {
        String url = result.getStoreUrl();
        boolean usable = result.getFeed() != null;
        SecurityFeed feed = usable ? result.getFeed() : lastGoodFeeds.get(url);
        if (usable) {
            lastGoodFeeds.put(url, feed);
        }
        if (feed != null) {
            feeds.add(feed);
        }
        if (result.getCatalog() != null) {
            lastGoodCatalogs.put(url, result.getCatalog());
        }
        ModuleCatalog catalog = lastGoodCatalogs.get(url);
        if (catalog != null) {
            catalogs.add(catalog);
        }
        boolean blocking = false;
        for (StoreError error : result.getErrors()) {
            boolean feedError = !NON_BLOCKING.contains(error.getKind());
            blocking |= feedError;
            boolean stale = feedError ? !usable && feed != null : result.getCatalog() == null && catalog != null;
            errors.add(stale ? new StoreError(error.getStoreUrl(), error.getKind(), error.getMessage() + STALE_SUFFIX) : error);
        }
        return usable && !blocking;
    }

    private List<StoreTarget> targets(Settings current, List<StoreError> errors) {
        List<StoreTarget> targets = new ArrayList<>();
        if (current.isUseForgeSettings()) {
            try {
                targets.addAll(forgeSettingsReader.readForges());
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("Could not read the module manager store settings, using the default store: {}", e.getMessage());
                logger.debug("Store settings read failure", e);
                errors.add(new StoreError(null, StoreErrorKind.UNAVAILABLE,
                        "The store settings of the module manager could not be read: the default store is used"));
            }
        }
        if (targets.isEmpty() && !current.getDefaultStoreUrl().isEmpty()) {
            targets.add(StoreTarget.store(current.getDefaultStoreUrl(), null, null));
        }
        for (String feedUrl : current.getExtraFeedUrls()) {
            targets.add(StoreTarget.feed(feedUrl));
        }
        return targets;
    }

    private void logNewFindings(List<Finding> findings) {
        Set<String> current = new HashSet<>();
        for (Finding finding : findings) {
            String key = finding.key();
            current.add(key);
            if (loggedFindings.add(key)) {
                warnSink.accept(finding);
            }
        }
        loggedFindings.retainAll(current);
    }

    private static void logFinding(Finding finding) {
        logger.warn("Security update available for {} {}: install {} ({} severity, {}, compatibility {}): {}",
                finding.getComponent(), finding.getInstalledVersion(), finding.getRecommendedVersion(),
                finding.getSeverity(), finding.getSource(), finding.getCompatibility(),
                finding.getAdvisories().stream().map(AdvisoryRef::getId).collect(Collectors.joining(", ")));
    }

    private SecurityReport disabledReport() {
        SecurityReport previous = report;
        return new SecurityReport(ReportStatus.DISABLED, null, previous == null ? null : previous.getLastSuccess(),
                jahiaVersion.get(), Collections.emptyList(), Collections.emptyList());
    }

    private SecurityReport failedReport(Instant now) {
        SecurityReport previous = getReport();
        List<StoreError> errors = Collections.singletonList(new StoreError(null, StoreErrorKind.UNAVAILABLE,
                "The check failed unexpectedly; see the Jahia log. The previous findings are shown"));
        ReportStatus status = previous.getFindings().isEmpty() && previous.getStatus() != ReportStatus.OK
                ? ReportStatus.UNAVAILABLE : ReportStatus.PARTIAL;
        return new SecurityReport(status, now, previous.getLastSuccess(), previous.getJahiaVersion(), errors,
                previous.getFindings());
    }
}
