package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * Who published the information a finding is based on.
 */
@GraphQLName("SecurityUpdatesSource")
@GraphQLDescription("Origin of a finding: a Jahia security advisory or a module owner report")
public enum FindingSource {
    /** A Jahia security advisory of the store feed. */
    ADVISORY,
    /** A version flagged by the module owner on the store. */
    OWNER_REPORTED
}
