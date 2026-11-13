package org.jahia.community.securityupdates.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Shipped default configuration")
class DefaultConfigurationTest {

    private static final String CFG = "/META-INF/configurations/" + SecurityUpdateService.PID + ".cfg";

    private static String content() throws IOException {
        try (InputStream in = DefaultConfigurationTest.class.getResourceAsStream(CFG)) {
            assertThat(in).as(CFG).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("starts with the exact marker that stops Jahia from overwriting the deployed file")
    void firstLineMarker() throws IOException {
        assertThat(content().split("\\R", 2)[0]).isEqualTo("# default configuration - won't be overridden");
    }

    @Test
    @DisplayName("declares exactly the OCD keys, with the OCD defaults")
    void keysMatchTheOcd() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(content()));

        assertThat(properties.stringPropertyNames()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(SecurityUpdatesConfig.class.getDeclaredMethods()).map(Method::getName).collect(Collectors.toList()));
        Settings defaults = Settings.defaults();
        assertThat(properties.getProperty("enabled")).isEqualTo(String.valueOf(defaults.isEnabled()));
        assertThat(properties.getProperty("refreshIntervalHours")).isEqualTo(String.valueOf(defaults.getRefreshIntervalHours()));
        assertThat(properties.getProperty("useForgeSettings")).isEqualTo(String.valueOf(defaults.isUseForgeSettings()));
        assertThat(properties.getProperty("extraFeedUrls")).isEmpty();
        assertThat(properties.getProperty("defaultStoreUrl")).isEqualTo(defaults.getDefaultStoreUrl());
        assertThat(properties.getProperty("bannerEnabled")).isEqualTo(String.valueOf(defaults.getBanner().isEnabled()));
        assertThat(properties.getProperty("bannerMinimumSeverity")).isEqualTo(defaults.getBanner().getMinimumSeverity().name());
        assertThat(properties.getProperty("bannerIncludeOwnerReports"))
                .isEqualTo(String.valueOf(defaults.getBanner().isIncludeOwnerReports()));
    }

    @Test
    @DisplayName("every value of the shipped file is the default, read without warning")
    void everyValueIsTheDefault() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(content()));
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        properties.stringPropertyNames().forEach(key -> map.put(key, properties.getProperty(key)));

        Settings shipped = Settings.fromProperties(map, warnings::add);

        assertThat(shipped.toValues()).isEqualTo(Settings.defaults().toValues());
        assertThat(shipped.getOnDemandMinInterval()).isEqualTo(Settings.defaults().getOnDemandMinInterval());
        assertThat(warnings).isEmpty();
        for (String key : SettingsKeys.ALL) {
            assertThat(properties.getProperty(key)).as(key).isEqualTo(Settings.defaults().toValues().get(key));
        }
    }

    @Test
    @DisplayName("the settings UI keys are the OCD keys except the hidden on-demand interval")
    void uiKeysAreTheOcdKeys() {
        assertThat(Arrays.stream(SecurityUpdatesConfig.class.getDeclaredMethods()).map(Method::getName)
                .filter(name -> !Settings.ON_DEMAND_MIN_INTERVAL_SECONDS.equals(name)))
                .containsExactlyInAnyOrderElementsOf(SettingsKeys.ALL);
    }

    @Test
    @DisplayName("the shipped file and the metatype descriptions require https and do not name the JCR path")
    void httpsOnlyWording() throws IOException {
        Properties en = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/OSGI-INF/l10n/security-updates/config.properties")) {
            en.load(in);
        }
        assertThat(content()).doesNotContain("http(s)").doesNotContain("/settings/forgesSettings");
        assertThat(en.getProperty("extraFeedUrls.desc")).contains("https").doesNotContain("http(s)");
        assertThat(en.getProperty("defaultStoreUrl.desc")).contains("https");
        assertThat(en.getProperty("useForgeSettings.desc")).doesNotContain("/settings/forgesSettings");
    }

    @Test
    @DisplayName("metatype labels: same keys in en and fr, accents readable by Properties.load (ISO-8859-1)")
    void metatypeLabels() throws IOException {
        Properties en = new Properties();
        Properties fr = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/OSGI-INF/l10n/security-updates/config.properties")) {
            en.load(in);
        }
        try (InputStream in = getClass().getResourceAsStream("/OSGI-INF/l10n/security-updates/config_fr.properties")) {
            fr.load(in);
        }

        assertThat(fr.stringPropertyNames()).isEqualTo(en.stringPropertyNames());
        assertThat(fr.getProperty("severity.HIGH")).isEqualTo("\u00c9lev\u00e9e");
        for (Method method : SecurityUpdatesConfig.class.getDeclaredMethods()) {
            assertThat(en.getProperty(method.getName())).as(method.getName()).isNotBlank();
            assertThat(en.getProperty(method.getName() + ".desc")).as(method.getName()).isNotBlank();
        }
    }
}
