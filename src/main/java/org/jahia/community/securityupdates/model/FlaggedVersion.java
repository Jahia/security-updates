package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * A module version that its owner flagged on the store as a security update. Any store developer can set
 * these: they are not Jahia advisories and are reported as {@link FindingSource#OWNER_REPORTED}.
 */
public final class FlaggedVersion {

    private final String groupId;
    private final String name;
    private final String version;
    private final Severity severity;
    private final List<String> references;
    private final String note;

    public FlaggedVersion(String groupId, String name, String version, Severity severity, List<String> references,
                          String note) {
        this.groupId = groupId;
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.severity = severity == null ? Severity.UNKNOWN : severity;
        this.references = List.copyOf(references);
        this.note = note;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Severity getSeverity() {
        return severity;
    }

    public List<String> getReferences() {
        return references;
    }

    public String getNote() {
        return note;
    }
}
