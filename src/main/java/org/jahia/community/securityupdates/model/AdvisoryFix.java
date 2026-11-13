package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * One fix entry of an advisory: the component it concerns and the versions that fix the issue.
 */
public final class AdvisoryFix {

    private final String component;
    private final String groupId;
    private final List<String> fixedVersions;

    /**
     * @param component     store module name (bundle symbolic name) or {@link InstalledComponent#JAHIA_CORE}
     * @param groupId       Maven groupId of the module, null when the feed does not give one
     * @param fixedVersions versions that fix the issue, one per maintained line
     */
    public AdvisoryFix(String component, String groupId, List<String> fixedVersions) {
        this.component = Objects.requireNonNull(component, "component");
        this.groupId = groupId;
        this.fixedVersions = List.copyOf(fixedVersions);
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
}
