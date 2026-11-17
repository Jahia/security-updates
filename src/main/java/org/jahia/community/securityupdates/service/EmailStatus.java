package org.jahia.community.securityupdates.service;

import java.time.Instant;

/**
 * What the settings page shows about the security email.
 */
public final class EmailStatus {

    private final boolean mailServerReady;
    private final String recipient;
    private final boolean recipientMissing;
    private final boolean processingServer;
    private final Instant lastSentAt;

    /**
     * @param mailServerReady  whether Jahia's mail service can send
     * @param recipient        the administrator email address, null when the mail service chooses it
     * @param recipientMissing true when the mail server settings have no administrator email address
     * @param processingServer whether this cluster node sends the scheduled emails
     * @param lastSentAt       the last scheduled email sent from this node, null if none
     */
    public EmailStatus(boolean mailServerReady, String recipient, boolean recipientMissing, boolean processingServer,
                       Instant lastSentAt) {
        this.mailServerReady = mailServerReady;
        this.recipient = recipient;
        this.recipientMissing = recipientMissing;
        this.processingServer = processingServer;
        this.lastSentAt = lastSentAt;
    }

    public boolean isMailServerReady() {
        return mailServerReady;
    }

    public String getRecipient() {
        return recipient;
    }

    public boolean isRecipientMissing() {
        return recipientMissing;
    }

    public boolean isProcessingServer() {
        return processingServer;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }
}
