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
    /** The store could not be reached or answered with an error status. */
    UNAVAILABLE,
    /** The feed was received but its format or schema version is not understood. */
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
