package org.jahia.community.securityupdates.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Whether the security updates banner is shown in the Jahia UI, what it counts, and how it looks.
 */
public final class BannerDecision {

    /** Default banner colours (17.2:1). */
    public static final String DEFAULT_BACKGROUND_COLOR = "#1B1B1B";
    public static final String DEFAULT_TEXT_COLOR = "#FFFFFF";
    public static final DismissPolicy DEFAULT_DISMISS_POLICY = DismissPolicy.SESSION;
    public static final int DEFAULT_DISMISS_HOURS = 24;
    public static final Severity DEFAULT_MINIMUM_SEVERITY = Severity.HIGH;

    private final boolean visible;
    private final Severity minimumSeverity;
    private final List<String> components;
    private final List<String> dismissalKeys;
    private final String backgroundColor;
    private final String textColor;
    private final DismissPolicy dismissPolicy;
    private final int dismissHours;

    /**
     * @param visible         true when the banner is enabled and at least one finding qualifies
     * @param minimumSeverity the configured threshold
     * @param components      the distinct components of the qualifying findings, sorted
     * @param dismissalKeys   one {@code <component>|<advisory id>} per qualifying advisory, distinct, sorted
     * @param backgroundColor validated {@code #RRGGBB} background colour
     * @param textColor       validated {@code #RRGGBB} text colour, at least 7:1 against the background
     * @param dismissPolicy   how long a dismissal lasts
     * @param dismissHours    duration of a dismissal for {@link DismissPolicy#HOURS}
     */
    @SuppressWarnings("java:S107") // a plain value object exposed to GraphQL
    public BannerDecision(boolean visible, Severity minimumSeverity, List<String> components, List<String> dismissalKeys,
                          String backgroundColor, String textColor, DismissPolicy dismissPolicy, int dismissHours) {
        this.visible = visible;
        this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        this.components = List.copyOf(components);
        this.dismissalKeys = List.copyOf(dismissalKeys);
        this.backgroundColor = Objects.requireNonNull(backgroundColor, "backgroundColor");
        this.textColor = Objects.requireNonNull(textColor, "textColor");
        this.dismissPolicy = Objects.requireNonNull(dismissPolicy, "dismissPolicy");
        this.dismissHours = dismissHours;
    }

    /**
     * @return a banner that is not shown, with the default presentation (used when the service is not running)
     */
    public static BannerDecision hidden() {
        return new BannerDecision(false, DEFAULT_MINIMUM_SEVERITY, Collections.emptyList(), Collections.emptyList(),
                DEFAULT_BACKGROUND_COLOR, DEFAULT_TEXT_COLOR, DEFAULT_DISMISS_POLICY, DEFAULT_DISMISS_HOURS);
    }

    public boolean isVisible() {
        return visible;
    }

    /** @return the number of distinct components concerned */
    public int getCount() {
        return components.size();
    }

    public Severity getMinimumSeverity() {
        return minimumSeverity;
    }

    public List<String> getComponents() {
        return components;
    }

    public List<String> getDismissalKeys() {
        return dismissalKeys;
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
