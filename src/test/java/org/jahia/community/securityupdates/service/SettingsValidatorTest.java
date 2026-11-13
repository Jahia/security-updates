package org.jahia.community.securityupdates.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettingsValidator")
class SettingsValidatorTest {

    static Map<String, String> valid() {
        Map<String, String> values = new HashMap<>(Settings.defaults().toValues());
        values.put(SettingsKeys.BANNER_BACKGROUND_COLOR, "#0b3d91");
        values.put(SettingsKeys.EXTRA_FEED_URLS, " https://a.example.org/f.json ,https://b.example.org/g.json ");
        return values;
    }

    @Test
    @DisplayName("defaults are valid")
    void defaultsAreValid() {
        ValidationResult result = SettingsValidator.validate(Settings.defaults().toValues());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getValues()).isEqualTo(Settings.defaults().toValues());
    }

    @Test
    @DisplayName("a valid input is normalised: colours upper case, booleans lower case, URLs trimmed")
    void normalises() {
        Map<String, String> input = valid();
        input.put(SettingsKeys.ENABLED, "TRUE");
        input.put(SettingsKeys.BANNER_MINIMUM_SEVERITY, "critical");
        input.put(SettingsKeys.BANNER_DISMISS_POLICY, "until_change");

        ValidationResult result = SettingsValidator.validate(input);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValues())
                .containsEntry(SettingsKeys.ENABLED, "true")
                .containsEntry(SettingsKeys.BANNER_BACKGROUND_COLOR, "#0B3D91")
                .containsEntry(SettingsKeys.BANNER_MINIMUM_SEVERITY, "CRITICAL")
                .containsEntry(SettingsKeys.BANNER_DISMISS_POLICY, "UNTIL_CHANGE")
                .containsEntry(SettingsKeys.EXTRA_FEED_URLS, "https://a.example.org/f.json, https://b.example.org/g.json");
        assertThat(result.getValues()).containsOnlyKeys(SettingsKeys.ALL);
    }

    @ParameterizedTest(name = "{0}=[{1}] -> {2}")
    @CsvSource({
            "enabled, yes, INVALID_BOOLEAN",
            "useForgeSettings, '', INVALID_BOOLEAN",
            "bannerEnabled, 1, INVALID_BOOLEAN",
            "bannerIncludeOwnerReports, nope, INVALID_BOOLEAN",
            "refreshIntervalHours, 0, OUT_OF_RANGE",
            "refreshIntervalHours, 721, OUT_OF_RANGE",
            "refreshIntervalHours, ten, INVALID_NUMBER",
            "bannerDismissHours, 0, OUT_OF_RANGE",
            "bannerDismissHours, 2.5, INVALID_NUMBER",
            "defaultStoreUrl, ftp://store.example.org, INVALID_URL",
            "defaultStoreUrl, https://user:pw@store.example.org, INVALID_URL",
            "extraFeedUrls, 'https://ok.example.org/f.json, not a url', INVALID_URL",
            "bannerMinimumSeverity, UNKNOWN, INVALID_VALUE",
            "bannerMinimumSeverity, SEVERE, INVALID_VALUE",
            "bannerDismissPolicy, FOREVER, INVALID_VALUE",
            "bannerBackgroundColor, #FFF, INVALID_COLOR",
            "bannerTextColor, white, INVALID_COLOR"
    })
    void refusesInvalidFields(String field, String value, String code) {
        Map<String, String> input = valid();
        input.put(field, value);

        ValidationResult result = SettingsValidator.validate(input);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getField()).isEqualTo(field);
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getMessage()).isNotBlank();
        });
        assertThat(result.getValues()).isEmpty();
    }

    @Test
    @DisplayName("a colour pair under 7:1 is refused on both colour fields, with the ratio")
    void lowContrast() {
        Map<String, String> input = valid();
        input.put(SettingsKeys.BANNER_BACKGROUND_COLOR, "#888888");
        input.put(SettingsKeys.BANNER_TEXT_COLOR, "#777777");

        ValidationResult result = SettingsValidator.validate(input);

        assertThat(result.getErrors()).extracting(FieldError::getField)
                .containsExactlyInAnyOrder(SettingsKeys.BANNER_BACKGROUND_COLOR, SettingsKeys.BANNER_TEXT_COLOR);
        assertThat(result.getErrors()).allSatisfy(e -> {
            assertThat(e.getCode()).isEqualTo("LOW_CONTRAST");
            assertThat(e.getMessage()).contains("1.26").contains("7");
        });
    }

    @Test
    @DisplayName("a low contrast error carries the text and background suggestions, also in its message")
    void lowContrastSuggestions() {
        Map<String, String> input = valid();
        input.put(SettingsKeys.BANNER_BACKGROUND_COLOR, "#1B1B1B");
        input.put(SettingsKeys.BANNER_TEXT_COLOR, "#333333");

        FieldError error = SettingsValidator.validate(input).getErrors().get(0);

        assertThat(error.getSuggestions()).extracting(s -> s.getField() + "=" + s.getValue() + "@" + s.getRatio())
                .containsExactly("bannerTextColor=#A6A6A6@7.07", "bannerBackgroundColor=#C1C1C1@7.01");
        assertThat(error.getMessage()).endsWith("Suggested: text #A6A6A6 (7.07:1) or background #C1C1C1 (7.01:1).");
    }

    @Test
    @DisplayName("when no single colour change reaches 7:1, the message says so and offers the default pair")
    void lowContrastWithoutSuggestion() {
        Map<String, String> input = valid();
        input.put(SettingsKeys.BANNER_BACKGROUND_COLOR, "#777777");
        input.put(SettingsKeys.BANNER_TEXT_COLOR, "#888888");

        FieldError error = SettingsValidator.validate(input).getErrors().get(0);

        assertThat(error.getSuggestions()).isEmpty();
        assertThat(error.getMessage()).contains("No single colour change reaches 7:1").contains("#1B1B1B").contains("#FFFFFF");
    }

    @Test
    @DisplayName("a missing key is an error: nothing is ever written partially")
    void missingKey() {
        Map<String, String> input = valid();
        input.remove(SettingsKeys.BANNER_DISMISS_HOURS);

        ValidationResult result = SettingsValidator.validate(input);

        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getField()).isEqualTo(SettingsKeys.BANNER_DISMISS_HOURS);
            assertThat(e.getCode()).isEqualTo("MISSING");
        });
    }

    @Test
    @DisplayName("an empty default store URL is allowed (no default store); extra feed URLs may be empty")
    void emptyUrls() {
        Map<String, String> input = valid();
        input.put(SettingsKeys.DEFAULT_STORE_URL, " ");
        input.put(SettingsKeys.EXTRA_FEED_URLS, "");

        ValidationResult result = SettingsValidator.validate(input);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValues()).containsEntry(SettingsKeys.DEFAULT_STORE_URL, "")
                .containsEntry(SettingsKeys.EXTRA_FEED_URLS, "");
    }

    @Test
    @DisplayName("several invalid fields are all reported")
    void severalErrors() {
        Map<String, String> input = valid();
        input.put(SettingsKeys.REFRESH_INTERVAL_HOURS, "0");
        input.put(SettingsKeys.DEFAULT_STORE_URL, "store");
        input.put(SettingsKeys.BANNER_TEXT_COLOR, "#12");

        assertThat(SettingsValidator.validate(input).getErrors()).extracting(FieldError::getField)
                .containsExactlyInAnyOrder(SettingsKeys.REFRESH_INTERVAL_HOURS, SettingsKeys.DEFAULT_STORE_URL,
                        SettingsKeys.BANNER_TEXT_COLOR);
    }
}
