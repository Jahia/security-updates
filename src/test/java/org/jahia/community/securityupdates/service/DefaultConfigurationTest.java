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

    private static Properties labels(String suffix) throws IOException {
        Properties labels = new Properties();
        try (InputStream in = DefaultConfigurationTest.class.getResourceAsStream(
                "/OSGI-INF/l10n/security-updates/config" + suffix + ".properties")) {
            labels.load(in);
        }
        return labels;
    }

    private static org.json.JSONObject uiLocale(String language) throws IOException {
        try (InputStream in = DefaultConfigurationTest.class.getResourceAsStream("/javascript/locales/" + language + ".json")) {
            assertThat(in).as(language).isNotNull();
            return new org.json.JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("the dismiss policy labels of the configuration UI are those of the settings page")
    void dismissLabelsMatchTheUi() throws IOException {
        for (String language : java.util.List.of("en", "fr")) {
            Properties labels = labels("en".equals(language) ? "" : "_" + language);
            org.json.JSONObject dismiss = uiLocale(language).getJSONObject("settings").getJSONObject("dismiss");
            for (org.jahia.community.securityupdates.model.DismissPolicy policy
                    : org.jahia.community.securityupdates.model.DismissPolicy.values()) {
                assertThat(labels.getProperty("dismiss." + policy.name())).as(language + " " + policy)
                        .isEqualTo(dismiss.getString(policy.name()));
            }
        }
    }

    @Test
    @DisplayName("the URL attributes, which may be empty, are not required in the metatype")
    void urlAttributesAreOptional() throws IOException {
        // the metatype XML is generated when the bundle is packaged, after the tests, and @AttributeDefinition is not
        // kept at run time: the annotation is read from the source
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/org/jahia/community/securityupdates/service/SecurityUpdatesConfig.java")), StandardCharsets.UTF_8);
        for (String key : java.util.List.of(SettingsKeys.DEFAULT_STORE_URL, SettingsKeys.EXTRA_FEED_URLS)) {
            assertThat(source).containsPattern("@AttributeDefinition\\(name = \"%" + key + "\"[^)]*required = false\\)");
        }
        assertThat(labels("").getProperty("defaultStoreUrl.desc")).contains("empty for none");
    }
}
