package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.Severity;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Settings of the notice shown in the Jahia UI: whether it is shown, from which severity, and whether
 * owner-reported findings count.
 */
public final class BannerSettings {

    static final Severity DEFAULT_MINIMUM_SEVERITY = Severity.HIGH;
    private static final int MAX_SHOWN_VALUE = 40;

    private final boolean enabled;
    private final Severity minimumSeverity;
    private final boolean includeOwnerReports;

    public BannerSettings(boolean enabled, Severity minimumSeverity, boolean includeOwnerReports) {
        this.enabled = enabled;
        this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        this.includeOwnerReports = includeOwnerReports;
    }

    public static BannerSettings defaults() {
        return new BannerSettings(true, DEFAULT_MINIMUM_SEVERITY, true);
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
            warnings.accept("Invalid bannerMinimumSeverity '" + shown(value) + "' (expected LOW, MEDIUM, HIGH or CRITICAL): using "
                    + DEFAULT_MINIMUM_SEVERITY);
            return DEFAULT_MINIMUM_SEVERITY;
        }
        return severity;
    }

    /** The configured value as it may appear in a log line: printable characters only, bounded. */
    private static String shown(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length() && sb.length() < MAX_SHOWN_VALUE; i++) {
            char c = value.charAt(i);
            sb.append(Character.isISOControl(c) ? '?' : c);
        }
        return sb.toString();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Severity getMinimumSeverity() {
        return minimumSeverity;
    }

    public boolean isIncludeOwnerReports() {
        return includeOwnerReports;
    }
}
