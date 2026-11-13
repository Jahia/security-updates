package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
 * {@link StoreError} rather than an exception. Never logs nor reports credentials.
 */
public final class StoreClient {

    /** Path of the security feed below a store base URL. */
    public static final String SECURITY_FEED_PATH = "/contents/modules-repository.securityUpdates.json";
    /** Path of the module catalogue below a store base URL (the one Jahia's module manager reads). */
    public static final String MODULE_LIST_PATH = "/contents/modules-repository.moduleList.json";
    /** Largest security feed accepted. */
    public static final int MAX_FEED_BYTES = 2 * 1024 * 1024;
    /** Largest module catalogue accepted (store.jahia.com serves about 0.6 MB in 2026). */
    public static final int MAX_CATALOG_BYTES = 16 * 1024 * 1024;

    private static final Logger logger = LoggerFactory.getLogger(StoreClient.class);
    private static final int MAX_STORE_MESSAGE = 200;
    private static final int SC_OK = 200;
    private static final int SC_NOT_FOUND = 404;
    private static final int SC_UNAUTHORIZED = 401;
    private static final int SC_FORBIDDEN = 403;
    private static final int SC_SERVICE_UNAVAILABLE = 503;
    private static final int REDIRECT_MIN = 300;
    private static final int REDIRECT_MAX = 399;

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
            String shown = StoreUrls.display(target.getUrl());
            logger.warn("Ignoring the security source {}: not an absolute http(s) URL without credentials, query or fragment", shown);
            return new StoreFetchResult(shown, null, null, Collections.singletonList(new StoreError(shown,
                    StoreErrorKind.INVALID_URL, "Not an absolute http(s) URL without credentials, query or fragment")));
        }
        String base = url.get();
        Map<String, String> headers = headers(target);
        List<StoreError> errors = new ArrayList<>();

        String feedUrl = target.isFeedOnly() ? base : base + SECURITY_FEED_PATH;
        SecurityFeed feed = readFeed(base, feedUrl, headers, errors);
        ModuleCatalog catalog = target.isFeedOnly() ? null : readCatalog(base, headers, errors);
        return new StoreFetchResult(base, feed, catalog, errors);
    }

    private SecurityFeed readFeed(String base, String feedUrl, Map<String, String> headers, List<StoreError> errors) {
        HttpResult result;
        try {
            result = fetcher.get(feedUrl, headers, MAX_FEED_BYTES);
        } catch (IOException e) {
            errors.add(new StoreError(base, StoreErrorKind.UNAVAILABLE, "Security feed: " + transportMessage(e)));
            logger.debug("Security feed of {} could not be read", base, e);
            return null;
        }
        int status = result.getStatus();
        if (status == SC_NOT_FOUND) {
            errors.add(new StoreError(base, StoreErrorKind.NOT_PUBLISHED,
                    "The store does not publish a security feed (HTTP 404)"));
            return null;
        }
        if (status != SC_OK) {
            errors.add(new StoreError(base, StoreErrorKind.UNAVAILABLE, "Security feed: " + statusMessage(result)));
            return null;
        }
        try {
            return SecurityFeedParser.parse(result.getBody());
        } catch (FeedFormatException e) {
            errors.add(new StoreError(base, StoreErrorKind.NOT_UNDERSTOOD, "Security feed not understood: " + e.getMessage()));
            return null;
        }
    }

    private ModuleCatalog readCatalog(String base, Map<String, String> headers, List<StoreError> errors) {
        try {
            HttpResult result = fetcher.get(base + MODULE_LIST_PATH, headers, MAX_CATALOG_BYTES);
            if (result.getStatus() != SC_OK) {
                errors.add(new StoreError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, "Module list: " + statusMessage(result)));
                return null;
            }
            return ModuleCatalogParser.parse(result.getBody());
        } catch (IOException e) {
            errors.add(new StoreError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, "Module list: " + transportMessage(e)));
            logger.debug("Module list of {} could not be read", base, e);
        } catch (FeedFormatException e) {
            errors.add(new StoreError(base, StoreErrorKind.CATALOGUE_UNAVAILABLE, "Module list not understood: " + e.getMessage()));
        }
        return null;
    }

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

    private static String statusMessage(HttpResult result) {
        int status = result.getStatus();
        if (status == SC_UNAUTHORIZED || status == SC_FORBIDDEN) {
            return "access denied (HTTP " + status + "): check the store credentials";
        }
        if (status >= REDIRECT_MIN && status <= REDIRECT_MAX) {
            return "the store answered with a redirect (HTTP " + status + "), which is not followed: check the store URL";
        }
        String storeMessage = status == SC_SERVICE_UNAVAILABLE ? storeErrorMessage(result.getBody()) : null;
        return "HTTP " + status + (storeMessage == null ? "" : ": " + storeMessage);
    }

    /** The {@code error} text of a JSON error body, cleaned; null when the body is not such a document. */
    private static String storeErrorMessage(String body) {
        try {
            Object value = new JSONTokener(body).nextValue();
            if (value instanceof JSONObject) {
                Object error = ((JSONObject) value).opt("error");
                return error instanceof String ? UntrustedText.clean((String) error, MAX_STORE_MESSAGE) : null;
            }
        } catch (RuntimeException e) {
            // not JSON: the status alone is reported
        }
        return null;
    }

    private static String transportMessage(IOException e) {
        if (e instanceof ResponseTooLargeException) {
            return "response too large";
        }
        // the exception message can carry a URL or a proxy answer; only its type is reported
        return "connection failed (" + e.getClass().getSimpleName() + ")";
    }
}
