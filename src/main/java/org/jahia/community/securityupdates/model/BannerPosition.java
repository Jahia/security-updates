package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * Where the security updates notice appears in the Jahia UI.
 */
@GraphQLName("SecurityUpdatesBannerPosition")
@GraphQLDescription("Where the security updates notice appears in the Jahia UI")
public enum BannerPosition {
    /** A full-width bar at the top of the window (the default). */
    TOP,
    /** A full-width bar at the bottom of the window. */
    BOTTOM,
    /** A small box in the top right corner of the window. */
    TOP_RIGHT,
    /** A small box in the bottom right corner of the window. */
    BOTTOM_RIGHT
}
