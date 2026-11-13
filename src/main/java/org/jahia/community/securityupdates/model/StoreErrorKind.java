package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * Category of a problem met while building a report.
 */
@GraphQLName("SecurityUpdatesErrorKind")
@GraphQLDescription("Category of a problem met while building the security report")
public enum StoreErrorKind {
    /** The store answered 404 for the security feed: it does not publish security data. */
    NOT_PUBLISHED,
    /**
     * The store could not be reached or answered with an error status; also the stores of the module manager could
     * not be read (detail {@code FORGE_SETTINGS_UNREADABLE}, the default store is used), the check failed
     * unexpectedly (detail {@code CHECK_FAILED}, the previous findings are kept), or more than 20 stores and feeds
     * are configured (only the first 20 are read).
     */
    UNAVAILABLE,
    /**
     * The feed was received but its format or schema version is not understood; also (detail
     * {@code ENTRIES_SKIPPED}) some entries or values of a feed were ignored, a feed listed more than 10,000
     * advisories or flagged versions, or the matching caps were reached (fixed versions per component, references
     * per finding or per report).
     */
    NOT_UNDERSTOOD,
    /** The configured URL is not acceptable (detail NOT_HTTPS for a plain http URL): it is not fetched. */
    INVALID_URL,
    /** The module catalogue could not be read: compatibility is unknown for this store. */
    CATALOGUE_UNAVAILABLE,
    /** An installed version could not be compared. */
    MALFORMED_VERSION,
    /** No store is configured. */
    NO_STORE,
    /** The last check is older than twice the refresh interval: the schedule may have stopped. */
    STALE
}
