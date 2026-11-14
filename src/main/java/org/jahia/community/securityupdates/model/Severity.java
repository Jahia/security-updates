package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.Locale;

/**
 * Severity of a security issue, ordered from the least to the most severe.
 * {@link #UNKNOWN} is used when the feed gives no severity or one this module does not know.
 */
@GraphQLName("SecurityUpdatesSeverity")
@GraphQLDescription("Severity of a security issue")
public enum Severity {
    UNKNOWN, LOW, MEDIUM, HIGH, CRITICAL;

    /**
     * @param value the severity as written in the feed, in any case; may be null
     * @return the matching severity, or {@link #UNKNOWN} when the value is absent or not recognised
     */
    public static Severity parse(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * @return the more severe of this severity and {@code other}
     */
    public Severity max(Severity other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }

    /**
     * @return true for {@link #HIGH} and {@link #CRITICAL}
     */
    public boolean isHighOrCritical() {
        return this == HIGH || this == CRITICAL;
    }
}
