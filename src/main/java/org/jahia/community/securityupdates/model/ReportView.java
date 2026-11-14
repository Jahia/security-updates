package org.jahia.community.securityupdates.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The report as shown at one moment: the last report (with the staleness rule applied), whether a check is running
 * and when the next scheduled check is due.
 */
public final class ReportView {

    private final SecurityReport report;
    private final boolean inProgress;
    private final Instant nextCheckAt;
    private final boolean stale;

    /**
     * @param report      the report, with the staleness rule already applied
     * @param inProgress  true while a check runs
     * @param nextCheckAt when the next scheduled check is due; null when none is scheduled
     * @param stale       true when the last check is older than twice the refresh interval
     */
    public ReportView(SecurityReport report, boolean inProgress, Instant nextCheckAt, boolean stale) {
        this.report = Objects.requireNonNull(report, "report");
        this.inProgress = inProgress;
        this.nextCheckAt = nextCheckAt;
        this.stale = stale;
    }

    public SecurityReport getReport() {
        return report;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public boolean isStale() {
        return stale;
    }
}
