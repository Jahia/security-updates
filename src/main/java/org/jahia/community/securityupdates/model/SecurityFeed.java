package org.jahia.community.securityupdates.model;

import java.util.List;

/**
 * A parsed store security feed (schema version 1).
 */
public final class SecurityFeed {

    /** The only schema version this module understands. */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final List<Advisory> advisories;
    private final List<FlaggedVersion> flaggedVersions;
    private final int skippedEntries;
    private final boolean truncated;
    private final String storeHost;

    /**
     * @param advisories      the advisories
     * @param flaggedVersions the owner-flagged versions
     * @param skippedEntries  the number of entries (or optional values) ignored because they were incomplete or
     *                        malformed
     */
    public SecurityFeed(List<Advisory> advisories, List<FlaggedVersion> flaggedVersions, int skippedEntries) {
        this(advisories, flaggedVersions, skippedEntries, false);
    }

    /**
     * @param truncated true when the document listed more advisories or flagged versions than were read
     */
    public SecurityFeed(List<Advisory> advisories, List<FlaggedVersion> flaggedVersions, int skippedEntries,
                        boolean truncated) {
        this(advisories, flaggedVersions, skippedEntries, truncated, "");
    }

    private SecurityFeed(List<Advisory> advisories, List<FlaggedVersion> flaggedVersions, int skippedEntries,
                         boolean truncated, String storeHost) {
        this.advisories = List.copyOf(advisories);
        this.flaggedVersions = List.copyOf(flaggedVersions);
        this.skippedEntries = skippedEntries;
        this.truncated = truncated;
        this.storeHost = storeHost == null ? "" : storeHost;
    }

    /**
     * @param host the lower-case host of the store (or extra feed URL) that published this feed
     * @return a copy of this feed that knows where it comes from
     */
    public SecurityFeed withStoreHost(String host) {
        return new SecurityFeed(advisories, flaggedVersions, skippedEntries, truncated, host);
    }

    public List<Advisory> getAdvisories() {
        return advisories;
    }

    public List<FlaggedVersion> getFlaggedVersions() {
        return flaggedVersions;
    }

    public int getSkippedEntries() {
        return skippedEntries;
    }

    /** @return the lower-case host of the store that published this feed; empty when unknown */
    public String getStoreHost() {
        return storeHost;
    }

    /** @return true when entries beyond the limit were not read: the feed is then not fully used */
    public boolean isTruncated() {
        return truncated;
    }
}
