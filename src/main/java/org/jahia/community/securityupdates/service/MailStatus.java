package org.jahia.community.securityupdates.service;

/**
 * Whether Jahia's mail service can send the security email, and to whom.
 */
public final class MailStatus {

    private static final MailStatus NOT_READY = new MailStatus(false, null, false);

    private final boolean ready;
    private final String recipient;
    private final boolean recipientMissing;

    /**
     * @param ready            whether Jahia's mail service is on and configured
     * @param recipient        the administrator email address, or null when the mail service chooses it (the
     *                         mail-service module sends to its default recipient)
     * @param recipientMissing true when the mail server settings have no administrator email address, so that the
     *                         email would go nowhere
     */
    public MailStatus(boolean ready, String recipient, boolean recipientMissing) {
        this.ready = ready;
        this.recipient = recipient;
        this.recipientMissing = recipientMissing;
    }

    /** @return a mail service that cannot send */
    public static MailStatus notReady() {
        return NOT_READY;
    }

    public boolean isReady() {
        return ready;
    }

    public String getRecipient() {
        return recipient;
    }

    public boolean isRecipientMissing() {
        return recipientMissing;
    }
}
