package org.jahia.community.securityupdates.service;

import org.jahia.registries.ServicesRegistry;
import org.jahia.services.mail.MailService;
import org.jahia.services.mail.MailSettings;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * The legacy service hands the email to a queue: "sent" means accepted, not delivered. The mail-service module may
 * send it before it returns, over SMTP: the call runs on the daemon thread {@code security-updates-mail} and the
 * caller (the check thread) waits for it at most {@link #SEND_TIMEOUT}. A call still running then is reported as
 * not sent (it may still go out later) and the check goes on.
 */
@Component(service = MailSender.class)
public class JahiaMailSender implements MailSender {

    private static final Logger logger = LoggerFactory.getLogger(JahiaMailSender.class);

    /** Longest wait for Jahia's mail service to take the email. */
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(60);

    private final Supplier<MailService> mailService;
    private final Duration sendTimeout;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "security-updates-mail");
        thread.setDaemon(true);
        return thread;
    });

    /** Declarative Services constructor: Jahia's mail service. */
    public JahiaMailSender() {
        this(() -> ServicesRegistry.getInstance().getMailService());
    }

    /** Test constructor. */
    JahiaMailSender(Supplier<MailService> mailService) {
        this(mailService, SEND_TIMEOUT);
    }

    /** Test constructor. */
    JahiaMailSender(Supplier<MailService> mailService, Duration sendTimeout) {
        this.mailService = mailService;
        this.sendTimeout = sendTimeout;
    }

    /** Stops the send thread, interrupting a send still running. */
    @Deactivate
    void deactivate() {
        sendExecutor.shutdownNow();
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
        Future<?> sending;
        try {
            sending = sendExecutor.submit(() -> mail.sendMessage(trimToNull(mail.defaultSender()), status.getRecipient(), null,
                    null, message.getSubject(), message.getText(), message.getHtml()));
        } catch (RejectedExecutionException e) {
            logger.warn("The security email was not sent: the module is stopping");
            return false;
        }
        try {
            sending.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            logger.warn("Jahia's mail service did not take the security email within {} seconds; it may still be sent",
                    sendTimeout.getSeconds());
            return false;
        } catch (ExecutionException e) {
            logger.warn("Jahia's mail service could not send the security email", e.getCause());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
