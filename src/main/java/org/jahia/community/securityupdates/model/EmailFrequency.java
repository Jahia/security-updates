package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

import java.time.Duration;

/**
 * How often the security updates email may be sent.
 */
@GraphQLName("SecurityUpdatesEmailFrequency")
@GraphQLDescription("How often the security updates email may be sent")
public enum EmailFrequency {
    /** At most once a day. */
    DAILY(Duration.ofDays(1)),
    /** At most once a week (the default). */
    WEEKLY(Duration.ofDays(7)),
    /** At most once every 30 days. */
    MONTHLY(Duration.ofDays(30));

    private final Duration period;

    EmailFrequency(Duration period) {
        this.period = period;
    }

    /** @return the shortest time between two emails */
    public Duration getPeriod() {
        return period;
    }
}
