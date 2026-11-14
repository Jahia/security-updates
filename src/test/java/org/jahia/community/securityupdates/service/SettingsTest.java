package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Settings")
class SettingsTest {

    private final List<String> warnings = new ArrayList<>();

    private static Map<String, Object> stored(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("no stored value: the defaults, without warning")
    void emptyPropertiesAreTheDefaults() {
        Settings settings = Settings.fromProperties(Map.of(), warnings::add);

        assertThat(settings.toValues()).isEqualTo(Settings.defaults().toValues());
        assertThat(settings.getOnDemandMinInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("stored strings (as FileInstall gives them) and typed values are both read")
    void readsStoredValues() {
        Settings settings = Settings.fromProperties(stored(
                SettingsKeys.ENABLED, "FALSE",
                SettingsKeys.REFRESH_INTERVAL_HOURS, 6,
                SettingsKeys.USE_FORGE_SETTINGS, Boolean.FALSE,
                SettingsKeys.EXTRA_FEED_URLS, "https://a.example.org/f.json https://b.example.org/g.json",
                SettingsKeys.DEFAULT_STORE_URL, " https://store.example.org ",
                SettingsKeys.BANNER_ENABLED, "false",
                SettingsKeys.BANNER_MINIMUM_SEVERITY, "critical",
                SettingsKeys.BANNER_INCLUDE_OWNER_REPORTS, "false",
                SettingsKeys.BANNER_BACKGROUND_COLOR, "#0b3d91",
                SettingsKeys.BANNER_TEXT_COLOR, "#ffffff",
                SettingsKeys.BANNER_DISMISS_POLICY, "hours",
                SettingsKeys.BANNER_DISMISS_HOURS, "12",
                Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "0"), warnings::add);

        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.getRefreshIntervalHours()).isEqualTo(6);
        assertThat(settings.isUseForgeSettings()).isFalse();
        assertThat(settings.getExtraFeedUrls()).containsExactly("https://a.example.org/f.json", "https://b.example.org/g.json");
        assertThat(settings.getDefaultStoreUrl()).isEqualTo("https://store.example.org");
        assertThat(settings.getBanner().isEnabled()).isFalse();
        assertThat(settings.getBanner().getMinimumSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(settings.getBanner().isIncludeOwnerReports()).isFalse();
        assertThat(settings.getBanner().getBackgroundColor()).isEqualTo("#0B3D91");
        assertThat(settings.getBanner().getDismissPolicy()).isEqualTo(DismissPolicy.HOURS);
        assertThat(settings.getBanner().getDismissHours()).isEqualTo(12);
        assertThat(settings.getOnDemandMinInterval()).isZero();
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("an unreadable stored value falls back to its default, with a warning")
    void invalidStoredValues() {
        Settings settings = Settings.fromProperties(stored(
                SettingsKeys.ENABLED, "yes",
                SettingsKeys.REFRESH_INTERVAL_HOURS, "ten",
                Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "soon"), warnings::add);

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getRefreshIntervalHours()).isEqualTo(Settings.DEFAULT_REFRESH_INTERVAL_HOURS);
        assertThat(settings.getOnDemandMinInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(warnings).hasSize(3).allSatisfy(w -> assertThat(w).contains("using"));
    }

    @Test
    @DisplayName("out-of-range numbers are clamped, with one warning each")
    void clampingWarns() {
        Settings settings = Settings.fromProperties(stored(
                SettingsKeys.REFRESH_INTERVAL_HOURS, "0",
                SettingsKeys.BANNER_DISMISS_HOURS, "99999",
                Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "-5"), warnings::add);

        assertThat(settings.getRefreshIntervalHours()).isEqualTo(Settings.MIN_REFRESH_INTERVAL_HOURS);
        assertThat(settings.getBanner().getDismissHours()).isEqualTo(SettingsValidator.MAX_HOURS);
        assertThat(settings.getOnDemandMinInterval()).isZero();
        assertThat(warnings).hasSize(3);
        assertThat(warnings.get(0)).contains("refreshIntervalHours").contains("0").contains("1");
    }

    @Test
    @DisplayName("a typed number beyond the int range is clamped with a warning, never wrapped")
    void longBeyondIntRangeIsClamped() {
        // 4294967297 wraps to 1 as an int: within range, it would have been taken silently
        Settings settings = Settings.fromProperties(stored(
                SettingsKeys.REFRESH_INTERVAL_HOURS, 4_294_967_297L,
                Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "-4294967296"), warnings::add);

        assertThat(settings.getRefreshIntervalHours()).isEqualTo(Settings.MAX_REFRESH_INTERVAL_HOURS);
        assertThat(settings.getOnDemandMinInterval()).isZero();
        assertThat(warnings).containsExactly(
                "refreshIntervalHours 4294967297 is out of range (1 to 720): using 720",
                "onDemandMinIntervalSeconds -4294967296 is out of range (0 to 3600): using 0");
    }

    @Test
    @DisplayName("a multi-valued property (array or collection, from a typed .cfg) reads as its values joined")
    void multiValuedProperties() {
        Settings fromArray = Settings.fromProperties(stored(SettingsKeys.EXTRA_FEED_URLS,
                new String[] {"https://a.example.org/f.json", "https://b.example.org/f.json"}), warnings::add);
        Settings fromList = Settings.fromProperties(stored(SettingsKeys.EXTRA_FEED_URLS,
                List.of("https://a.example.org/f.json", "https://b.example.org/f.json")), warnings::add);

        assertThat(fromArray.getExtraFeedUrls()).containsExactly("https://a.example.org/f.json", "https://b.example.org/f.json");
        assertThat(fromList.getExtraFeedUrls()).isEqualTo(fromArray.getExtraFeedUrls());
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("at most 10 extra feed URLs are kept, with a warning")
    void extraFeedUrlsAreCapped() {
        String urls = IntStream.range(0, 12).mapToObj(i -> "https://f" + i + ".example.org/f.json")
                .collect(Collectors.joining(","));

        Settings settings = Settings.fromProperties(stored(SettingsKeys.EXTRA_FEED_URLS, urls), warnings::add);

        assertThat(settings.getExtraFeedUrls()).hasSize(Settings.MAX_EXTRA_FEED_URLS)
                .startsWith("https://f0.example.org/f.json");
        assertThat(warnings).singleElement().satisfies(w -> assertThat(w).contains("extraFeedUrls").contains("10"));
    }

    @Test
    @DisplayName("the hidden on-demand interval is read from 0 to 3600 seconds")
    void onDemandInterval() {
        assertThat(Settings.fromProperties(stored(Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "3600"), warnings::add)
                .getOnDemandMinInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(Settings.fromProperties(stored(Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "7200"), warnings::add)
                .getOnDemandMinInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("only the check keys change the schedule, not the banner presentation")
    void sameSchedule() {
        Settings base = Settings.defaults();
        Settings colours = Settings.fromProperties(stored(SettingsKeys.BANNER_BACKGROUND_COLOR, "#0B3D91",
                SettingsKeys.BANNER_DISMISS_POLICY, "NEVER", Settings.ON_DEMAND_MIN_INTERVAL_SECONDS, "0"), warnings::add);

        assertThat(base.sameSchedule(colours)).isTrue();
        assertThat(base.sameSchedule(Settings.fromProperties(stored(SettingsKeys.REFRESH_INTERVAL_HOURS, "6"), warnings::add)))
                .isFalse();
        assertThat(base.sameSchedule(Settings.fromProperties(stored(SettingsKeys.ENABLED, "false"), warnings::add))).isFalse();
        assertThat(base.sameSchedule(Settings.fromProperties(stored(SettingsKeys.USE_FORGE_SETTINGS, "false"), warnings::add)))
                .isFalse();
        assertThat(base.sameSchedule(Settings.fromProperties(stored(SettingsKeys.DEFAULT_STORE_URL, ""), warnings::add)))
                .isFalse();
        assertThat(base.sameSchedule(Settings.fromProperties(stored(SettingsKeys.EXTRA_FEED_URLS, "https://x.org/f"),
                warnings::add))).isFalse();
    }

    @Test
    @DisplayName("the constructor splits extra feed URLs on commas and white space and clamps silently")
    void constructor() {
        assertThat(new Settings(true, 0, true, "", "").getRefreshIntervalHours()).isEqualTo(Settings.MIN_REFRESH_INTERVAL_HOURS);
        assertThat(new Settings(true, 24, true, "a, b\nc,,", null).getExtraFeedUrls()).containsExactly("a", "b", "c");
        assertThat(new Settings(true, 24, true, null, null).getDefaultStoreUrl()).isEmpty();
    }
}
