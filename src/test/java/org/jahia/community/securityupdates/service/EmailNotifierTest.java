package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.EmailFrequency;
import org.jahia.community.securityupdates.model.EmailOutcome;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNotifierTest {

    private static final EmailSettings WEEKLY = new EmailSettings(true, EmailFrequency.WEEKLY, Severity.HIGH);
    private static final EmailSettings OFF = new EmailSettings(false, EmailFrequency.WEEKLY, Severity.HIGH);
    private static final Finding HIGH = EmailComposerTest.finding("graphql-dxm-provider", Severity.HIGH, FindingSource.ADVISORY);
    private static final Finding LOW = EmailComposerTest.finding("jcontent", Severity.LOW, FindingSource.ADVISORY);

    private final ServiceFixture.MutableClock clock = new ServiceFixture.MutableClock(Instant.parse("2026-11-13T10:00:00Z"));
    private final FakeSender sender = new FakeSender();
    private final MemoryState state = new MemoryState();
    private final AtomicBoolean processing = new AtomicBoolean(true);
    private final EmailNotifier notifier = new EmailNotifier(clock, sender, state, processing::get, () -> "jahia-prod");

    private static SecurityReport withFindings(Finding... findings) {
        return EmailComposerTest.report(ReportStatus.OK, findings);
    }

    @Test
    @DisplayName("the first check with a finding of at least the minimum severity sends the email and records the time")
    void firstEmail() {
        assertThat(notifier.afterCheck(withFindings(HIGH, LOW), WEEKLY)).isEqualTo(EmailOutcome.SENT);

        assertThat(sender.sent).singleElement().satisfies(m -> {
            assertThat(m.getSubject()).contains("1 component needs");
            assertThat(m.getText()).contains("graphql-dxm-provider").doesNotContain("jcontent");
        });
        assertThat(state.lastSentAt()).contains(clock.instant());
    }

    @Test
    @DisplayName("nothing is sent when the email is off, on another cluster node, or with nothing to report")
    void notSent() {
        assertThat(notifier.afterCheck(withFindings(HIGH), OFF)).isEqualTo(EmailOutcome.DISABLED);
        assertThat(notifier.afterCheck(withFindings(LOW), WEEKLY)).isEqualTo(EmailOutcome.NOTHING_TO_REPORT);
        processing.set(false);
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.NOT_PROCESSING_SERVER);

        assertThat(sender.sent).isEmpty();
        assertThat(state.lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("the next email waits for the period, less one hour so that a check at the same time of day sends it")
    void period() {
        notifier.afterCheck(withFindings(HIGH), WEEKLY);

        clock.advance(Duration.ofDays(7).minusHours(1).minusSeconds(1));
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.NOT_DUE);
        clock.advance(Duration.ofSeconds(1));
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.SENT);

        assertThat(sender.sent).hasSize(2);
    }

    @Test
    @DisplayName("a check with nothing to report does not delay the email: a finding found later is sent at once")
    void nothingToReportKeepsTheEmailDue() {
        notifier.afterCheck(withFindings(HIGH), WEEKLY);
        clock.advance(Duration.ofDays(8));
        assertThat(notifier.afterCheck(withFindings(), WEEKLY)).isEqualTo(EmailOutcome.NOTHING_TO_REPORT);
        clock.advance(Duration.ofDays(1));

        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.SENT);
    }

    @Test
    @DisplayName("without a ready mail server or a recipient nothing is sent, and the email stays due")
    void mailServerNotReady() {
        sender.status = new MailStatus(false, null, false);
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.MAIL_SERVER_NOT_CONFIGURED);

        sender.status = new MailStatus(true, null, true);
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.NO_RECIPIENT);

        assertThat(sender.sent).isEmpty();
        assertThat(state.lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("a refused or failing send is FAILED and records nothing")
    void sendFails() {
        sender.accept = false;
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.FAILED);

        sender.accept = true;
        sender.failure.set(new IllegalStateException("smtp down"));
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.FAILED);

        assertThat(state.lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("a test email is sent even when the email is off, on any node and with nothing to report; it records nothing")
    void testEmail() {
        processing.set(false);

        assertThat(notifier.sendTest(withFindings(), OFF)).isEqualTo(EmailOutcome.SENT);

        assertThat(sender.sent).singleElement().satisfies(m -> assertThat(m.getSubject()).startsWith("Test email"));
        assertThat(state.lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("test emails are limited to one a minute; a refused attempt is not counted")
    void testEmailRateLimit() {
        sender.status = new MailStatus(false, null, false);
        assertThat(notifier.sendTest(withFindings(), WEEKLY)).isEqualTo(EmailOutcome.MAIL_SERVER_NOT_CONFIGURED);
        sender.status = new MailStatus(true, "admin@example.org", false);

        assertThat(notifier.sendTest(withFindings(), WEEKLY)).isEqualTo(EmailOutcome.SENT);
        clock.advance(Duration.ofSeconds(59));
        assertThat(notifier.sendTest(withFindings(), WEEKLY)).isEqualTo(EmailOutcome.RATE_LIMITED);
        clock.advance(Duration.ofSeconds(1));
        assertThat(notifier.sendTest(withFindings(), WEEKLY)).isEqualTo(EmailOutcome.SENT);
    }

    @Test
    @DisplayName("the status gives the mail server state, the recipient, the node role and the last email")
    void status() {
        notifier.afterCheck(withFindings(HIGH), WEEKLY);

        EmailStatus status = notifier.status();

        assertThat(status.isMailServerReady()).isTrue();
        assertThat(status.getRecipient()).isEqualTo("admin@example.org");
        assertThat(status.isRecipientMissing()).isFalse();
        assertThat(status.isProcessingServer()).isTrue();
        assertThat(status.getLastSentAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("a mail sender that throws on status reads as not ready")
    void statusFailure() {
        sender.statusFailure = true;

        assertThat(notifier.status().isMailServerReady()).isFalse();
        assertThat(notifier.afterCheck(withFindings(HIGH), WEEKLY)).isEqualTo(EmailOutcome.MAIL_SERVER_NOT_CONFIGURED);
    }

    static final class FakeSender implements MailSender {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        volatile MailStatus status = new MailStatus(true, "admin@example.org", false);
        volatile boolean accept = true;
        volatile boolean statusFailure;
        /** Runs at the start of each send, as the mail service would be called. */
        volatile Runnable onSend;

        @Override
        public MailStatus status() {
            if (statusFailure) {
                throw new IllegalStateException("mail service gone");
            }
            return status;
        }

        @Override
        public boolean send(EmailMessage message) {
            Runnable hook = onSend;
            if (hook != null) {
                hook.run();
            }
            RuntimeException e = failure.get();
            if (e != null) {
                throw e;
            }
            if (accept) {
                sent.add(message);
            }
            return accept;
        }
    }

    static final class MemoryState implements EmailState {
        private final AtomicReference<Instant> last = new AtomicReference<>();

        @Override
        public Optional<Instant> lastSentAt() {
            return Optional.ofNullable(last.get());
        }

        @Override
        public void recordSent(Instant at) {
            last.set(at);
        }
    }

    @Test
    @DisplayName("a last email dated in the future (clock set back) does not hold the email back")
    void lastSentInTheFuture() {
        state.recordSent(clock.instant().plus(Duration.ofDays(20)));

        assertThat(notifier.afterCheck(withFindings(HIGH), new EmailSettings(true, EmailFrequency.MONTHLY, Severity.HIGH)))
                .isEqualTo(EmailOutcome.SENT);
    }
}
