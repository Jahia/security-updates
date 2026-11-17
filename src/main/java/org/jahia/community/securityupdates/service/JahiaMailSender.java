package org.jahia.community.securityupdates.service;

import org.jahia.registries.ServicesRegistry;
import org.jahia.services.mail.MailService;
import org.jahia.services.mail.MailSettings;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Sends the security email through Jahia's mail service ({@code ServicesRegistry.getMailService()}), the service
 * that sends Jahia's own notifications.
 *
 * <ul>
 *     <li>Jahia 8.2.3.7 and earlier: the mail server of Server settings &gt; Mail server; the email goes to its
 *     administrator email address ({@code defaultRecipient()}), from its sender address. Without an administrator
 *     address nothing is sent.</li>
 *     <li>Later Jahia versions, where the mail-service module sends the mail: {@code defaultRecipient()} is null, so
 *     the email is sent without a recipient and the module sends it to its default recipient.</li>
 * </ul>
 * The legacy service hands the email to a queue: "sent" means accepted, not delivered.
 */
@Component(service = MailSender.class)
public class JahiaMailSender implements MailSender {

    private static final Logger logger = LoggerFactory.getLogger(JahiaMailSender.class);

    private final Supplier<MailService> mailService;

    /** Declarative Services constructor: Jahia's mail service. */
    public JahiaMailSender() {
        this(() -> ServicesRegistry.getInstance().getMailService());
    }

    /** Test constructor. */
    JahiaMailSender(Supplier<MailService> mailService) {
        this.mailService = mailService;
    }

    @Override
    public MailStatus status() {
        MailService mail = mailService.get();
        return mail == null ? MailStatus.notReady() : status(mail);
    }

    private static MailStatus status(MailService mail) {
        if (!mail.isEnabled()) {
            return MailStatus.notReady();
        }
        String recipient = trimToNull(mail.defaultRecipient());
        MailSettings settings = mail.getSettings();
        // the legacy mail server has a server URI; with the mail-service module the legacy settings are empty
        boolean legacy = settings != null && trimToNull(settings.getUri()) != null;
        return new MailStatus(true, recipient, legacy && recipient == null);
    }

    @Override
    public boolean send(EmailMessage message) {
        MailService mail = mailService.get();
        if (mail == null) {
            return false;
        }
        MailStatus status = status(mail);
        if (!status.isReady() || status.isRecipientMissing()) {
            return false;
        }
        try {
            mail.sendMessage(trimToNull(mail.defaultSender()), status.getRecipient(), null, null, message.getSubject(),
                    message.getText(), message.getHtml());
            return true;
        } catch (RuntimeException e) {
            logger.warn("Jahia's mail service could not send the security email", e);
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
