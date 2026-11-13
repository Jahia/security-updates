package org.jahia.community.securityupdates.service;

import java.util.List;

/**
 * Keys of the configuration {@code org.jahia.community.securityupdates}, as written in the .cfg file.
 */
public final class SettingsKeys {

    public static final String ENABLED = "enabled";
    public static final String REFRESH_INTERVAL_HOURS = "refreshIntervalHours";
    public static final String USE_FORGE_SETTINGS = "useForgeSettings";
    public static final String DEFAULT_STORE_URL = "defaultStoreUrl";
    public static final String EXTRA_FEED_URLS = "extraFeedUrls";
    public static final String BANNER_ENABLED = "bannerEnabled";
    public static final String BANNER_MINIMUM_SEVERITY = "bannerMinimumSeverity";
    public static final String BANNER_INCLUDE_OWNER_REPORTS = "bannerIncludeOwnerReports";
    public static final String BANNER_BACKGROUND_COLOR = "bannerBackgroundColor";
    public static final String BANNER_TEXT_COLOR = "bannerTextColor";
    public static final String BANNER_DISMISS_POLICY = "bannerDismissPolicy";
    public static final String BANNER_DISMISS_HOURS = "bannerDismissHours";

    /** Every key, in the order of the .cfg file. */
    public static final List<String> ALL = List.of(ENABLED, REFRESH_INTERVAL_HOURS, USE_FORGE_SETTINGS, EXTRA_FEED_URLS,
            DEFAULT_STORE_URL, BANNER_ENABLED, BANNER_MINIMUM_SEVERITY, BANNER_INCLUDE_OWNER_REPORTS, BANNER_BACKGROUND_COLOR,
            BANNER_TEXT_COLOR, BANNER_DISMISS_POLICY, BANNER_DISMISS_HOURS);

    private SettingsKeys() {
    }
}
