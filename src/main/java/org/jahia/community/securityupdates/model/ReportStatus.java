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
    /** Every configured store returned a security feed this module understands. */
    OK,
    /** Some stores returned a usable feed (fresh or kept from an earlier check), others failed. */
    PARTIAL,
    /** No usable security feed is available: findings cannot be trusted to be complete. */
    UNAVAILABLE
}
