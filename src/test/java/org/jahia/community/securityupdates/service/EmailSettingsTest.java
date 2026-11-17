package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.EmailFrequency;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSettingsTest {

    @Test
    @DisplayName("by default the email is off, weekly, from HIGH")
    void defaults() {
        EmailSettings email = Settings.defaults().getEmail();

        assertThat(email.isEnabled()).isFalse();
        assertThat(email.getFrequency()).isEqualTo(EmailFrequency.WEEKLY);
        assertThat(email.getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(Settings.defaults().toValues())
                .containsEntry(SettingsKeys.EMAIL_ENABLED, "false")
                .containsEntry(SettingsKeys.EMAIL_FREQUENCY, "WEEKLY")
                .containsEntry(SettingsKeys.EMAIL_MINIMUM_SEVERITY, "HIGH");
    }

    @Test
    @DisplayName("the email keys come last in the .cfg order")
    void keysOrder() {
        List<String> all = SettingsKeys.ALL;

        assertThat(all.subList(all.size() - 3, all.size())).containsExactly(SettingsKeys.EMAIL_ENABLED,
                SettingsKeys.EMAIL_FREQUENCY, SettingsKeys.EMAIL_MINIMUM_SEVERITY);
        assertThat(Settings.defaults().toValues()).containsOnlyKeys(all);
    }

    @Test
    @DisplayName("stored values are read in any case")
    void readsValues() {
        List<String> warnings = new ArrayList<>();

        EmailSettings email = Settings.fromProperties(Map.of(SettingsKeys.EMAIL_ENABLED, "TRUE",
                SettingsKeys.EMAIL_FREQUENCY, " daily ", SettingsKeys.EMAIL_MINIMUM_SEVERITY, "critical"), warnings::add).getEmail();

        assertThat(warnings).isEmpty();
        assertThat(email.isEnabled()).isTrue();
        assertThat(email.getFrequency()).isEqualTo(EmailFrequency.DAILY);
        assertThat(email.getMinimumSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @ParameterizedTest(name = "{0}=[{1}] -> default with a warning")
    @CsvSource({
            "emailEnabled, yes",
            "emailFrequency, HOURLY",
            "emailMinimumSeverity, UNKNOWN",
            "emailMinimumSeverity, SEVERE"
    })
    void invalidValueTakesItsDefault(String key, String value) {
        List<String> warnings = new ArrayList<>();

        Settings settings = Settings.fromProperties(Map.of(key, value), warnings::add);

        assertThat(settings.getEmail().isEnabled()).isFalse();
        assertThat(settings.getEmail().getFrequency()).isEqualTo(EmailFrequency.WEEKLY);
        assertThat(settings.getEmail().getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(warnings).singleElement().satisfies(w -> assertThat(w).contains(key).contains(value));
    }

    @Test
    @DisplayName("a change of the email settings does not restart the schedule")
    void emailIsNotPartOfTheSchedule() {
        Settings on = Settings.fromProperties(Map.of(SettingsKeys.EMAIL_ENABLED, "true",
                SettingsKeys.EMAIL_FREQUENCY, "MONTHLY"), w -> { });

        assertThat(on.sameSchedule(Settings.defaults())).isTrue();
    }

    @Test
    @DisplayName("each frequency has its period")
    void periods() {
        assertThat(EmailFrequency.DAILY.getPeriod()).isEqualTo(Duration.ofDays(1));
        assertThat(EmailFrequency.WEEKLY.getPeriod()).isEqualTo(Duration.ofDays(7));
        assertThat(EmailFrequency.MONTHLY.getPeriod()).isEqualTo(Duration.ofDays(30));
    }
}
