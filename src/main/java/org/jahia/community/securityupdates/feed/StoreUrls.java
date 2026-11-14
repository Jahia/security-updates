package org.jahia.community.securityupdates.feed;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates the URLs this module fetches from: only absolute https URLs naming a host, in ASCII, without user
 * information (credentials come from the module manager settings, never from a URL), query or fragment. Plain
 * http is refused: a feed read over http could be altered on the way and hide a vulnerability.
 */
public final class StoreUrls {

    static final int MAX_LENGTH = 2048;
    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    /** Host of the Jahia Store: its advisories take precedence over those of other stores. */
    private static final String JAHIA_STORE_HOST = "store.jahia.com";
    private static final char LAST_ASCII = 0x7E;
    private static final char FIRST_PRINTABLE = 0x21;
    private static final String SCHEME_SEPARATOR = "://";

    private StoreUrls() {
    }

    /**
     * @param url a configured store base URL or feed URL
     * @return the trimmed URL without trailing slashes, or empty when it is not acceptable
     */
    public static Optional<String> normalize(String url) {
        return check(url, HTTPS);
    }

    /**
     * @return true when {@code url} would be acceptable with https but uses plain http: such a URL is refused with
     * the {@code NOT_HTTPS} detail rather than as an invalid URL
     */
    public static boolean isPlainHttp(String url) {
        return check(url, HTTP).isPresent();
    }

    /**
     * @return the lower-case host of {@code url}, empty when it has none
     */
    public static String host(String url) {
        try {
            String host = new URI(url == null ? "" : url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /**
     * @return true when {@code url} is on the Jahia Store host
     */
    public static boolean isJahiaStore(String url) {
        return JAHIA_STORE_HOST.equals(host(url));
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

    /**
     * Drops the user information ({@code user:password@}) of every URL in {@code text} and keeps everything else as
     * written: unlike {@link #display}, the path stays, so that a value shown and saved again keeps its meaning.
     * Used where stored settings are shown or logged; a stored URL with user information is refused when checked.
     *
     * @param text one URL, or a list of URLs (as {@code extraFeedUrls}); null gives null
     */
    public static String withoutUserInfo(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int from = 0;
        int scheme = text.indexOf(SCHEME_SEPARATOR);
        while (scheme >= 0) {
            int authorityStart = scheme + SCHEME_SEPARATOR.length();
            int authorityEnd = authorityStart;
            while (authorityEnd < text.length() && !endsAuthority(text.charAt(authorityEnd))) {
                authorityEnd++;
            }
            int at = text.lastIndexOf('@', authorityEnd - 1);
            sb.append(text, from, authorityStart);
            from = at >= authorityStart ? at + 1 : authorityStart;
            scheme = text.indexOf(SCHEME_SEPARATOR, from);
        }
        return sb.append(text, from, text.length()).toString();
    }

    private static boolean endsAuthority(char c) {
        return c == '/' || c == '?' || c == '#' || c == ',' || Character.isWhitespace(c);
    }

    private static Optional<String> check(String url, String expectedScheme) {
        if (url == null) {
            return Optional.empty();
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH || !isPrintableAscii(trimmed)) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean acceptable = expectedScheme.equals(scheme)
                && !uri.isOpaque()
                && uri.getHost() != null && !uri.getHost().isEmpty()
                && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        return acceptable ? Optional.of(trimmed) : Optional.empty();
    }

    /** Non-ASCII URLs are refused: they could not be written as is in the ISO-8859-1 .cfg file. */
    private static boolean isPrintableAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < FIRST_PRINTABLE || c > LAST_ASCII) {
                return false;
            }
        }
        return true;
    }
}
