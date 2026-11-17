package org.jahia.community.securityupdates.service;

/**
 * Sends the security email through Jahia's mail service, to the address that receives Jahia's notifications.
 */
public interface MailSender {

    /** @return whether an email can be sent now, and to whom */
    MailStatus status();

    /**
     * @param message the email
     * @return true when Jahia's mail service accepted the email (its delivery is not known); false when it was not sent
     */
    boolean send(EmailMessage message);
}
