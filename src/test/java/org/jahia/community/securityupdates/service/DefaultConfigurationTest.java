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
    }

    @Test
    @DisplayName("settings clamp the interval and split the extra feed URLs")
    void settingsValidation() {
        assertThat(new Settings(true, 0, true, "", "").getRefreshIntervalHours()).isEqualTo(Settings.MIN_REFRESH_INTERVAL_HOURS);
        assertThat(new Settings(true, 99_999, true, "", "").getRefreshIntervalHours()).isEqualTo(Settings.MAX_REFRESH_INTERVAL_HOURS);
        assertThat(new Settings(true, 24, true, "a, b\nc,,", null).getExtraFeedUrls()).containsExactly("a", "b", "c");
        assertThat(new Settings(true, 24, true, null, null).getDefaultStoreUrl()).isEmpty();
    }
}
