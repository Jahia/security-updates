package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * One installed component that has security updates, from one source, with every advisory concerning it.
 */
public final class Finding {

    private final String component;
    private final String groupId;
    private final String installedVersion;
    private final String recommendedVersion;
    private final Compatibility compatibility;
    private final String requiredJahiaVersion;
    private final Severity severity;
    private final FindingSource source;
    private final List<AdvisoryRef> advisories;

    @SuppressWarnings("java:S107") // a plain value object exposed to GraphQL
    public Finding(String component, String groupId, String installedVersion, String recommendedVersion,
                   Compatibility compatibility, String requiredJahiaVersion, Severity severity, FindingSource source,
                   List<AdvisoryRef> advisories) {
        this.component = Objects.requireNonNull(component, "component");
        this.groupId = groupId;
        this.installedVersion = installedVersion;
        this.recommendedVersion = recommendedVersion;
        this.compatibility = Objects.requireNonNull(compatibility, "compatibility");
        this.requiredJahiaVersion = requiredJahiaVersion;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.source = Objects.requireNonNull(source, "source");
        this.advisories = List.copyOf(advisories);
    }

    public String getComponent() {
        return component;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    public String getRecommendedVersion() {
        return recommendedVersion;
    }

    public Compatibility getCompatibility() {
        return compatibility;
    }

    public String getRequiredJahiaVersion() {
        return requiredJahiaVersion;
    }

    public Severity getSeverity() {
        return severity;
    }

    public FindingSource getSource() {
        return source;
    }

    public List<AdvisoryRef> getAdvisories() {
        return advisories;
    }

    /**
     * @return a stable identity of this finding, used to log each new finding only once
     */
    public String key() {
        StringBuilder sb = new StringBuilder();
        sb.append(source).append('|').append(groupId).append(':').append(component).append('@')
                .append(installedVersion).append("->").append(recommendedVersion);
        for (AdvisoryRef ref : advisories) {
            sb.append('|').append(ref.getId());
        }
        return sb.toString();
    }
}
