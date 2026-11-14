package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;

import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Decides, from a report and the banner settings, whether the banner is shown and what it counts. Pure.
 */
public final class BannerPolicy {

    private static final char KEY_SEPARATOR = '|';

    private BannerPolicy() {
    }

    /**
     * A finding counts when its severity is at least the minimum and its source is a Jahia advisory, or an owner
     * report when those are included; a severity of UNKNOWN never counts. The banner counts the distinct
     * components of those findings. Its dismissal keys name each counted advisory of at least the minimum
     * severity, as {@code <component>|<advisory id>} (the owner report id for an owner report), so that a new
     * advisory for a component already counted brings a dismissed banner back.
     */
    public static BannerDecision decide(SecurityReport report, BannerSettings settings) {
        Severity minimum = settings.getMinimumSeverity();
        SortedSet<String> components = new TreeSet<>();
        SortedSet<String> keys = new TreeSet<>();
        for (Finding finding : report.getFindings()) {
            boolean sourceCounted = finding.getSource() == FindingSource.ADVISORY
                    || (finding.getSource() == FindingSource.OWNER_REPORTED && settings.isIncludeOwnerReports());
            if (sourceCounted && reaches(finding.getSeverity(), minimum)) {
                components.add(finding.getComponent());
                addKeys(finding, minimum, keys);
            }
        }
        return new BannerDecision(settings.isEnabled() && !components.isEmpty(), minimum, new ArrayList<>(components),
                new ArrayList<>(keys), settings.getBackgroundColor(), settings.getTextColor(), settings.getDismissPolicy(),
                settings.getDismissHours());
    }

    private static boolean reaches(Severity severity, Severity minimum) {
        return severity != Severity.UNKNOWN && severity.ordinal() >= minimum.ordinal();
    }

    private static void addKeys(Finding finding, Severity minimum, SortedSet<String> keys) {
        boolean added = false;
        for (AdvisoryRef ref : finding.getAdvisories()) {
            if (reaches(ref.getSeverity(), minimum)) {
                keys.add(finding.getComponent() + KEY_SEPARATOR + ref.getId());
                added = true;
            }
        }
        if (!added) {
            keys.add(finding.getComponent() + KEY_SEPARATOR + finding.getSource().name());
        }
    }
}
