package org.jahia.community.securityupdates.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Validated, immutable form of {@link SecurityUpdatesConfig}.
 */
public final class Settings {

    static final int DEFAULT_REFRESH_INTERVAL_HOURS = 24;
    static final String DEFAULT_STORE_URL = "https://store.jahia.com";
    static final int MIN_REFRESH_INTERVAL_HOURS = 1;
    static final int MAX_REFRESH_INTERVAL_HOURS = 720;

    private final boolean enabled;
    private final int refreshIntervalHours;
    private final boolean useForgeSettings;
    private final List<String> extraFeedUrls;
    private final String defaultStoreUrl;
    private final BannerSettings banner;

    Settings(boolean enabled, int refreshIntervalHours, boolean useForgeSettings, String extraFeedUrls,
             String defaultStoreUrl) {
        this(enabled, refreshIntervalHours, useForgeSettings, extraFeedUrls, defaultStoreUrl, BannerSettings.defaults());
    }

    Settings(boolean enabled, int refreshIntervalHours, boolean useForgeSettings, String extraFeedUrls,
             String defaultStoreUrl, BannerSettings banner) {
        this.banner = banner;
        this.enabled = enabled;
        this.refreshIntervalHours = Math.max(MIN_REFRESH_INTERVAL_HOURS,
                Math.min(MAX_REFRESH_INTERVAL_HOURS, refreshIntervalHours));
        this.useForgeSettings = useForgeSettings;
        this.extraFeedUrls = split(extraFeedUrls);
        this.defaultStoreUrl = defaultStoreUrl == null ? "" : defaultStoreUrl.trim();
    }

    static Settings from(SecurityUpdatesConfig config, Consumer<String> warnings) {
        String[] colors = BannerSettings.parseColors(config.bannerBackgroundColor(), config.bannerTextColor(), warnings);
        BannerSettings banner = new BannerSettings(config.bannerEnabled(),
                BannerSettings.parseMinimumSeverity(config.bannerMinimumSeverity(), warnings),
                config.bannerIncludeOwnerReports(), colors[0], colors[1],
                BannerSettings.parseDismissPolicy(config.bannerDismissPolicy(), warnings), config.bannerDismissHours());
        return new Settings(config.enabled(), config.refreshIntervalHours(), config.useForgeSettings(),
                config.extraFeedUrls(), config.defaultStoreUrl(), banner);
    }

    public BannerSettings getBanner() {
        return banner;
    }

    /**
     * @return every configuration key with its value as written in the .cfg file (the form the settings UI edits
     * and {@link SettingsStore} writes)
     */
    public Map<String, String> toValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(SettingsKeys.ENABLED, String.valueOf(enabled));
        values.put(SettingsKeys.REFRESH_INTERVAL_HOURS, String.valueOf(refreshIntervalHours));
        values.put(SettingsKeys.USE_FORGE_SETTINGS, String.valueOf(useForgeSettings));
        values.put(SettingsKeys.EXTRA_FEED_URLS, String.join(", ", extraFeedUrls));
        values.put(SettingsKeys.DEFAULT_STORE_URL, defaultStoreUrl);
        values.put(SettingsKeys.BANNER_ENABLED, String.valueOf(banner.isEnabled()));
        values.put(SettingsKeys.BANNER_MINIMUM_SEVERITY, banner.getMinimumSeverity().name());
        values.put(SettingsKeys.BANNER_INCLUDE_OWNER_REPORTS, String.valueOf(banner.isIncludeOwnerReports()));
        values.put(SettingsKeys.BANNER_BACKGROUND_COLOR, banner.getBackgroundColor());
        values.put(SettingsKeys.BANNER_TEXT_COLOR, banner.getTextColor());
        values.put(SettingsKeys.BANNER_DISMISS_POLICY, banner.getDismissPolicy().name());
        values.put(SettingsKeys.BANNER_DISMISS_HOURS, String.valueOf(banner.getDismissHours()));
        return values;
    }

    public static Settings defaults() {
        return new Settings(true, DEFAULT_REFRESH_INTERVAL_HOURS, true, "", DEFAULT_STORE_URL);
    }

    private static List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        for (String part : value.split("[,\\s]+")) {
            if (!part.isEmpty()) {
                urls.add(part);
            }
        }
        return Collections.unmodifiableList(urls);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRefreshIntervalHours() {
        return refreshIntervalHours;
    }

    public boolean isUseForgeSettings() {
        return useForgeSettings;
    }

    public List<String> getExtraFeedUrls() {
        return extraFeedUrls;
    }

    public String getDefaultStoreUrl() {
        return defaultStoreUrl;
    }
}
