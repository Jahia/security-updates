package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BannerPolicy")
class BannerPolicyTest {

    /** The rows of the e2e fixture store, plus a low owner row and an unknown-severity advisory row. */
    private static SecurityReport report() {
        return new SecurityReport(ReportStatus.OK, Instant.EPOCH, Instant.EPOCH, "8.2.2.1", Collections.emptyList(), List.of(
                finding("jahia-core", Severity.CRITICAL, FindingSource.ADVISORY),
                finding("security-updates", Severity.HIGH, FindingSource.ADVISORY),
                finding("security-updates", Severity.HIGH, FindingSource.OWNER_REPORTED),
                finding("a-module", Severity.LOW, FindingSource.OWNER_REPORTED),
                finding("b-module", Severity.UNKNOWN, FindingSource.ADVISORY)));
    }

    private static Finding finding(String component, Severity severity, FindingSource source) {
        return finding(component, severity, source, List.of(new AdvisoryRef(component + "-" + source + "-" + severity, null,
                null, List.of(), severity)));
    }

    private static Finding finding(String component, Severity severity, FindingSource source, List<AdvisoryRef> refs) {
        return new Finding(component, "g", "1.0.0", "2.0.0", Compatibility.UNKNOWN, null, severity, source, refs);
    }

    private static AdvisoryRef ref(String id, Severity severity) {
        return new AdvisoryRef(id, null, null, List.of(), severity);
    }

    @ParameterizedTest(name = "a {0} report with findings: the banner counts them as for OK")
    @org.junit.jupiter.params.provider.EnumSource(value = ReportStatus.class, names = {"PARTIAL", "UNAVAILABLE", "DISABLED"})
    void statusDoesNotHideFindings(ReportStatus status) {
        SecurityReport ok = report();
        SecurityReport other = new SecurityReport(status, ok.getCheckedAt(), ok.getLastSuccess(), ok.getJahiaVersion(),
                ok.getErrors(), ok.getFindings());

        BannerDecision banner = BannerPolicy.decide(other, BannerSettings.defaults());

        // the decision reads the findings only; the service never builds a DISABLED report with findings
        assertThat(banner.isVisible()).isTrue();
        assertThat(banner.getComponents()).containsExactly("jahia-core", "security-updates");
        assertThat(banner.getDismissalKeys()).isEqualTo(BannerPolicy.decide(ok, BannerSettings.defaults()).getDismissalKeys());
    }

