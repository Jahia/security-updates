package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.EmailFrequency;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailComposerTest {

    private static final Instant CHECKED = Instant.parse("2026-11-13T10:00:00Z");
    private static final EmailSettings WEEKLY_HIGH = new EmailSettings(true, EmailFrequency.WEEKLY, Severity.HIGH);

    static Finding finding(String component, Severity severity, FindingSource source, AdvisoryRef... refs) {
        return new Finding(component, "org.jahia.modules", "3.4.0", "3.10.0", Compatibility.COMPATIBLE, null, severity,
                source, List.of(refs));
    }

    static AdvisoryRef ref(String id, String title, String url) {
        return new AdvisoryRef(id, title, url, List.of("CVE-2026-1234"), Severity.HIGH, "store.jahia.com");
    }

    static SecurityReport report(ReportStatus status, Finding... findings) {
        return new SecurityReport(status, CHECKED, CHECKED, "8.2.3.7", Collections.emptyList(), List.of(findings));
    }

    @Test
    @DisplayName("only findings of at least the minimum severity are kept, most severe first; UNKNOWN never counts")
    void selection() {
        SecurityReport report = report(ReportStatus.OK,
                finding("b-module", Severity.HIGH, FindingSource.ADVISORY),
                finding("low-module", Severity.LOW, FindingSource.ADVISORY),
                finding("unknown-module", Severity.UNKNOWN, FindingSource.OWNER_REPORTED),
                finding("a-module", Severity.HIGH, FindingSource.OWNER_REPORTED),
                finding("c-module", Severity.CRITICAL, FindingSource.ADVISORY));

        assertThat(EmailComposer.selected(report, Severity.HIGH)).extracting(Finding::getComponent)
                .containsExactly("c-module", "a-module", "b-module");
        assertThat(EmailComposer.selected(report, Severity.LOW)).extracting(Finding::getComponent)
                .doesNotContain("unknown-module").contains("low-module");
    }

    @Test
    @DisplayName("the subject counts the distinct components and names the server")
    void subject() {
        Finding one = finding("graphql-dxm-provider", Severity.HIGH, FindingSource.ADVISORY);
        Finding sameComponent = finding("graphql-dxm-provider", Severity.HIGH, FindingSource.OWNER_REPORTED);
        Finding other = finding("jcontent", Severity.CRITICAL, FindingSource.ADVISORY);

        assertThat(EmailComposer.periodic(report(ReportStatus.OK, one), List.of(one), WEEKLY_HIGH, "jahia-prod").getSubject())
                .isEqualTo("Jahia on jahia-prod: 1 component needs a security update");
        assertThat(EmailComposer.periodic(report(ReportStatus.OK), List.of(one, sameComponent, other), WEEKLY_HIGH, "jahia-prod")
                .getSubject()).isEqualTo("Jahia on jahia-prod: 2 components need a security update");
    }

    @Test
    @DisplayName("the server name cannot add a header: control characters are removed and the name is bounded")
    void serverNameIsHeaderSafe() {
        Finding one = finding("m", Severity.HIGH, FindingSource.ADVISORY);
        String hostile = "evil\r\nBcc: victim@example.org" + "x".repeat(300);

        EmailMessage message = EmailComposer.periodic(report(ReportStatus.OK, one), List.of(one), WEEKLY_HIGH, hostile);

        assertThat(message.getSubject()).doesNotContain("\r").doesNotContain("\n").hasSizeLessThan(200);
    }

    @Test
    @DisplayName("the text body lists each finding with its versions, severity, compatibility, source and advisories")
    void textBody() {
        Finding one = finding("graphql-dxm-provider", Severity.HIGH, FindingSource.ADVISORY,
                ref("JSA-2026-0011", "Injection", "https://academy.jahia.com/jsa"));

        String text = EmailComposer.periodic(report(ReportStatus.OK, one), List.of(one), WEEKLY_HIGH, "jahia-prod").getText();

        assertThat(text).contains("Jahia 8.2.3.7 on jahia-prod")
                .contains("1 component needs a security update (severity High or above)")
                .contains("graphql-dxm-provider 3.4.0")
                .contains("Install: 3.10.0")
                .contains("Severity: High")
                .contains("Compatibility: Compatible with this Jahia")
                .contains("Source: Jahia advisory")
                .contains("JSA-2026-0011 Injection (CVE-2026-1234) https://academy.jahia.com/jsa")
                .contains("Last check: 2026-11-13 10:00 UTC")
                .contains("Administration > Server > System components > Security updates")
                .contains("weekly");
    }

    @Test
    @DisplayName("feed text is escaped in the HTML body, and only https advisory URLs become links")
    void htmlIsEscaped() {
        Finding one = finding("graphql-dxm-provider", Severity.HIGH, FindingSource.ADVISORY,
                ref("JSA-2026-0011", "<script>alert(1)</script> & \"x\"", "https://academy.jahia.com/jsa?a=1&b=2"),
                ref("JSA-2026-0012", "Plain", "http://insecure.example.org/a"),
                ref("JSA-2026-0013", "Script", "javascript:alert(1)"));

        EmailMessage message = EmailComposer.periodic(report(ReportStatus.OK, one), List.of(one), WEEKLY_HIGH, "<b>srv</b>");

        assertThat(message.getHtml()).startsWith("<!DOCTYPE html>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;x&quot;")
                .doesNotContain("<script>")
                .doesNotContain("<b>srv</b>")
                .contains("<a href=\"https://academy.jahia.com/jsa?a=1&amp;b=2\">")
                .doesNotContain("href=\"http://insecure")
                .doesNotContain("href=\"javascript");
        assertThat(message.getText()).doesNotContain("javascript:").doesNotContain("http://insecure");
    }

    @Test
    @DisplayName("each compatibility and source has its own wording")
    void wording() {
        Finding incompatible = new Finding("a", "g", "1.0.0", "2.0.0", Compatibility.INCOMPATIBLE, "8.2.4.0", Severity.HIGH,
                FindingSource.OWNER_REPORTED, List.of());
        Finding core = new Finding("jahia-core", null, "8.2.3.7", "8.2.3.8", Compatibility.JAHIA_UPGRADE, null,
                Severity.CRITICAL, FindingSource.ADVISORY, List.of());
        Finding unknown = new Finding("b", "g", "1.0.0", "2.0.0", Compatibility.UNKNOWN, null, Severity.HIGH,
                FindingSource.ADVISORY, List.of());

        String text = EmailComposer.periodic(report(ReportStatus.OK), List.of(incompatible, core, unknown), WEEKLY_HIGH, "s")
                .getText();

        assertThat(text).contains("Compatibility: Needs Jahia 8.2.4.0")
                .contains("Source: Reported by the module owner")
                .contains("Compatibility: Jahia upgrade required")
                .contains("Compatibility: Not known");
    }

    @Test
    @DisplayName("an incomplete check is said in the email")
    void incompleteCheck() {
        Finding one = finding("m", Severity.HIGH, FindingSource.ADVISORY);

        EmailMessage message = EmailComposer.periodic(report(ReportStatus.PARTIAL, one), List.of(one), WEEKLY_HIGH, "s");

        assertThat(message.getText()).contains("the list may be incomplete");
        assertThat(message.getHtml()).contains("the list may be incomplete");
        assertThat(EmailComposer.periodic(report(ReportStatus.OK, one), List.of(one), WEEKLY_HIGH, "s").getText())
                .doesNotContain("incomplete");
    }

    @Test
    @DisplayName("at most 100 findings are listed; the rest are counted")
    void manyFindings() {
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            findings.add(finding(String.format("module-%03d", i), Severity.HIGH, FindingSource.ADVISORY));
        }

        EmailMessage message = EmailComposer.periodic(report(ReportStatus.OK), findings, WEEKLY_HIGH, "s");

        assertThat(message.getText()).contains("module-099").doesNotContain("module-100").contains("5 more");
        assertThat(message.getHtml()).contains("module-099").doesNotContain("module-100").contains("5 more");
    }

    @Test
    @DisplayName("a test email says so, and says when nothing reaches the minimum severity")
    void testEmail() {
        EmailMessage message = EmailComposer.test(report(ReportStatus.OK), List.of(),
                new EmailSettings(false, EmailFrequency.DAILY, Severity.CRITICAL), "jahia-prod");

        assertThat(message.getSubject()).isEqualTo("Test email: security updates of Jahia on jahia-prod");
        assertThat(message.getText()).contains("This is a test email")
                .contains("No component needs a security update of severity Critical or above.")
                .contains("The security updates email is off");
        assertThat(message.getHtml()).contains("This is a test email");
    }

    @Test
    @DisplayName("before the first check the email says that no check has run")
    void notChecked() {
        SecurityReport report = SecurityReport.notChecked("8.2.3.7");

        String text = EmailComposer.test(report, List.of(), WEEKLY_HIGH, "s").getText();

        assertThat(text).contains("No check has run yet").doesNotContain("Last check:");
    }

    @Test
    @DisplayName("SECUPD-SEC-001: an advisory from another store is never labelled a Jahia advisory, and names its store and link host")
    void otherStoreAdvisory() {
        AdvisoryRef jahia = ref("JSA-2026-0011", "Injection", "https://academy.jahia.com/jsa");
        AdvisoryRef mirror = new AdvisoryRef("MIRROR-1", "Spoofed", "https://evil.example.net/a", List.of(), Severity.HIGH,
                "store.example.org");
        AdvisoryRef sameHost = new AdvisoryRef("MIRROR-2", "Own link", "https://store.example.org/a", List.of(), Severity.HIGH,
                "store.example.org");
        Finding fromJahia = finding("graphql-dxm-provider", Severity.HIGH, FindingSource.ADVISORY, jahia);
        Finding fromMirror = finding("jcontent", Severity.HIGH, FindingSource.ADVISORY, mirror, sameHost);

        EmailMessage message = EmailComposer.periodic(report(ReportStatus.OK), List.of(fromJahia, fromMirror), WEEKLY_HIGH, "s");

        String text = message.getText();
        String mirrorBlock = text.substring(text.indexOf("jcontent 3.4.0"));
        assertThat(mirrorBlock).contains("Source: Advisory from store.example.org").doesNotContain("Jahia advisory")
                .contains("MIRROR-1 Spoofed (from store.example.org, link to evil.example.net) https://evil.example.net/a")
                .contains("MIRROR-2 Own link (from store.example.org) https://store.example.org/a");
        assertThat(text.substring(0, text.indexOf("jcontent 3.4.0"))).contains("Source: Jahia advisory")
                .contains("JSA-2026-0011 Injection (CVE-2026-1234) https://academy.jahia.com/jsa")
                .doesNotContain("(from ");
        assertThat(message.getHtml()).contains("Advisory from store.example.org")
                .contains("(from store.example.org, link to evil.example.net)");
    }

    @Test
    @DisplayName("SECUPD-SEC-001: an advisory whose store is not known is not labelled a Jahia advisory either")
    void unknownStoreAdvisory() {
        AdvisoryRef unknown = new AdvisoryRef("X-1", "Unknown", "https://x.example.org/a", List.of(), Severity.HIGH);
        Finding finding = finding("m", Severity.HIGH, FindingSource.ADVISORY, unknown);

        String text = EmailComposer.periodic(report(ReportStatus.OK), List.of(finding), WEEKLY_HIGH, "s").getText();

        assertThat(text).contains("Source: Advisory from a store that is not named").doesNotContain("Jahia advisory");
    }
}
