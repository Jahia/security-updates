package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * The part of an advisory (or of an owner report) that is shown next to a finding.
 */
public final class AdvisoryRef {

    private final String id;
    private final String title;
    private final String url;
    private final List<String> cves;
    private final Severity severity;

    public AdvisoryRef(String id, String title, String url, List<String> cves, Severity severity) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title;
        this.url = url;
        this.cves = List.copyOf(cves);
        this.severity = severity == null ? Severity.UNKNOWN : severity;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public List<String> getCves() {
        return cves;
    }

    public Severity getSeverity() {
        return severity;
    }
}
