package org.jahia.community.securityupdates.feed;

import java.util.Objects;

/**
 * A place to read security data from: a store base URL (with optional credentials), or the direct URL of
 * an extra security feed (no catalogue, no credentials). {@link #toString()} never shows the password.
 */
public final class StoreTarget {

    private final String url;
    private final String user;
    private final String password;
    private final boolean feedOnly;

    private StoreTarget(String url, String user, String password, boolean feedOnly) {
        this.url = Objects.requireNonNull(url, "url");
        this.user = user;
        this.password = password;
        this.feedOnly = feedOnly;
    }

    /**
     * @param baseUrl  the store base URL, such as {@code https://store.jahia.com}
     * @param user     the store user, null or empty for anonymous access
     * @param password the store password; never logged
     */
    public static StoreTarget store(String baseUrl, String user, String password) {
        return new StoreTarget(baseUrl, user, password, false);
    }

    /**
     * @param feedUrl the full URL of a security feed document
     */
    public static StoreTarget feed(String feedUrl) {
        return new StoreTarget(feedUrl, null, null, true);
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public boolean isFeedOnly() {
        return feedOnly;
    }

    public boolean hasCredentials() {
        return user != null && !user.isEmpty();
    }

    @Override
    public String toString() {
        return (feedOnly ? "feed " : "store ") + url + (hasCredentials() ? " (authenticated)" : "");
    }
}
