package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * One fix entry of an advisory: the component it concerns, the versions that fix the issue and, when the feed
 * gives it, the first version that has the issue (versions below it are not affected).
 */
public final class AdvisoryFix {

    private final String component;
    private final String groupId;
    private final List<String> fixedVersions;
    private final String introducedVersion;

    /**
     * @param component     store module name (bundle symbolic name) or {@link InstalledComponent#JAHIA_CORE}
     * @param groupId       Maven groupId of the module, null when the feed does not give one
     * @param fixedVersions versions that fix the issue, one per maintained line
     */
    public AdvisoryFix(String component, String groupId, List<String> fixedVersions) {
        this(component, groupId, fixedVersions, null);
    }

    /**
     * @param introducedVersion the first version that has the issue (feed key {@code introducedVersion}), null
     *                          when the feed does not give one: every version below the fixes is then affected
     */
    public AdvisoryFix(String component, String groupId, List<String> fixedVersions, String introducedVersion) {
        this.component = Objects.requireNonNull(component, "component");
        this.groupId = groupId;
        this.fixedVersions = List.copyOf(fixedVersions);
        this.introducedVersion = introducedVersion;
    }

    public String getComponent() {
        return component;
    }

    public String getGroupId() {
        return groupId;
    }

    public List<String> getFixedVersions() {
        return fixedVersions;
    }

    /** @return the first version that has the issue, or null when unknown */
    public String getIntroducedVersion() {
        return introducedVersion;
    }
}
