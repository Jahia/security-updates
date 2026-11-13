package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * How long a dismissed notice stays hidden. Except for {@link #NEVER}, it comes back as soon as the set of
 * counted components changes.
 */
@GraphQLName("SecurityUpdatesDismissPolicy")
@GraphQLDescription("How long a dismissed security updates notice stays hidden")
public enum DismissPolicy {
    /** Until the browser session ends (sessionStorage). */
    SESSION,
    /** For a number of hours (localStorage, per Jahia user). */
    HOURS,
    /** Until the counted components change (localStorage, per Jahia user). */
    UNTIL_CHANGE,
    /** The notice cannot be dismissed. */
    NEVER
}