    @Test
    @DisplayName("defaults: high or above, owner reports included")
    void defaults() {
        BannerDecision banner = BannerPolicy.decide(report(), BannerSettings.defaults());

        assertThat(banner.isVisible()).isTrue();
        assertThat(banner.getCount()).isEqualTo(2);
        assertThat(banner.getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(banner.getComponents()).containsExactly("jahia-core", "security-updates");
        assertThat(banner.getDismissalKeys()).containsExactly("jahia-core|jahia-core-ADVISORY-CRITICAL",
                "security-updates|security-updates-ADVISORY-HIGH", "security-updates|security-updates-OWNER_REPORTED-HIGH");
    }

    @ParameterizedTest(name = "minimum {0}, owner reports {1} -> {2}")
    @CsvSource({
            "CRITICAL, true, 1",
            "HIGH, true, 2",
            "HIGH, false, 2",
            "MEDIUM, true, 2",
            "LOW, true, 3",
            "LOW, false, 2"
    })
    void thresholds(Severity minimum, boolean includeOwnerReports, int expected) {
        BannerDecision banner = BannerPolicy.decide(report(), new BannerSettings(true, minimum, includeOwnerReports));

        assertThat(banner.getCount()).isEqualTo(expected);
        assertThat(banner.getComponents()).hasSize(expected).isSorted().doesNotHaveDuplicates();
        assertThat(banner.getDismissalKeys()).isSorted().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a new advisory for an already counted component gives a new dismissal key, not a new component")
    void newAdvisoryForACountedComponentGivesANewKey() {
        SecurityReport before = new SecurityReport(ReportStatus.OK, Instant.EPOCH, Instant.EPOCH, "8.2.2.1", List.of(), List.of(
                finding("jahia-core", Severity.CRITICAL, FindingSource.ADVISORY, List.of(ref("JSA-2026-0001", Severity.CRITICAL)))));
        SecurityReport after = new SecurityReport(ReportStatus.OK, Instant.EPOCH, Instant.EPOCH, "8.2.2.1", List.of(), List.of(
                finding("jahia-core", Severity.CRITICAL, FindingSource.ADVISORY, List.of(ref("JSA-2026-0001", Severity.CRITICAL),
                        ref("JSA-2026-0002", Severity.CRITICAL)))));

        BannerDecision first = BannerPolicy.decide(before, BannerSettings.defaults());
        BannerDecision second = BannerPolicy.decide(after, BannerSettings.defaults());

        assertThat(second.getComponents()).isEqualTo(first.getComponents());
        assertThat(second.getCount()).isEqualTo(first.getCount());
        assertThat(second.getDismissalKeys()).containsExactly("jahia-core|JSA-2026-0001", "jahia-core|JSA-2026-0002")
                .containsAll(first.getDismissalKeys());
    }

    @Test
    @DisplayName("an advisory below the minimum severity gives no dismissal key, even in a counted finding")
    void advisoriesBelowTheMinimumGiveNoKey() {
        SecurityReport report = new SecurityReport(ReportStatus.OK, Instant.EPOCH, Instant.EPOCH, "8.2.2.1", List.of(), List.of(
                finding("jahia-core", Severity.CRITICAL, FindingSource.ADVISORY, List.of(ref("JSA-HIGH-ONE", Severity.CRITICAL),
                        ref("JSA-LOW-ONE", Severity.LOW), ref("JSA-UNKNOWN", Severity.UNKNOWN)))));

        BannerDecision banner = BannerPolicy.decide(report, BannerSettings.defaults());

        assertThat(banner.getDismissalKeys()).containsExactly("jahia-core|JSA-HIGH-ONE");
    }

    @Test
    @DisplayName("a counted finding without advisory reference still gives a key, per component and source")
    void findingWithoutReference() {
        SecurityReport report = new SecurityReport(ReportStatus.OK, Instant.EPOCH, Instant.EPOCH, "8.2.2.1", List.of(), List.of(
                finding("jahia-core", Severity.CRITICAL, FindingSource.ADVISORY, List.of())));

        assertThat(BannerPolicy.decide(report, BannerSettings.defaults()).getDismissalKeys())
                .containsExactly("jahia-core|ADVISORY");
    }

    @Test
    @DisplayName("a finding of unknown severity never reaches a threshold")
    void unknownSeverity() {
        BannerDecision banner = BannerPolicy.decide(report(), new BannerSettings(true, Severity.LOW, true));
        assertThat(banner.getComponents()).isNotEmpty().doesNotContain("b-module");
    }

    @Test
    @DisplayName("disabled: never visible, the count is still given")
    void disabled() {
        BannerDecision banner = BannerPolicy.decide(report(), new BannerSettings(false, Severity.HIGH, true));

        assertThat(banner.isVisible()).isFalse();
        assertThat(banner.getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("nothing above the threshold: not visible")
    void nothingToShow() {
        SecurityReport empty = SecurityReport.notChecked("8.2.2.1");

        BannerDecision banner = BannerPolicy.decide(empty, BannerSettings.defaults());

        assertThat(banner.isVisible()).isFalse();
        assertThat(banner.getCount()).isZero();
        assertThat(banner.getComponents()).isEmpty();
        assertThat(banner.getDismissalKeys()).isEmpty();
    }

    @Test
    @DisplayName("the hidden decision used when the service is not running has the default presentation")
    void hidden() {
        BannerDecision banner = BannerDecision.hidden();

        assertThat(banner.isVisible()).isFalse();
        assertThat(banner.getBackgroundColor()).isEqualTo(BannerDecision.DEFAULT_BACKGROUND_COLOR);
        assertThat(banner.getTextColor()).isEqualTo(BannerDecision.DEFAULT_TEXT_COLOR);
        assertThat(banner.getDismissHours()).isEqualTo(BannerDecision.DEFAULT_DISMISS_HOURS);
        assertThat(BannerSettings.defaults().getBackgroundColor()).isEqualTo(BannerDecision.DEFAULT_BACKGROUND_COLOR);
    }

    @Test
    @DisplayName("passes the validated colours and dismiss policy through")
    void presentation() {
        BannerSettings settings = new BannerSettings(true, Severity.HIGH, true, "#0B3D91", "#FFFFFF",
                org.jahia.community.securityupdates.model.DismissPolicy.HOURS, 12);

        BannerDecision banner = BannerPolicy.decide(report(), settings);

        assertThat(banner.getBackgroundColor()).isEqualTo("#0B3D91");
        assertThat(banner.getTextColor()).isEqualTo("#FFFFFF");
        assertThat(banner.getDismissPolicy()).isEqualTo(org.jahia.community.securityupdates.model.DismissPolicy.HOURS);
        assertThat(banner.getDismissHours()).isEqualTo(12);
    }
}
