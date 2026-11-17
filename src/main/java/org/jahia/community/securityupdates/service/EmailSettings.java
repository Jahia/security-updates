package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.EmailFrequency;
import org.jahia.community.securityupdates.model.Severity;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Settings of the security updates email: whether it is sent, how often, and from which severity.
 */
public final class EmailSettings {

    static final EmailFrequency DEFAULT_FREQUENCY = EmailFrequency.WEEKLY;
    static final Severity DEFAULT_MINIMUM_SEVERITY = Severity.HIGH;

    private final boolean enabled;
    private final EmailFrequency frequency;
    private final Severity minimumSeverity;

    /**
     * @param enabled         whether the email is sent
     * @param frequency       how often it may be sent
     * @param minimumSeverity lowest severity of a finding that the email lists (not UNKNOWN)
     */
    public EmailSettings(boolean enabled, EmailFrequency frequency, Severity minimumSeverity) {
        this.enabled = enabled;
        this.frequency = Objects.requireNonNull(frequency, "frequency");
        this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
    }

    /** @return the email off, weekly, from HIGH */
    public static EmailSettings defaults() {
        return new EmailSettings(false, DEFAULT_FREQUENCY, DEFAULT_MINIMUM_SEVERITY);
    }

    /**
     * @param value    the configured value: DAILY, WEEKLY or MONTHLY, in any case
     * @param warnings receives one message when the value is not one of those
     * @return the frequency, or {@link #DEFAULT_FREQUENCY} when the value is absent or invalid
     */
    static EmailFrequency parseFrequency(String value, Consumer<String> warnings) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_FREQUENCY;
        }
        try {
            return EmailFrequency.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnings.accept("Invalid " + SettingsKeys.EMAIL_FREQUENCY + " '" + Settings.shown(value)
                    + "' (expected DAILY, WEEKLY or MONTHLY): using " + DEFAULT_FREQUENCY);
            return DEFAULT_FREQUENCY;
        }
    }

    /**
     * @param value    the configured value: LOW, MEDIUM, HIGH or CRITICAL, in any case
     * @param warnings receives one message when the value is not one of those
     * @return the severity, or {@link #DEFAULT_MINIMUM_SEVERITY} when the value is absent or invalid
     */
    static Severity parseMinimumSeverity(String value, Consumer<String> warnings) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_MINIMUM_SEVERITY;
        }
        Severity severity = Severity.parse(value);
        if (severity == Severity.UNKNOWN) {
            warnings.accept("Invalid " + SettingsKeys.EMAIL_MINIMUM_SEVERITY + " '" + Settings.shown(value)
                    + "' (expected LOW, MEDIUM, HIGH or CRITICAL): using " + DEFAULT_MINIMUM_SEVERITY);
            return DEFAULT_MINIMUM_SEVERITY;
        }
        return severity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public EmailFrequency getFrequency() {
        return frequency;
    }

    public Severity getMinimumSeverity() {
        return minimumSeverity;
    }
}
