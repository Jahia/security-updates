package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BannerSettings")
class BannerSettingsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"LOW, LOW", "MEDIUM, MEDIUM", "HIGH, HIGH", "CRITICAL, CRITICAL", "critical, CRITICAL", "' high ', HIGH"})
    void acceptsTheFourSeverities(String value, Severity expected) {
        List<String> warnings = new ArrayList<>();
        assertThat(BannerSettings.parseMinimumSeverity(value, warnings::add)).isEqualTo(expected);
        assertThat(warnings).isEmpty();
    }

    @ParameterizedTest(name = "[{0}] falls back to HIGH with one warning")
    @ValueSource(strings = {"UNKNOWN", "SEVERE", "4", "high;drop"})
    void invalidFallsBackToHigh(String value) {
        List<String> warnings = new ArrayList<>();

        assertThat(BannerSettings.parseMinimumSeverity(value, warnings::add)).isEqualTo(Severity.HIGH);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("bannerMinimumSeverity").contains("HIGH");
    }

    @ParameterizedTest(name = "[{0}] means the default, silently")
    @NullAndEmptySource
    void absentIsTheDefault(String value) {
        List<String> warnings = new ArrayList<>();
        assertThat(BannerSettings.parseMinimumSeverity(value, warnings::add)).isEqualTo(Severity.HIGH);
        assertThat(warnings).isEmpty();
    }

    @Test
    void defaults() {
        BannerSettings defaults = BannerSettings.defaults();
        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(defaults.isIncludeOwnerReports()).isTrue();
    }

    @Test
    @DisplayName("Settings.from reads the banner keys of the configuration")
    void fromConfiguration() {
        SecurityUpdatesConfig config = config("bannerEnabled", false, "bannerMinimumSeverity", "bogus",
                "bannerIncludeOwnerReports", false);
        List<String> warnings = new ArrayList<>();

        Settings settings = Settings.from(config, warnings::add);

        assertThat(settings.getBanner().isEnabled()).isFalse();
        assertThat(settings.getBanner().getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(settings.getBanner().isIncludeOwnerReports()).isFalse();
        assertThat(warnings).hasSize(1);
        assertThat(settings.getDefaultStoreUrl()).isEqualTo(Settings.DEFAULT_STORE_URL);
    }

    /** A configuration answering the given values, and the OCD defaults for every other key. */
    static SecurityUpdatesConfig config(Object... keyValues) {
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put((String) keyValues[i], keyValues[i + 1]);
        }
        return (SecurityUpdatesConfig) java.lang.reflect.Proxy.newProxyInstance(SecurityUpdatesConfig.class.getClassLoader(),
                new Class<?>[]{SecurityUpdatesConfig.class}, (proxy, method, args) -> values.containsKey(method.getName())
                        ? values.get(method.getName()) : method.getDefaultValue());
    }
}
