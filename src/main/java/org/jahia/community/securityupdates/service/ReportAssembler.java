package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.ForgeSettingsReader;
import org.jahia.community.securityupdates.feed.HttpFetcher;
import org.jahia.community.securityupdates.feed.StoreClient;
import org.jahia.community.securityupdates.feed.StoreFetchResult;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.feed.StoreUrls;
import org.jahia.community.securityupdates.matching.MatchResult;
import org.jahia.community.securityupdates.matching.SecurityMatcher;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Builds one report: reads every store, keeps the last data read from each one, matches the installed components
 * and computes the status. Not thread-safe: the service runs one check at a time.
 *
 * <p>When a store fails, the last data read from it is kept (and the error says so), so a transient outage does
 * not empty the report; the status is then {@link ReportStatus#PARTIAL}. The status is {@link ReportStatus#OK} only
 * when every store returned a feed this module understands and every component could be compared.
 */
final class ReportAssembler {

    static final String KEPT_DATA_SUFFIX = " (showing the data of the last successful check of this store)";
    /** Error kinds that do not make the report incomplete. */
    private static final Set<StoreErrorKind> NON_BLOCKING = EnumSet.of(StoreErrorKind.CATALOGUE_UNAVAILABLE);

    private final Map<String, SecurityFeed> lastGoodFeeds = new HashMap<>();
    private final Map<String, ModuleCatalog> lastGoodCatalogs = new HashMap<>();
    /** Keys of the findings already logged; a finding that disappears is logged again when it comes back. */
    private final Set<String> loggedFindings = new HashSet<>();
    private final Consumer<Finding> warnSink;

    ReportAssembler(Consumer<Finding> warnSink) {
        this.warnSink = warnSink;
    }

    /** What the check reads from and the context it runs in. */
    static final class Sources {
        final HttpFetcher httpFetcher;
        final ForgeSettingsReader forgeSettingsReader;
        final InstalledComponentsProvider installedComponents;
        final String jahiaVersion;

        Sources(HttpFetcher httpFetcher, ForgeSettingsReader forgeSettingsReader, InstalledComponentsProvider installedComponents,
                String jahiaVersion) {
            this.httpFetcher = httpFetcher;
            this.forgeSettingsReader = forgeSettingsReader;
            this.installedComponents = installedComponents;
            this.jahiaVersion = jahiaVersion;
        }
    }

    /**
     * @param closed   checked between two stores: when it turns true, the check stops
     * @param previous the report before this check, for its last success time
     * @return the report, or null when the check was stopped
     */
    SecurityReport assemble(Settings settings, Sources sources, Instant now, SecurityReport previous, BooleanSupplier closed) {
        List<StoreError> errors = new ArrayList<>();
        List<StoreTarget> targets = StoreTargets.of(settings, sources.forgeSettingsReader, errors);
        boolean complete = errors.isEmpty();
        if (targets.isEmpty()) {
            errors.add(new StoreError(null, StoreErrorKind.NO_STORE, "No store is configured"));
            complete = false;
        }

        StoreClient client = new StoreClient(sources.httpFetcher);
        List<SecurityFeed> feeds = new ArrayList<>();
        List<ModuleCatalog> catalogs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (StoreTarget target : targets) {
            if (closed.getAsBoolean()) {
                return null;
            }
            String key = StoreUrls.normalize(target.getUrl()).orElse(StoreUrls.display(target.getUrl()));
            if (seen.add(key)) {
                StoreOutcome outcome = collect(client.fetch(target));
                outcome.addTo(feeds, catalogs, errors);
                complete = complete && outcome.complete;
            }
        }
        lastGoodFeeds.keySet().retainAll(seen);
        lastGoodCatalogs.keySet().retainAll(seen);

        List<InstalledComponent> modules = sources.installedComponents.installedModules();
        MatchResult match = new SecurityMatcher(sources.jahiaVersion).match(modules, feeds, ModuleCatalog.merge(catalogs));
        errors.addAll(match.getProblems());
        // a component that could not be compared was not checked: the report is not complete
        complete = complete && match.getProblems().isEmpty();
        logNewFindings(match.getFindings());

        ReportStatus status = status(!feeds.isEmpty(), complete);
        Instant lastSuccess = status == ReportStatus.OK ? now : previous.getLastSuccess();
        return new SecurityReport(status, now, lastSuccess, sources.jahiaVersion, errors, match.getFindings());
    }

    static ReportStatus status(boolean anyFeed, boolean complete) {
        if (!anyFeed) {
            return ReportStatus.UNAVAILABLE;
        }
        return complete ? ReportStatus.OK : ReportStatus.PARTIAL;
    }

    /** What one store contributes to a check. */
    private static final class StoreOutcome {
        private final SecurityFeed feed;
        private final ModuleCatalog catalog;
        private final List<StoreError> errors;
        /** True when the store returned a usable feed and no blocking error. */
        private final boolean complete;

        StoreOutcome(SecurityFeed feed, ModuleCatalog catalog, List<StoreError> errors, boolean complete) {
            this.feed = feed;
            this.catalog = catalog;
            this.errors = errors;
            this.complete = complete;
        }

        void addTo(List<SecurityFeed> feeds, List<ModuleCatalog> catalogs, List<StoreError> allErrors) {
            if (feed != null) {
                feeds.add(feed);
            }
            if (catalog != null) {
                catalogs.add(catalog);
            }
            allErrors.addAll(errors);
        }
    }

    /** The fresh data of a store, or its last good data, and its errors (marked when kept data is shown). */
    private StoreOutcome collect(StoreFetchResult result) {
        String url = result.getStoreUrl();
        boolean freshFeed = result.getFeed() != null;
        if (freshFeed) {
            lastGoodFeeds.put(url, result.getFeed());
        }
        boolean freshCatalog = result.getCatalog() != null;
        if (freshCatalog) {
            lastGoodCatalogs.put(url, result.getCatalog());
        }
        SecurityFeed feed = lastGoodFeeds.get(url);
        ModuleCatalog catalog = lastGoodCatalogs.get(url);
        List<StoreError> errors = new ArrayList<>();
        boolean blocking = false;
        for (StoreError error : result.getErrors()) {
            boolean feedError = !NON_BLOCKING.contains(error.getKind());
            blocking = blocking || feedError;
            boolean showsKeptData = feedError ? !freshFeed && feed != null : !freshCatalog && catalog != null;
            errors.add(showsKeptData ? error.withMessageSuffix(KEPT_DATA_SUFFIX) : error);
        }
        return new StoreOutcome(feed, catalog, errors, freshFeed && !blocking);
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
}
