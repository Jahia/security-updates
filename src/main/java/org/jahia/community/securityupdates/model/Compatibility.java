package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * Whether the recommended version of a component can be installed on the running Jahia.
 */
@GraphQLName("SecurityUpdatesCompatibility")
@GraphQLDescription("Whether the recommended version can be installed on the running Jahia")
public enum Compatibility {
    /** The store catalogue says the recommended version runs on this Jahia. */
    COMPATIBLE,
    /** The store catalogue says the recommended version needs another Jahia version. */
    INCOMPATIBLE,
    /** The recommended version is not in any store catalogue, so nothing can be said. */
    UNKNOWN,
    /** The finding concerns the Jahia platform itself: Jahia must be upgraded. */
    JAHIA_UPGRADE
}
