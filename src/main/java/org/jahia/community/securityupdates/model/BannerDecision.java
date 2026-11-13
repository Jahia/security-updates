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

    /**
     * @param visible         true when the notice is enabled and at least one finding qualifies
     * @param count           number of qualifying finding rows
     * @param minimumSeverity the configured threshold
     * @param components      component of each qualifying row, sorted (one entry per row)
     */
    public BannerDecision(boolean visible, int count, Severity minimumSeverity, List<String> components) {
        this.visible = visible;
        this.count = count;
        this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        this.components = List.copyOf(components);
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
}
