package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * Whether the security updates notice is shown in the Jahia UI, and what it counts.
 */
public final class BannerDecision {

    private final boolean visible;
    private final int count;
    private final Severity minimumSeverity;
    private final List<String> components;
    private final String backgroundColor;
    private final String textColor;
    private final DismissPolicy dismissPolicy;
    private final int dismissHours;

    /**
     * A decision with the default presentation (colours #1B1B1B/#FFFFFF, dismissal per session).
     */
    public BannerDecision(boolean visible, int count, Severity minimumSeverity, List<String> components) {
        this(visible, count, minimumSeverity, components, "#1B1B1B", "#FFFFFF", DismissPolicy.SESSION, 24);
    }

    /**
     * @param visible         true when the notice is enabled and at least one finding qualifies
     * @param count           number of qualifying finding rows
     * @param minimumSeverity the configured threshold
     * @param components      component of each qualifying row, sorted (one entry per row)
     * @param backgroundColor validated {@code #RRGGBB} background colour
     * @param textColor       validated {@code #RRGGBB} text colour, at least 7:1 against the background
     * @param dismissPolicy   how long a dismissal lasts
     * @param dismissHours    duration of a dismissal for {@link DismissPolicy#HOURS}
     */
    @SuppressWarnings("java:S107") // a plain value object exposed to GraphQL
    public BannerDecision(boolean visible, int count, Severity minimumSeverity, List<String> components,
                          String backgroundColor, String textColor, DismissPolicy dismissPolicy, int dismissHours) {
        this.visible = visible;
        this.count = count;
        this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        this.components = List.copyOf(components);
        this.backgroundColor = Objects.requireNonNull(backgroundColor, "backgroundColor");
        this.textColor = Objects.requireNonNull(textColor, "textColor");
        this.dismissPolicy = Objects.requireNonNull(dismissPolicy, "dismissPolicy");
        this.dismissHours = dismissHours;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getCount() {
        return count;
    }

    public Severity getMinimumSeverity() {
        return minimumSeverity;
    }

    public List<String> getComponents() {
        return components;
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
