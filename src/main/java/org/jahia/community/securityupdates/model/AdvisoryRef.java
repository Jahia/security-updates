package org.jahia.community.securityupdates.model;

import java.util.List;
import java.util.Objects;

/**
 * The part of an advisory (or of an owner report) that is shown next to a finding, with the host of the store whose
 * feed published it.
 */
public final class AdvisoryRef {

    private final String id;
    private final String title;
    private final String url;
    private final List<String> cves;
    private final Severity severity;
    private final String storeHost;

    /** A reference whose store is unknown (empty store host). */
    public AdvisoryRef(String id, String title, String url, List<String> cves, Severity severity) {
        this(id, title, url, cves, severity, "");
    }

    /**
     * @param storeHost the lower-case host of the store whose feed published it; null reads as empty
     */
    public AdvisoryRef(String id, String title, String url, List<String> cves, Severity severity, String storeHost) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title;
        this.url = url;
        this.cves = List.copyOf(cves);
        this.severity = severity == null ? Severity.UNKNOWN : severity;
        this.storeHost = storeHost == null ? "" : storeHost;
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

    /** @return the lower-case host of the store whose feed published it, such as {@code store.jahia.com}; never null */
    public String getStoreHost() {
        return storeHost;
    }
}
