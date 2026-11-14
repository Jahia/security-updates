package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Settings of the notice shown in the Jahia UI: whether it is shown, from which severity, whether owner-reported
 * findings count, its colours and how long a dismissal lasts.
 */
public final class BannerSettings {

    static final Severity DEFAULT_MINIMUM_SEVERITY = BannerDecision.DEFAULT_MINIMUM_SEVERITY;
    static final String DEFAULT_BACKGROUND_COLOR = BannerDecision.DEFAULT_BACKGROUND_COLOR;
    static final String DEFAULT_TEXT_COLOR = BannerDecision.DEFAULT_TEXT_COLOR;
    static final DismissPolicy DEFAULT_DISMISS_POLICY = BannerDecision.DEFAULT_DISMISS_POLICY;
    static final int DEFAULT_DISMISS_HOURS = BannerDecision.DEFAULT_DISMISS_HOURS;

    private final boolean enabled;
    private final Severity minimumSeverity;
    private final boolean includeOwnerReports;
    private final String backgroundColor;
    private final String textColor;
    private final DismissPolicy dismissPolicy;
    private final int dismissHours;

    public BannerSettings(boolean enabled, Severity minimumSeverity, boolean includeOwnerReports) {
        this(enabled, minimumSeverity, includeOwnerReports, DEFAULT_BACKGROUND_COLOR, DEFAULT_TEXT_COLOR,
                DEFAULT_DISMISS_POLICY, DEFAULT_DISMISS_HOURS);
    }

    /**
     * @param backgroundColor validated {@code #RRGGBB} background (see {@link #parseColors})
     * @param textColor       validated {@code #RRGGBB} text colour
     * @param dismissHours    clamped to 1..720
     */
    @SuppressWarnings("java:S107") // a plain value object mirroring the configuration
    public BannerSettings(boolean enabled, Severity minimumSeverity, boolean includeOwnerReports, String backgroundColor,
                          String textColor, DismissPolicy dismissPolicy, int dismissHours) {
        this.enabled = enabled;
        this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        this.includeOwnerReports = includeOwnerReports;
        this.backgroundColor = Objects.requireNonNull(backgroundColor, "backgroundColor");
        this.textColor = Objects.requireNonNull(textColor, "textColor");
        this.dismissPolicy = Objects.requireNonNull(dismissPolicy, "dismissPolicy");
        this.dismissHours = Math.max(SettingsValidator.MIN_HOURS, Math.min(SettingsValidator.MAX_HOURS, dismissHours));
    }

    public static BannerSettings defaults() {
        return new BannerSettings(true, DEFAULT_MINIMUM_SEVERITY, true);
    }

    /**
     * @return the background and text colours, upper case; the default pair (with one warning) when either is not
     * {@code #RRGGBB} or when their contrast ratio is below 7:1
     */
    static String[] parseColors(String background, String text, Consumer<String> warnings) {
        Optional<String> bg = Colors.normalize(background);
        Optional<String> fg = Colors.normalize(text);
        if (bg.isPresent() && fg.isPresent() && Colors.hasEnoughContrast(bg.get(), fg.get())) {
            return new String[]{bg.get(), fg.get()};
        }
        warnings.accept("Invalid bannerBackgroundColor/bannerTextColor '" + Settings.shown(background) + "'/'"
                + Settings.shown(text) + "' (expected #RRGGBB colours with a contrast ratio of at least 7:1): using "
                + DEFAULT_BACKGROUND_COLOR + "/" + DEFAULT_TEXT_COLOR);
        return new String[]{DEFAULT_BACKGROUND_COLOR, DEFAULT_TEXT_COLOR};
    }

    /**
     * @return the policy, or {@link DismissPolicy#SESSION} (with one warning) when the value is not one
     */
    static DismissPolicy parseDismissPolicy(String value, Consumer<String> warnings) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_DISMISS_POLICY;
        }
        try {
            return DismissPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnings.accept("Invalid bannerDismissPolicy '" + Settings.shown(value)
                    + "' (expected SESSION, HOURS, UNTIL_CHANGE or NEVER): using " + DEFAULT_DISMISS_POLICY);
            return DEFAULT_DISMISS_POLICY;
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
            warnings.accept("Invalid bannerMinimumSeverity '" + Settings.shown(value) + "' (expected LOW, MEDIUM, HIGH or CRITICAL): using "
                    + DEFAULT_MINIMUM_SEVERITY);
            return DEFAULT_MINIMUM_SEVERITY;
        }
        return severity;
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

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public DismissPolicy getDismissPolicy() {
        return dismissPolicy;
    }

    public int getDismissHours() {
        return dismissHours;
    }
}
