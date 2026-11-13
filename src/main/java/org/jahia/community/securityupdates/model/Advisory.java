package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * A Jahia security advisory as published in the store security feed. All text is untrusted.
 */
public final class Advisory {

    private final String id;
    private final String title;
    private final Severity severity;
    private final Double cvss;
    private final List<String> cves;
    private final String url;
    private final String published;
    private final List<AdvisoryFix> fixes;

    @SuppressWarnings("java:S107") // a plain value object mirroring the feed entry
    public Advisory(String id, String title, Severity severity, Double cvss, List<String> cves, String url,
                    String published, List<AdvisoryFix> fixes) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title;
        this.severity = severity == null ? Severity.UNKNOWN : severity;
        this.cvss = cvss;
        this.cves = List.copyOf(cves);
        this.url = url;
        this.published = published;
        this.fixes = List.copyOf(fixes);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Double getCvss() {
        return cvss;
    }

    public List<String> getCves() {
        return cves;
    }

    public String getUrl() {
        return url;
    }

    public String getPublished() {
        return published;
    }

    public List<AdvisoryFix> getFixes() {
        return fixes;
    }
}
