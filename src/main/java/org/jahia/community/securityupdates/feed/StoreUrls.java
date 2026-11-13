package org.jahia.community.securityupdates.feed;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates the URLs this module fetches from: only absolute http(s) URLs naming a host, without user
 * information (credentials come from the forge settings, never from a URL), query or fragment.
 */
public final class StoreUrls {

    static final int MAX_LENGTH = 2048;

    private StoreUrls() {
    }

    /**
     * @param url a configured store base URL or feed URL
     * @return the trimmed URL without trailing slashes, or empty when it is not acceptable
     */
    public static Optional<String> normalize(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean acceptable = ("http".equals(scheme) || "https".equals(scheme))
                && !uri.isOpaque()
                && uri.getHost() != null && !uri.getHost().isEmpty()
                && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        return acceptable ? Optional.of(trimmed) : Optional.empty();
    }

    /**
     * @return a form of {@code url} safe to show or log: the URL itself when valid, otherwise its scheme and
     * host only (dropping any user information)
     */
    public static String display(String url) {
        Optional<String> valid = normalize(url);
        if (valid.isPresent()) {
            return valid.get();
        }
        try {
            URI uri = new URI(url == null ? "" : url.trim());
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme() + "://" + uri.getHost();
            }
        } catch (URISyntaxException e) {
            // fall through: nothing of the URL is shown
        }
        return "(invalid URL)";
    }
}
