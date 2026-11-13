package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.jahia.services.notification.SsrfProtectionDnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the security feed and the module catalogue of one store, and turns every failure into a
 * {@link StoreError} rather than an exception. Only https URLs are fetched. Error messages are made of this
 * module's own words and status codes: they never repeat text received from a store, and never credentials.
 */
public final class StoreClient {

    /** Path of the security feed below a store base URL. */
    public static final String SECURITY_FEED_PATH = "/contents/modules-repository.securityUpdates.json";
    /** Path of the module catalogue below a store base URL (the one Jahia's module manager reads). */
    public static final String MODULE_LIST_PATH = "/contents/modules-repository.moduleList.json";
    /** Largest security feed accepted. */
    public static final int MAX_FEED_BYTES = 2 * 1024 * 1024;
    /** Largest module catalogue accepted (store.jahia.com serves about 0.6 MB in 2026). */
    public static final int MAX_CATALOG_BYTES = 4 * 1024 * 1024;

    private static final Logger logger = LoggerFactory.getLogger(StoreClient.class);
    private static final String FEED = "Security feed: ";
    private static final String MODULE_LIST = "Module list: ";

    private final HttpFetcher fetcher;

    public StoreClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * @param target the store (or extra feed) to read
     * @return what could be read, and the errors; never throws
     */
    public StoreFetchResult fetch(StoreTarget target) {
        Optional<String> url = StoreUrls.normalize(target.getUrl());
        if (url.isEmpty()) {
            return refused(target);
        }
        String base = url.get();
        List<StoreError> errors = new ArrayList<>();
        Map<String, String> headers = headers(target);

        String feedUrl = target.isFeedOnly() ? base : base + SECURITY_FEED_PATH;
        FeedOutcome feed = readFeed(base, feedUrl, headers, errors);
        // a store that publishes no security feed gives no finding: its catalogue is not needed
        ModuleCatalog catalog = target.isFeedOnly() || feed == FeedOutcome.NOT_PUBLISHED
                ? null : readCatalog(base, headers, errors);
        return new StoreFetchResult(base, feed.feed, catalog, errors);
    }

    private static StoreFetchResult refused(StoreTarget target) {
        String shown = StoreUrls.display(target.getUrl());
        StoreError error;
        if (StoreUrls.isPlainHttp(target.getUrl())) {
            logger.warn("Ignoring the security source {}: plain http is refused, configure an https URL", shown);
            error = new StoreError(shown, StoreErrorKind.INVALID_URL, StoreErrorDetail.NOT_HTTPS,
                    "Plain http is refused: configure an https URL");
        } else {
            logger.warn("Ignoring the security source {}: not an absolute https URL without credentials, query or fragment",
                    shown);
            error = new StoreError(shown, StoreErrorKind.INVALID_URL,
                    "Not an absolute https URL without credentials, query or fragment");
        }
        return new StoreFetchResult(shown, null, null, Collections.singletonList(error));
    }

    /** The feed read, or why there is none. */
    private static final class FeedOutcome {
        static final FeedOutcome NOT_PUBLISHED = new FeedOutcome(null);
        static final FeedOutcome FAILED = new FeedOutcome(null);

        private final SecurityFeed feed;

        private FeedOutcome(SecurityFeed feed) {
            this.feed = feed;
        }
    }

    private FeedOutcome readFeed(String base, String feedUrl, Map<String, String> headers, List<StoreError> errors) {
        HttpResult result;
        try {
            result = fetcher.get(feedUrl, headers, MAX_FEED_BYTES);
        } catch (IOException | RuntimeException e) {
            errors.add(transportError(base, StoreErrorKind.UNAVAILABLE, FEED, e));
            logger.debug("Security feed of {} could not be read", base, e);
            return FeedOutcome.FAILED;
        }
        int status = result.getStatus();
        if (status == HttpStatus.NOT_FOUND) {
            errors.add(new StoreError(base, StoreErrorKind.NOT_PUBLISHED, "The store does not publish a security feed (HTTP 404)"));
            return FeedOutcome.NOT_PUBLISHED;
        }
        if (status != HttpStatus.OK) {
            errors.add(statusError(base, StoreErrorKind.UNAVAILABLE, FEED, status));
            return FeedOutcome.FAILED;
        }
        try {
            SecurityFeed feed = SecurityFeedParser.parse(result.getBody());
            if (feed.getSkippedEntries() > 0) {
                errors.add(new StoreError(base, StoreErrorKind.NOT_UNDERSTOOD, StoreErrorDetail.ENTRIES_SKIPPED,
                        feed.getSkippedEntries() + " entries of the security feed could not be understood and were ignored"));
            }
            return new FeedOutcome(feed);
        } catch (FeedFormatException e) {
            logger.debug("Security feed of {} not understood: {}", base, e.getMessage());
            errors.add(new StoreError(base, StoreErrorKind.NOT_UNDERSTOOD, e.getDetail(), "Security feed not understood: "
                    + e.getMessage()));
            return FeedOutcome.FAILED;
        }
    }

