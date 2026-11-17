package org.jahia.community.securityupdates.service;

import java.time.Instant;
import java.util.Optional;

/**
 * When the last security email was sent, kept across restarts.
 */
public interface EmailState {

    /** @return the time of the last email sent by the schedule, if any */
    Optional<Instant> lastSentAt();

    /** Records the time of an email sent by the schedule. Never throws. */
    void recordSent(Instant at);
}
