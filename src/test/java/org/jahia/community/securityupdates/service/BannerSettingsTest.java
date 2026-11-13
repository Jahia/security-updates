package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        assertThat(defaults.getBackgroundColor()).isEqualTo("#1B1B1B");
        assertThat(defaults.getTextColor()).isEqualTo("#FFFFFF");
        assertThat(defaults.getDismissPolicy()).isEqualTo(DismissPolicy.SESSION);
        assertThat(defaults.getDismissHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("valid colours are kept, normalised to upper case")
    void validColours() {
        List<String> warnings = new ArrayList<>();
        String[] pair = BannerSettings.parseColors("#0b3d91", "#ffffff", warnings::add);
        assertThat(pair).containsExactly("#0B3D91", "#FFFFFF");
        assertThat(warnings).isEmpty();
    }

    @ParameterizedTest(name = "[{0}] on [{1}] falls back to the default pair with one warning")
    @CsvSource({"#888888, #777777", "#GGGGGG, #FFFFFF", "#1B1B1B, white", "'', ''"})
    void invalidColoursFallBack(String background, String text) {
        List<String> warnings = new ArrayList<>();

        String[] pair = BannerSettings.parseColors(background, text, warnings::add);

        assertThat(pair).containsExactly(BannerSettings.DEFAULT_BACKGROUND_COLOR, BannerSettings.DEFAULT_TEXT_COLOR);
        assertThat(warnings).hasSize(1);
    }

    @ParameterizedTest(name = "dismiss policy [{0}] -> {1}")
    @CsvSource({"SESSION, SESSION", "hours, HOURS", "Until_Change, UNTIL_CHANGE", "NEVER, NEVER"})
    void dismissPolicies(String value, DismissPolicy expected) {
        List<String> warnings = new ArrayList<>();
        assertThat(BannerSettings.parseDismissPolicy(value, warnings::add)).isEqualTo(expected);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("an invalid dismiss policy falls back to SESSION with one warning")
    void invalidDismissPolicy() {
        List<String> warnings = new ArrayList<>();
        assertThat(BannerSettings.parseDismissPolicy("FOREVER", warnings::add)).isEqualTo(DismissPolicy.SESSION);
        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("dismiss hours are clamped to 1..720")
    void dismissHoursClamped() {
        assertThat(new BannerSettings(true, Severity.HIGH, true, "#1B1B1B", "#FFFFFF", DismissPolicy.HOURS, 0).getDismissHours())
                .isEqualTo(1);
        assertThat(new BannerSettings(true, Severity.HIGH, true, "#1B1B1B", "#FFFFFF", DismissPolicy.HOURS, 9999).getDismissHours())
                .isEqualTo(720);
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

    @Test
    @DisplayName("Settings.from reads colours and dismiss settings; each invalid group gives one warning")
    void fromConfigurationColoursAndDismiss() {
        List<String> warnings = new ArrayList<>();
        Settings ok = Settings.from(config("bannerBackgroundColor", "#0b3d91", "bannerTextColor", "#FFFFFF",
                "bannerDismissPolicy", "HOURS", "bannerDismissHours", 48), warnings::add);
        assertThat(ok.getBanner().getBackgroundColor()).isEqualTo("#0B3D91");
        assertThat(ok.getBanner().getDismissPolicy()).isEqualTo(DismissPolicy.HOURS);
        assertThat(ok.getBanner().getDismissHours()).isEqualTo(48);
        assertThat(warnings).isEmpty();

        Settings bad = Settings.from(config("bannerBackgroundColor", "#888888", "bannerTextColor", "#777777",
                "bannerDismissPolicy", "FOREVER"), warnings::add);
        assertThat(bad.getBanner().getBackgroundColor()).isEqualTo(BannerSettings.DEFAULT_BACKGROUND_COLOR);
        assertThat(bad.getBanner().getDismissPolicy()).isEqualTo(DismissPolicy.SESSION);
        assertThat(warnings).hasSize(2);
    }

    @Test
    @DisplayName("toValues gives every configuration key, as written in the .cfg")
    void toValues() {
        Map<String, String> values = Settings.defaults().toValues();

        assertThat(values).containsOnlyKeys(SettingsKeys.ALL)
                .containsEntry("enabled", "true")
                .containsEntry("refreshIntervalHours", "24")
                .containsEntry("extraFeedUrls", "")
                .containsEntry("bannerBackgroundColor", "#1B1B1B")
                .containsEntry("bannerDismissPolicy", "SESSION")
                .containsEntry("bannerDismissHours", "24");
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
