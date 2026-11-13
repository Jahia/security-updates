package org.jahia.community.securityupdates.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The result of a security check. Immutable: a refresh publishes a new instance.
 */
public final class SecurityReport {

    private final ReportStatus status;
    private final Instant checkedAt;
    private final Instant lastSuccess;
    private final String jahiaVersion;
    private final List<StoreError> errors;
    private final List<Finding> findings;

    public SecurityReport(ReportStatus status, Instant checkedAt, Instant lastSuccess, String jahiaVersion,
                          List<StoreError> errors, List<Finding> findings) {
        this.status = Objects.requireNonNull(status, "status");
        this.checkedAt = checkedAt;
        this.lastSuccess = lastSuccess;
        this.jahiaVersion = jahiaVersion;
        this.errors = List.copyOf(errors);
        this.findings = List.copyOf(findings);
    }

    /**
     * @return a report saying that no check has run yet
     */
    public static SecurityReport notChecked(String jahiaVersion) {
        return new SecurityReport(ReportStatus.NOT_CHECKED, null, null, jahiaVersion,
                Collections.emptyList(), Collections.emptyList());
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public Instant getLastSuccess() {
        return lastSuccess;
    }

    public String getJahiaVersion() {
        return jahiaVersion;
    }

    public List<StoreError> getErrors() {
        return errors;
    }

    public List<Finding> getFindings() {
        return findings;
    }
}
