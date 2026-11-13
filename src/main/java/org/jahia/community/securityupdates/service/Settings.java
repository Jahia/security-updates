package org.jahia.community.securityupdates.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    Settings(boolean enabled, int refreshIntervalHours, boolean useForgeSettings, String extraFeedUrls,
             String defaultStoreUrl) {
        this.enabled = enabled;
        this.refreshIntervalHours = Math.max(MIN_REFRESH_INTERVAL_HOURS,
                Math.min(MAX_REFRESH_INTERVAL_HOURS, refreshIntervalHours));
        this.useForgeSettings = useForgeSettings;
        this.extraFeedUrls = split(extraFeedUrls);
        this.defaultStoreUrl = defaultStoreUrl == null ? "" : defaultStoreUrl.trim();
    }

    static Settings from(SecurityUpdatesConfig config) {
        return new Settings(config.enabled(), config.refreshIntervalHours(), config.useForgeSettings(),
                config.extraFeedUrls(), config.defaultStoreUrl());
    }

    static Settings defaults() {
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
