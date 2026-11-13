package org.jahia.community.securityupdates.model;

import java.util.Objects;

/**
 * A component running on this Jahia: a module (bundle symbolic name, groupId, version) or the platform.
 */
public final class InstalledComponent {

    /** Component name the feed uses for the Jahia platform itself. */
    public static final String JAHIA_CORE = "jahia-core";

    private final String name;
    private final String groupId;
    private final String version;

    public InstalledComponent(String name, String groupId, String version) {
        this.name = Objects.requireNonNull(name, "name");
        this.groupId = groupId;
        this.version = Objects.requireNonNull(version, "version");
    }

    public String getName() {
        return name;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getVersion() {
        return version;
    }

    public boolean isCore() {
        return JAHIA_CORE.equals(name);
    }

    @Override
    public String toString() {
        return name + "@" + version;
    }
}
