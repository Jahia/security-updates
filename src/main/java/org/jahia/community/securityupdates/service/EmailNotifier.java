package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.EmailOutcome;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Decides when the security email is sent, and sends it.
 *
 * <ul>
 *     <li>After each check that published its report ({@link #afterCheck}): only when the email is on, on the
 *     processing server of a cluster (each node checks on its own), when at least one finding reaches the email's
 *     minimum severity, and when the previous email is older than the frequency less {@link #DUE_MARGIN} (so that a
 *     check at the same time of day is not one interval late). A check with nothing to report sends nothing and keeps
 *     the email due: a later finding is sent at once.</li>
 *     <li>A test email ({@link #sendTest}) is sent on demand, whatever the settings and the node, with the current
 *     report, at most once every {@link #TEST_MIN_INTERVAL}; it does not count as the scheduled email.</li>
 *     <li>The time of the last scheduled email is kept by {@link EmailState}, across restarts.</li>
 *     <li>A failure is logged once until the outcome changes; nothing here throws.</li>
 * </ul>
 */
final class EmailNotifier {

    static final Duration DUE_MARGIN = Duration.ofHours(1);
    static final Duration TEST_MIN_INTERVAL = Duration.ofSeconds(60);

    private static final Logger logger = LoggerFactory.getLogger(EmailNotifier.class);

    private final Clock clock;
    private final MailSender sender;
    private final EmailState state;
    private final BooleanSupplier processingServer;
    private final Supplier<String> serverName;
    private final Object testLock = new Object();
    private Instant lastTestAt;
    private volatile EmailOutcome lastLogged;
    private volatile String cachedServerName;

    EmailNotifier(Clock clock, MailSender sender, EmailState state, BooleanSupplier processingServer,
                  Supplier<String> serverName) {
        this.clock = clock;
        this.sender = sender;
        this.state = state;
        this.processingServer = processingServer;
        this.serverName = serverName;
    }

    /**
     * Sends the scheduled email when it is due. Called after a check published {@code report}.
     *
     * @return what happened
     */
    synchronized EmailOutcome afterCheck(SecurityReport report, EmailSettings settings) {
        EmailOutcome outcome;
        try {
            outcome = sendIfDue(report, settings);
        } catch (RuntimeException e) {
            logger.warn("The security updates email failed", e);
            outcome = EmailOutcome.FAILED;
        }
        logOutcome(outcome);
        return outcome;
    }

    private EmailOutcome sendIfDue(SecurityReport report, EmailSettings settings) {
        if (!settings.isEnabled()) {
            return EmailOutcome.DISABLED;
        }
        if (!isProcessingServer()) {
            return EmailOutcome.NOT_PROCESSING_SERVER;
        }
        Instant now = clock.instant();
        // a time in the future (the clock was set back) would hold the email back until then: ignored
        Optional<Instant> last = state.lastSentAt().filter(at -> !at.isAfter(now));
        if (last.isPresent() && now.isBefore(last.get().plus(settings.getFrequency().getPeriod()).minus(DUE_MARGIN))) {
            return EmailOutcome.NOT_DUE;
        }
        List<Finding> findings = EmailComposer.selected(report, settings.getMinimumSeverity());
        if (findings.isEmpty()) {
            return EmailOutcome.NOTHING_TO_REPORT;
        }
        EmailOutcome outcome = send(EmailComposer.periodic(report, findings, settings, serverName()));
        if (outcome == EmailOutcome.SENT) {
            state.recordSent(now);
            logger.info("Security updates email sent ({} finding(s))", findings.size());
        }
        return outcome;
    }

    /**
     * Sends a test email with the current report, unless one was sent less than {@link #TEST_MIN_INTERVAL} ago.
     *
     * @return what happened
     */
    EmailOutcome sendTest(SecurityReport report, EmailSettings settings) {
        synchronized (testLock) {
            Instant now = clock.instant();
            if (lastTestAt != null && now.isBefore(lastTestAt.plus(TEST_MIN_INTERVAL))) {
                return EmailOutcome.RATE_LIMITED;
            }
            EmailOutcome outcome;
            try {
                List<Finding> findings = EmailComposer.selected(report, settings.getMinimumSeverity());
                outcome = send(EmailComposer.test(report, findings, settings, serverName()));
            } catch (RuntimeException e) {
                logger.warn("The security updates test email failed", e);
                outcome = EmailOutcome.FAILED;
            }
            if (outcome == EmailOutcome.SENT || outcome == EmailOutcome.FAILED) {
                lastTestAt = now;
            }
            return outcome;
        }
    }

    /** @return what the settings page shows; never throws */
    EmailStatus status() {
        MailStatus mail = mailStatus();
        Instant last;
        try {
            last = state.lastSentAt().orElse(null);
        } catch (RuntimeException e) {
            last = null;
        }
        return new EmailStatus(mail.isReady(), mail.getRecipient(), mail.isRecipientMissing(), isProcessingServer(), last);
    }

    private EmailOutcome send(EmailMessage message) {
        MailStatus mail = mailStatus();
        if (!mail.isReady()) {
            return EmailOutcome.MAIL_SERVER_NOT_CONFIGURED;
        }
        if (mail.isRecipientMissing()) {
            return EmailOutcome.NO_RECIPIENT;
        }
        try {
            return sender.send(message) ? EmailOutcome.SENT : EmailOutcome.FAILED;
        } catch (RuntimeException e) {
            logger.warn("Jahia's mail service could not send the security updates email", e);
            return EmailOutcome.FAILED;
        }
    }

    private MailStatus mailStatus() {
        try {
            MailStatus status = sender.status();
            return status == null ? MailStatus.notReady() : status;
        } catch (RuntimeException e) {
            logger.debug("Could not read the state of Jahia's mail service", e);
            return MailStatus.notReady();
        }
    }

    private boolean isProcessingServer() {
        try {
            return processingServer.getAsBoolean();
        } catch (RuntimeException e) {
            logger.debug("Could not tell whether this node is the processing server", e);
            return false;
        }
    }

    private String serverName() {
        String name = cachedServerName;
        if (name == null) {
            try {
                name = serverName.get();
            } catch (RuntimeException e) {
                name = null;
            }
            name = EmailComposer.safeServerName(name);
            cachedServerName = name;
        }
        return name;
    }

    /** One WARN per outcome that keeps a due email from going out, until the outcome changes. */
    private void logOutcome(EmailOutcome outcome) {
        boolean problem = outcome == EmailOutcome.MAIL_SERVER_NOT_CONFIGURED || outcome == EmailOutcome.NO_RECIPIENT
                || outcome == EmailOutcome.FAILED;
        if (problem && outcome != lastLogged && logger.isWarnEnabled()) {
            logger.warn("The security updates email is due but was not sent: {}", describe(outcome));
        }
        if (outcome == EmailOutcome.SENT || problem) {
            lastLogged = outcome;
        }
    }

    private static String describe(EmailOutcome outcome) {
        switch (outcome) {
            case MAIL_SERVER_NOT_CONFIGURED:
                return "Jahia's mail server is off or not configured (Server settings > Mail server)";
            case NO_RECIPIENT:
                return "Jahia's mail server settings have no administrator email address";
            default:
                return "Jahia's mail service refused or failed to send it; see the Jahia log";
        }
    }
}
