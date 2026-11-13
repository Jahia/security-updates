package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * How long a dismissed notice stays hidden. Except for {@link #NEVER}, it is shown again as soon as a new advisory
 * or a new component is counted (a dismissal key not dismissed before); it stays hidden when advisories or
 * components only disappear.
 */
@GraphQLName("SecurityUpdatesDismissPolicy")
@GraphQLDescription("How long a dismissed security updates notice stays hidden")
public enum DismissPolicy {
    /** Until the browser session ends (sessionStorage). */
    SESSION,
    /** For a number of hours (localStorage, per Jahia user). */
    HOURS,
    /**
     * Shown again when a new advisory or a new component is counted; stays hidden when advisories only disappear
     * (localStorage, per Jahia user).
     */
    UNTIL_CHANGE,
    /** The notice cannot be dismissed. */
    NEVER
}
