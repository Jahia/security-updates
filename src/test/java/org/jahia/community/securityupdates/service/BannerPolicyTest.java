package org.jahia.community.securityupdates.service;

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
        return new SecurityReport(ReportStatus.OK, Instant.now(), Instant.now(), "8.2.2.1", Collections.emptyList(), List.of(
                finding("jahia-core", Severity.CRITICAL, FindingSource.ADVISORY),
                finding("security-updates", Severity.HIGH, FindingSource.ADVISORY),
                finding("security-updates", Severity.HIGH, FindingSource.OWNER_REPORTED),
                finding("a-module", Severity.LOW, FindingSource.OWNER_REPORTED),
                finding("b-module", Severity.UNKNOWN, FindingSource.ADVISORY)));
    }

    private static Finding finding(String component, Severity severity, FindingSource source) {
        return new Finding(component, "g", "1.0.0", "2.0.0", Compatibility.UNKNOWN, null, severity, source,
                Collections.emptyList());
    }

    @Test
    @DisplayName("defaults: high or above, owner reports included")
    void defaults() {
        BannerDecision banner = BannerPolicy.decide(report(), BannerSettings.defaults());

        assertThat(banner.isVisible()).isTrue();
        assertThat(banner.getCount()).isEqualTo(3);
        assertThat(banner.getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(banner.getComponents()).containsExactly("jahia-core", "security-updates", "security-updates");
    }

    @ParameterizedTest(name = "minimum {0}, owner reports {1} -> {2}")
    @CsvSource({
            "CRITICAL, true, 1",
            "HIGH, true, 3",
            "HIGH, false, 2",
            "MEDIUM, true, 3",
            "LOW, true, 4",
            "LOW, false, 2"
    })
    void thresholds(Severity minimum, boolean includeOwnerReports, int expected) {
        BannerDecision banner = BannerPolicy.decide(report(), new BannerSettings(true, minimum, includeOwnerReports));

        assertThat(banner.getCount()).isEqualTo(expected);
        assertThat(banner.getComponents()).hasSize(expected).isSorted();
    }

    @Test
    @DisplayName("a finding of unknown severity never reaches a threshold")
    void unknownSeverity() {
        BannerDecision banner = BannerPolicy.decide(report(), new BannerSettings(true, Severity.LOW, true));
        assertThat(banner.getComponents()).doesNotContain("b-module");
    }

    @Test
    @DisplayName("disabled: never visible, the count is still given")
    void disabled() {
        BannerDecision banner = BannerPolicy.decide(report(), new BannerSettings(false, Severity.HIGH, true));

        assertThat(banner.isVisible()).isFalse();
        assertThat(banner.getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("nothing above the threshold: not visible")
    void nothingToShow() {
        SecurityReport empty = SecurityReport.notChecked("8.2.2.1");

        BannerDecision banner = BannerPolicy.decide(empty, BannerSettings.defaults());

        assertThat(banner.isVisible()).isFalse();
        assertThat(banner.getCount()).isZero();
        assertThat(banner.getComponents()).isEmpty();
    }
}
