package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.EmailOutcome;
import org.jahia.community.securityupdates.model.ReportView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jahia.community.securityupdates.service.ServiceFixture.DEFAULT_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.EMPTY_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.FEED_WITH_FINDING;

/**
 * The security email of {@link SecurityUpdateService}: sent after a check that published its report.
 */
@DisplayName("SecurityUpdateService email")
class SecurityUpdateServiceEmailTest {

    private static final Settings EMAIL_ON = Settings.fromProperties(Map.of(SettingsKeys.EMAIL_ENABLED, "true"), w -> { });

    private ServiceFixture fixture;
    private SecurityUpdateService service;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        service = fixture.service();
    }

    @Test
    @DisplayName("a check that finds an update sends the email once, then not again before the period")
    void sentAfterACheck() {
        service.apply(EMAIL_ON);
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);

        fixture.check(service);
        fixture.clock.advance(Duration.ofDays(1));
        fixture.check(service);

        assertThat(fixture.mail.sent).singleElement().satisfies(m -> {
            assertThat(m.getSubject()).isEqualTo("Jahia on jahia-test: 1 component needs a security update");
            assertThat(m.getText()).contains("graphql-dxm-provider");
        });
        assertThat(service.getEmailStatus().getLastSentAt()).isEqualTo(fixture.clock.instant().minus(Duration.ofDays(1)));
    }

    @Test
    @DisplayName("the check ends after its email: the report is published, and the check still in progress, while it is sent")
    void emailIsPartOfTheCheck() {
        service.apply(EMAIL_ON);
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        AtomicReference<ReportView> duringSend = new AtomicReference<>();
        fixture.mail.onSend = () -> duringSend.set(service.getReportView());

        fixture.check(service);

        assertThat(duringSend.get()).isNotNull();
        assertThat(duringSend.get().isInProgress()).isTrue();
        assertThat(duringSend.get().getReport().getFindings()).isNotEmpty();
        assertThat(service.getReportView().isInProgress()).isFalse();
    }

    @Test
    @DisplayName("an Error thrown while the email is sent still ends the check")
    void errorDuringEmailEndsTheCheck() {
        service.apply(EMAIL_ON);
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        fixture.mail.onSend = () -> {
            throw new AssertionError("mail service broken");
        };

        fixture.check(service);

        assertThat(service.getReportView().isInProgress()).isFalse();
        assertThat(service.getReport().getFindings()).isNotEmpty();
    }

    @Test
    @DisplayName("nothing is sent with the default settings (email off) or when no update is found")
    void notSent() {
        service.apply(Settings.defaults());
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        fixture.check(service);

        service.apply(EMAIL_ON);
        fixture.storeServes(DEFAULT_STORE, EMPTY_FEED);
        fixture.check(service);

        assertThat(fixture.mail.sent).isEmpty();
    }

    @Test
    @DisplayName("a check whose report is not published (settings changed while it ran) sends nothing")
    void unpublishedCheck() {
        service.apply(EMAIL_ON);
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        fixture.fetcher.onGet(() -> service.apply(Settings.fromProperties(Map.of(SettingsKeys.EMAIL_ENABLED, "true",
                SettingsKeys.REFRESH_INTERVAL_HOURS, "6"), w -> { })));

        fixture.check(service);

        assertThat(fixture.mail.sent).isEmpty();
    }

    @Test
    @DisplayName("a test email uses the current report and settings")
    void testEmail() {
        service.apply(Settings.defaults());
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        fixture.check(service);

        assertThat(service.sendTestEmail()).isEqualTo(EmailOutcome.SENT);

        assertThat(fixture.mail.sent).singleElement().satisfies(m -> {
            assertThat(m.getSubject()).startsWith("Test email");
            assertThat(m.getText()).contains("graphql-dxm-provider").contains("The security updates email is off");
        });
        assertThat(fixture.emailState.lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("without the mail sender (not bound yet) no email is sent and the status says the mail server is not ready")
    void withoutMailSender() {
        SecurityUpdateService bare = new SecurityUpdateService(fixture.clock, () -> ServiceFixture.JAHIA, () -> fixture.executor,
                f -> { });

        assertThat(bare.sendTestEmail()).isEqualTo(EmailOutcome.FAILED);
        assertThat(bare.getEmailStatus().isMailServerReady()).isFalse();
    }
}
