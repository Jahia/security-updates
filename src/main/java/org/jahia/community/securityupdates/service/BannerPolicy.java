package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides, from a report and the banner settings, whether the notice is shown and what it counts. Pure.
 */
public final class BannerPolicy {

    private BannerPolicy() {
    }

    /**
     * Counts the finding rows whose severity is at least the minimum and whose source is a Jahia advisory, or an
     * owner report when those are included. A finding of unknown severity never qualifies.
     */
    public static BannerDecision decide(SecurityReport report, BannerSettings settings) {
        Severity minimum = settings.getMinimumSeverity();
        List<String> components = new ArrayList<>();
        for (Finding finding : report.getFindings()) {
            boolean severeEnough = finding.getSeverity() != Severity.UNKNOWN
                    && finding.getSeverity().ordinal() >= minimum.ordinal();
            boolean sourceCounted = finding.getSource() == FindingSource.ADVISORY
                    || (finding.getSource() == FindingSource.OWNER_REPORTED && settings.isIncludeOwnerReports());
            if (severeEnough && sourceCounted) {
                components.add(finding.getComponent());
            }
        }
        Collections.sort(components);
        return new BannerDecision(settings.isEnabled() && !components.isEmpty(), components.size(), minimum, components);
    }
}