    private ModuleCatalog readCatalog(String base, Map<String, String> headers, List<StoreError> errors) {
        try {
            HttpResult result = fetcher.get(base + MODULE_LIST_PATH, headers, MAX_CATALOG_BYTES);
            if (result.getStatus() != HttpStatus.OK) {
                errors.add(statusError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, MODULE_LIST, result.getStatus()));
                return null;
            }
            ModuleCatalog catalog = ModuleCatalogParser.parse(result.getBody());
            logger.debug("Module list of {}: {} entries skipped", base, catalog.getSkippedEntries());
            if (catalog.isTruncated()) {
                errors.add(new StoreError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, StoreErrorDetail.ENTRIES_SKIPPED,
                        "Module list: only the first " + ModuleCatalogParser.MAX_ENTRIES + " modules were read"));
            }
            return catalog;
        } catch (IOException | RuntimeException e) {
            errors.add(transportError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, MODULE_LIST, e));
            logger.debug("Module list of {} could not be read", base, e);
        } catch (FeedFormatException e) {
            logger.debug("Module list of {} not understood: {}", base, e.getMessage());
            errors.add(new StoreError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, e.getDetail(), "Module list not understood: "
                    + e.getMessage()));
        }
        return null;
    }

    /** Credentials go only to a store (never to an extra feed), and only over https, which is all that is fetched. */
    private static Map<String, String> headers(StoreTarget target) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (!target.isFeedOnly() && target.hasCredentials()) {
            String password = target.getPassword() == null ? "" : target.getPassword();
            String token = Base64.getEncoder().encodeToString(
                    (target.getUser() + ":" + password).getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + token);
        }
        return headers;
    }

    private static StoreError statusError(String base, StoreErrorKind kind, String prefix, int status) {
        if (HttpStatus.isAuthFailure(status)) {
            return new StoreError(base, kind, StoreErrorDetail.AUTH,
                    prefix + "access denied (HTTP " + status + "): check the store credentials");
        }
        if (HttpStatus.isRedirect(status)) {
            return new StoreError(base, kind, StoreErrorDetail.REDIRECT, prefix + "the store answered with a redirect (HTTP "
                    + status + "), which is not followed: check the store URL");
        }
        return new StoreError(base, kind, StoreErrorDetail.HTTP_STATUS, prefix + "HTTP " + status);
    }

    /** The exception message can carry a URL or a proxy answer: only its category and type are reported. */
    private static StoreError transportError(String base, StoreErrorKind kind, String prefix, Exception e) {
        if (e instanceof ResponseTooLargeException) {
            return new StoreError(base, kind, StoreErrorDetail.TOO_LARGE, prefix + "response too large");
        }
        if (isSsrfBlocked(e)) {
            return new StoreError(base, kind, StoreErrorDetail.SSRF_BLOCKED, prefix + "Jahia refused to connect to this "
                    + "address (outbound request screening; see jahia.httpClient.allowedInternalHosts)");
        }
        String type = " (" + e.getClass().getSimpleName() + ")";
        if (e instanceof InterruptedIOException) {
            return new StoreError(base, kind, StoreErrorDetail.TIMEOUT, prefix + "timed out" + type);
        }
        return new StoreError(base, kind, StoreErrorDetail.CONNECTION, prefix + "connection failed" + type);
    }

    private static boolean isSsrfBlocked(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SsrfProtectionDnsResolver.SsrfBlockedException) {
                return true;
            }
        }
        return false;
    }
}
