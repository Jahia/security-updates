package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * Overall state of a security report. Only {@link #OK} with no finding means "no known security update".
 */
@GraphQLName("SecurityUpdatesReportStatus")
@GraphQLDescription("Overall state of the security report")
public enum ReportStatus {
    /** No check has run yet. */
    NOT_CHECKED,
    /** The module is disabled by configuration. */
    DISABLED,
    /**
     * Every configured store returned a fresh security feed this module understands, every installed component
     * was compared, and the report is not stale. A module list that could not be read
     * ({@link StoreErrorKind#CATALOGUE_UNAVAILABLE}) does not prevent it: compatibility is then unknown.
     */
    OK,
    /**
     * A feed is usable (fresh, or kept from an earlier check) but the check is incomplete: a store failed or was
     * only partly understood, a component could not be compared, or the report is stale (its last check is older
     * than twice the refresh interval).
     */
    PARTIAL,
    /** No usable security feed is available: findings cannot be trusted to be complete. */
    UNAVAILABLE
}
