package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * What happened to a security updates email.
 */
@GraphQLName("SecurityUpdatesEmailOutcome")
@GraphQLDescription("What happened to a security updates email")
public enum EmailOutcome {
    /** Jahia's mail service accepted the email (delivery itself is not known). */
    SENT,
    /** The email is off in the settings. */
    DISABLED,
    /** This cluster node is not the processing server: that node sends the email. */
    NOT_PROCESSING_SERVER,
    /** The previous email is more recent than the frequency allows. */
    NOT_DUE,
    /** No finding reaches the minimum severity of the email. */
    NOTHING_TO_REPORT,
    /** Jahia's mail service is off or not configured. */
    MAIL_SERVER_NOT_CONFIGURED,
    /** Jahia's mail server settings have no administrator email address. */
    NO_RECIPIENT,
    /** A test email was sent less than a minute ago. */
    RATE_LIMITED,
    /** The mail service refused the email or failed; see the Jahia log. */
    FAILED
}
