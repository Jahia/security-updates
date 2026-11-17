package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes the security email, in English, as plain text and as HTML. Pure: no I/O.
 *
 * <ul>
 *     <li>The feed text (titles, ids, CVEs, URLs) is untrusted: it is escaped in the HTML body, and an advisory URL
 *     is shown (and linked) only when it is an https URL.</li>
 *     <li>The subject holds no feed text; the server name is reduced to printable characters and bounded, so that
 *     it cannot add a mail header.</li>
 *     <li>At most {@value #MAX_LISTED} findings are listed; the others are counted.</li>
 * </ul>
 */
final class EmailComposer {

    static final int MAX_LISTED = 100;
    private static final int MAX_SERVER_NAME = 100;
    private static final String PAGE_PATH = "Administration > Server > System components > Security updates";
    private static final DateTimeFormatter CHECK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final String NEW_LINE = "\n";
    private static final String HTML_PARAGRAPH = "<p>";
    private static final String HTML_PARAGRAPH_END = "</p>\n";

    private EmailComposer() {
    }

    /**
     * @return the findings of at least {@code minimum} severity (UNKNOWN never counts), most severe first, then by
     * component name
     */
    static List<Finding> selected(SecurityReport report, Severity minimum) {
        List<Finding> selected = new ArrayList<>();
        for (Finding finding : report.getFindings()) {
            Severity severity = finding.getSeverity();
            if (severity != Severity.UNKNOWN && severity.ordinal() >= minimum.ordinal()) {
                selected.add(finding);
            }
        }
        selected.sort(Comparator.comparing(Finding::getSeverity).reversed().thenComparing(Finding::getComponent));
        return selected;
    }

    /** The scheduled email: {@code findings} is not empty. */
    static EmailMessage periodic(SecurityReport report, List<Finding> findings, EmailSettings settings, String serverName) {
        String server = safeServerName(serverName);
        String subject = "Jahia on " + server + ": " + componentsSentence(findings);
        return new EmailMessage(subject, text(report, findings, settings, server, false),
                html(subject, report, findings, settings, server, false));
    }

    /** The email sent by "Send a test email": the same content, even with no finding. */
    static EmailMessage test(SecurityReport report, List<Finding> findings, EmailSettings settings, String serverName) {
        String server = safeServerName(serverName);
        String subject = "Test email: security updates of Jahia on " + server;
        return new EmailMessage(subject, text(report, findings, settings, server, true),
                html(subject, report, findings, settings, server, true));
    }

    // ------------------------------------------------------------------------------------------ text

    private static String text(SecurityReport report, List<Finding> findings, EmailSettings settings, String server,
                               boolean test) {
        StringBuilder sb = new StringBuilder();
        if (test) {
            sb.append("This is a test email, sent from the Settings tab of the Security updates page.").append(NEW_LINE)
                    .append(NEW_LINE);
        }
        sb.append("Security updates of Jahia ").append(report.getJahiaVersion()).append(" on ").append(server).append(NEW_LINE)
                .append(NEW_LINE).append(summary(findings, settings)).append(NEW_LINE);
        int listed = Math.min(findings.size(), MAX_LISTED);
        for (Finding finding : findings.subList(0, listed)) {
            sb.append(NEW_LINE).append(finding.getComponent()).append(' ').append(finding.getInstalledVersion()).append(NEW_LINE);
            for (String[] line : details(finding)) {
                sb.append("  ").append(line[0]).append(": ").append(line[1]).append(NEW_LINE);
            }
            List<String> advisories = advisoryTexts(finding);
            if (!advisories.isEmpty()) {
                sb.append("  Advisories:").append(NEW_LINE);
                advisories.forEach(a -> sb.append("    ").append(a).append(NEW_LINE));
            }
        }
        if (findings.size() > listed) {
            sb.append(NEW_LINE).append(moreText(findings.size() - listed)).append(NEW_LINE);
        }
        sb.append(NEW_LINE).append(checkText(report)).append(NEW_LINE);
        String status = statusText(report.getStatus());
        if (status != null) {
            sb.append(status).append(NEW_LINE);
        }
        sb.append(NEW_LINE).append("Details in Jahia: ").append(PAGE_PATH).append('.').append(NEW_LINE)
                .append(NEW_LINE).append(footer(settings)).append(NEW_LINE);
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------ HTML

    private static String html(String subject, SecurityReport report, List<Finding> findings, EmailSettings settings,
                               String server, boolean test) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<title>")
                .append(escape(subject)).append("</title>\n</head>\n<body style=\"font-family: Arial, sans-serif; color: #1B1B1B;"
                        + " background: #FFFFFF; line-height: 1.5;\">\n");
        if (test) {
            sb.append("<p><strong>This is a test email, sent from the Settings tab of the Security updates page.</strong></p>\n");
        }
        sb.append("<h1 style=\"font-size: 1.25em;\">Security updates of Jahia ").append(escape(report.getJahiaVersion()))
                .append(" on ").append(escape(server)).append("</h1>\n")
                .append(HTML_PARAGRAPH).append(escape(summary(findings, settings))).append(HTML_PARAGRAPH_END);
        int listed = Math.min(findings.size(), MAX_LISTED);
        for (Finding finding : findings.subList(0, listed)) {
            sb.append("<h2 style=\"font-size: 1.1em;\">").append(escape(finding.getComponent())).append(' ')
                    .append(escape(finding.getInstalledVersion())).append("</h2>\n<ul>\n");
            for (String[] line : details(finding)) {
                sb.append("<li>").append(escape(line[0])).append(": ").append(escape(line[1])).append("</li>\n");
            }
            appendAdvisories(sb, finding);
            sb.append("</ul>\n");
        }
        if (findings.size() > listed) {
            sb.append(HTML_PARAGRAPH).append(escape(moreText(findings.size() - listed))).append(HTML_PARAGRAPH_END);
        }
        sb.append(HTML_PARAGRAPH).append(escape(checkText(report)));
        String status = statusText(report.getStatus());
        if (status != null) {
            sb.append("<br>").append(escape(status));
        }
        sb.append(HTML_PARAGRAPH_END)
                .append(HTML_PARAGRAPH).append("Details in Jahia: ").append(escape(PAGE_PATH)).append('.').append(HTML_PARAGRAPH_END)
                .append("<p style=\"font-size: 0.9em;\">").append(escape(footer(settings))).append(HTML_PARAGRAPH_END)
                .append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void appendAdvisories(StringBuilder sb, Finding finding) {
        if (finding.getAdvisories().isEmpty()) {
            return;
        }
        sb.append("<li>Advisories:\n<ul>\n");
        for (AdvisoryRef ref : finding.getAdvisories()) {
            sb.append("<li>");
            String label = advisoryLabel(ref);
            if (isHttps(ref.getUrl())) {
                sb.append("<a href=\"").append(escape(ref.getUrl())).append("\">").append(escape(label)).append("</a>");
            } else {
                sb.append(escape(label));
            }
            sb.append("</li>\n");
        }
        sb.append("</ul>\n</li>\n");
    }

    // ------------------------------------------------------------------------------------------ shared wording

    private static String componentsSentence(List<Finding> findings) {
        int count = distinctComponents(findings);
        return count == 1 ? "1 component needs a security update" : count + " components need a security update";
    }

    private static String summary(List<Finding> findings, EmailSettings settings) {
        String severity = label(settings.getMinimumSeverity());
        if (findings.isEmpty()) {
            return "No component needs a security update of severity " + severity + " or above.";
        }
        return componentsSentence(findings) + " (severity " + severity + " or above).";
    }

    private static int distinctComponents(List<Finding> findings) {
        Set<String> components = new LinkedHashSet<>();
        findings.forEach(f -> components.add(f.getComponent()));
        return components.size();
    }

    private static List<String[]> details(Finding finding) {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"Install", finding.getRecommendedVersion()});
        lines.add(new String[]{"Severity", label(finding.getSeverity())});
        lines.add(new String[]{"Compatibility", compatibility(finding)});
        lines.add(new String[]{"Source", finding.getSource() == FindingSource.ADVISORY
                ? "Jahia advisory" : "Reported by the module owner"});
        return lines;
    }

    private static String compatibility(Finding finding) {
        switch (finding.getCompatibility()) {
            case COMPATIBLE:
                return "Compatible with this Jahia";
            case INCOMPATIBLE:
                return finding.getRequiredJahiaVersion() == null ? "Needs another Jahia version"
                        : "Needs Jahia " + finding.getRequiredJahiaVersion();
            case JAHIA_UPGRADE:
                return "Jahia upgrade required";
            default:
                return "Not known";
        }
    }

    private static List<String> advisoryTexts(Finding finding) {
        List<String> texts = new ArrayList<>();
        for (AdvisoryRef ref : finding.getAdvisories()) {
            String label = advisoryLabel(ref);
            texts.add(isHttps(ref.getUrl()) ? label + " " + ref.getUrl() : label);
        }
        return texts;
    }

    private static String advisoryLabel(AdvisoryRef ref) {
        StringBuilder sb = new StringBuilder(ref.getId());
        if (ref.getTitle() != null && !ref.getTitle().isEmpty()) {
            sb.append(' ').append(ref.getTitle());
        }
        if (!ref.getCves().isEmpty()) {
            sb.append(" (").append(String.join(", ", ref.getCves())).append(')');
        }
        return sb.toString();
    }

    private static String moreText(int more) {
        return "... and " + more + " more on the Security updates page.";
    }

    private static String checkText(SecurityReport report) {
        return report.getCheckedAt() == null ? "No check has run yet." : "Last check: " + CHECK_TIME.format(report.getCheckedAt());
    }

    /** @return a sentence when the check is not complete, null when it is */
    private static String statusText(ReportStatus status) {
        switch (status) {
            case PARTIAL:
                return "Some stores could not be checked: the list may be incomplete.";
            case UNAVAILABLE:
                return "No security feed could be read: the list may be incomplete.";
            case DISABLED:
                return "The checks are turned off.";
            default:
                return null;
        }
    }

    private static String footer(EmailSettings settings) {
        if (!settings.isEnabled()) {
            return "The security updates email is off. Turn it on in the Settings tab of the Security updates page.";
        }
        return "You receive this email " + settings.getFrequency().name().toLowerCase(Locale.ROOT)
                + " at most, while security updates are needed. Change it in the Settings tab of the Security updates page.";
    }

    private static String label(Severity severity) {
        String name = severity.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean isHttps(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    /** Printable characters only (no line break can reach a header), at most {@value #MAX_SERVER_NAME}. */
    static String safeServerName(String name) {
        StringBuilder sb = new StringBuilder();
        String value = name == null ? "" : name;
        for (int i = 0; i < value.length() && sb.length() < MAX_SERVER_NAME; i++) {
            char c = value.charAt(i);
            if (!Character.isISOControl(c) && Character.getType(c) != Character.FORMAT) {
                sb.append(c);
            }
        }
        String safe = sb.toString().trim();
        return safe.isEmpty() ? "an unknown server" : safe;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
