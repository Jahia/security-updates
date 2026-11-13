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

    /**
     * @param advisories      the advisories
     * @param flaggedVersions the owner-flagged versions
     * @param skippedEntries  the number of entries ignored because they were incomplete or malformed
     */
    public SecurityFeed(List<Advisory> advisories, List<FlaggedVersion> flaggedVersions, int skippedEntries) {
        this.advisories = List.copyOf(advisories);
        this.flaggedVersions = List.copyOf(flaggedVersions);
        this.skippedEntries = skippedEntries;
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
}
