package org.jahia.community.securityupdates.service;

import org.jahia.services.mail.MailService;
import org.jahia.services.mail.MailSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JahiaMailSenderTest {

    private static final EmailMessage MESSAGE = new EmailMessage("Subject", "Text", "<p>Html</p>");

    private final MailService mail = mock(MailService.class);

    /** Jahia 8.2.3.7 and earlier: the mail server settings of Server settings > Mail server. */
    private void legacy(boolean enabled, String to) {
        when(mail.isEnabled()).thenReturn(enabled);
        when(mail.defaultRecipient()).thenReturn(to);
        when(mail.defaultSender()).thenReturn("jahia@example.org");
        when(mail.getSettings()).thenReturn(new MailSettings(enabled, "smtp://mail.example.org:25", "jahia@example.org", to,
                "Disabled"));
    }

    @Test
    @DisplayName("with the legacy mail server, the email goes to the administrator address, from the default sender")
    void legacySend() {
        legacy(true, " admin@example.org ");
        JahiaMailSender sender = new JahiaMailSender(() -> mail);

        assertThat(sender.status()).satisfies(s -> {
            assertThat(s.isReady()).isTrue();
            assertThat(s.getRecipient()).isEqualTo("admin@example.org");
            assertThat(s.isRecipientMissing()).isFalse();
        });
        assertThat(sender.send(MESSAGE)).isTrue();
        verify(mail).sendMessage("jahia@example.org", "admin@example.org", null, null, "Subject", "Text", "<p>Html</p>");
    }

    @Test
    @DisplayName("with the legacy mail server and no administrator address, nothing is sent")
    void legacyWithoutRecipient() {
        legacy(true, "  ");
        JahiaMailSender sender = new JahiaMailSender(() -> mail);

        assertThat(sender.status().isRecipientMissing()).isTrue();
        assertThat(sender.send(MESSAGE)).isFalse();
        verify(mail, never()).sendMessage(any(), any(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("with the mail-service module, no recipient is passed: the module sends to its default recipient")
    void mailServiceModule() {
        when(mail.isEnabled()).thenReturn(true);
        when(mail.defaultRecipient()).thenReturn(null);
        when(mail.defaultSender()).thenReturn(null);
        when(mail.getSettings()).thenReturn(new MailSettings());
        JahiaMailSender sender = new JahiaMailSender(() -> mail);

        assertThat(sender.status()).satisfies(s -> {
            assertThat(s.isReady()).isTrue();
            assertThat(s.getRecipient()).isNull();
            assertThat(s.isRecipientMissing()).isFalse();
        });
        assertThat(sender.send(MESSAGE)).isTrue();
        verify(mail).sendMessage(null, null, null, null, "Subject", "Text", "<p>Html</p>");
    }

    @Test
    @DisplayName("a mail service that is off or missing sends nothing")
    void notReady() {
        legacy(false, "admin@example.org");

        assertThat(new JahiaMailSender(() -> mail).send(MESSAGE)).isFalse();
        assertThat(new JahiaMailSender(() -> null).status().isReady()).isFalse();
        assertThat(new JahiaMailSender(() -> null).send(MESSAGE)).isFalse();
    }

    @Test
    @DisplayName("a failure of the mail service is reported as not sent")
    void sendFailure() {
        legacy(true, "admin@example.org");
        doThrow(new IllegalStateException("camel stopped")).when(mail)
                .sendMessage(any(), any(), any(), any(), anyString(), anyString(), anyString());

        assertThat(new JahiaMailSender(() -> mail).send(MESSAGE)).isFalse();
    }

    @Test
    @DisplayName("a mail service that does not answer within the limit is reported as not sent, without blocking the caller")
    void sendTimeout() {
        legacy(true, "admin@example.org");
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mail).sendMessage(any(), any(), any(), any(), anyString(), anyString(), anyString());
        JahiaMailSender sender = new JahiaMailSender(() -> mail, Duration.ofMillis(200));
        try {
            long start = System.nanoTime();

            assertThat(sender.send(MESSAGE)).isFalse();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        } finally {
            release.countDown();
            sender.deactivate();
        }
    }
}
