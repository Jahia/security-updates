package org.jahia.community.securityupdates.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Validated, immutable form of the configuration described by {@link SecurityUpdatesConfig}. The factories are lenient, as a configuration file
 * edited by hand must not stop the module: a value that cannot be read falls back to its default and a number out
 * of range is clamped, each with one warning (logged once per configuration change).
 */
public final class Settings {

    /**
     * Hidden key (not in the settings UI): seconds before an on-demand check may start, counted from the later of the
     * start and the end of the previous check, 0 to 3600.
     */
    public static final String ON_DEMAND_MIN_INTERVAL_SECONDS = "onDemandMinIntervalSeconds";
    static final int DEFAULT_REFRESH_INTERVAL_HOURS = 24;
    static final String DEFAULT_STORE_URL = "https://store.jahia.com";
    static final int MIN_REFRESH_INTERVAL_HOURS = 1;
    static final int MAX_REFRESH_INTERVAL_HOURS = 720;
    static final int DEFAULT_ON_DEMAND_MIN_INTERVAL_SECONDS = 30;
    static final int MAX_ON_DEMAND_MIN_INTERVAL_SECONDS = 3600;
    /** Most extra feed URLs read. */
    static final int MAX_EXTRA_FEED_URLS = 10;
    private static final String URL_SEPARATORS = "[,\\s]+";
    private static final String USING = "': using ";
    private static final int MAX_SHOWN_VALUE = 40;

    private final boolean enabled;
    private final int refreshIntervalHours;
    private final boolean useForgeSettings;
    private final List<String> extraFeedUrls;
    private final String defaultStoreUrl;
    private final BannerSettings banner;
    private final int onDemandMinIntervalSeconds;

    Settings(boolean enabled, int refreshIntervalHours, boolean useForgeSettings, String extraFeedUrls,
             String defaultStoreUrl) {
        this(enabled, refreshIntervalHours, useForgeSettings, extraFeedUrls, defaultStoreUrl, BannerSettings.defaults());
    }

    Settings(boolean enabled, int refreshIntervalHours, boolean useForgeSettings, String extraFeedUrls,
             String defaultStoreUrl, BannerSettings banner) {
        this(enabled, refreshIntervalHours, useForgeSettings, split(extraFeedUrls), defaultStoreUrl, banner,
                DEFAULT_ON_DEMAND_MIN_INTERVAL_SECONDS);
    }

    @SuppressWarnings("java:S107") // one argument per configuration key
    Settings(boolean enabled, int refreshIntervalHours, boolean useForgeSettings, List<String> extraFeedUrls,
             String defaultStoreUrl, BannerSettings banner, int onDemandMinIntervalSeconds) {
        this.banner = Objects.requireNonNull(banner, "banner");
        this.enabled = enabled;
        this.refreshIntervalHours = clamp(refreshIntervalHours, MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS);
        this.useForgeSettings = useForgeSettings;
        this.extraFeedUrls = List.copyOf(extraFeedUrls);
        this.defaultStoreUrl = defaultStoreUrl == null ? "" : defaultStoreUrl.trim();
        this.onDemandMinIntervalSeconds = clamp(onDemandMinIntervalSeconds, 0, MAX_ON_DEMAND_MIN_INTERVAL_SECONDS);
    }

    /**
     * The settings of configuration properties as they are stored: strings from a .cfg file, or typed values (the
     * raw component properties of Declarative Services, read before any type conversion, so that a value that cannot
     * be read is noticed). A missing key takes its default without warning; unknown keys are ignored.
     */
    public static Settings fromProperties(Map<String, ?> properties, Consumer<String> warnings) {
        Reader reader = new Reader(properties, warnings);
        boolean enabled = reader.bool(SettingsKeys.ENABLED, true);
        int refreshIntervalHours = reader.number(SettingsKeys.REFRESH_INTERVAL_HOURS, DEFAULT_REFRESH_INTERVAL_HOURS,
                MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS);
        boolean useForgeSettings = reader.bool(SettingsKeys.USE_FORGE_SETTINGS, true);
        List<String> extraFeedUrls = cappedUrls(reader.text(SettingsKeys.EXTRA_FEED_URLS, ""), warnings);
        String defaultStoreUrl = reader.text(SettingsKeys.DEFAULT_STORE_URL, DEFAULT_STORE_URL);
        String[] colors = BannerSettings.parseColors(
                reader.text(SettingsKeys.BANNER_BACKGROUND_COLOR, BannerSettings.DEFAULT_BACKGROUND_COLOR),
                reader.text(SettingsKeys.BANNER_TEXT_COLOR, BannerSettings.DEFAULT_TEXT_COLOR), warnings);
        BannerSettings banner = new BannerSettings(reader.bool(SettingsKeys.BANNER_ENABLED, true),
                BannerSettings.parseMinimumSeverity(reader.text(SettingsKeys.BANNER_MINIMUM_SEVERITY, null), warnings),
                reader.bool(SettingsKeys.BANNER_INCLUDE_OWNER_REPORTS, true), colors[0], colors[1],
                BannerSettings.parseDismissPolicy(reader.text(SettingsKeys.BANNER_DISMISS_POLICY, null), warnings),
                reader.number(SettingsKeys.BANNER_DISMISS_HOURS, BannerSettings.DEFAULT_DISMISS_HOURS,
                        SettingsValidator.MIN_HOURS, SettingsValidator.MAX_HOURS));
        int onDemandSeconds = reader.number(ON_DEMAND_MIN_INTERVAL_SECONDS, DEFAULT_ON_DEMAND_MIN_INTERVAL_SECONDS, 0,
                MAX_ON_DEMAND_MIN_INTERVAL_SECONDS);
        return new Settings(enabled, refreshIntervalHours, useForgeSettings, extraFeedUrls, defaultStoreUrl, banner,
                onDemandSeconds);
    }

    public static Settings defaults() {
        return new Settings(true, DEFAULT_REFRESH_INTERVAL_HOURS, true, "", DEFAULT_STORE_URL);
    }

    public BannerSettings getBanner() {
        return banner;
    }

    /**
     * @return every key of {@link SettingsKeys#ALL} with its value as written in the .cfg file (the form the
     * settings UI edits and {@link SettingsStore} writes); the hidden on-demand interval is not part of it
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

    /**
     * @return true when {@code other} checks the same stores on the same schedule: a change of the banner keys or
     * of the on-demand interval does not restart the schedule
     */
    public boolean sameSchedule(Settings other) {
        return other != null && enabled == other.enabled && refreshIntervalHours == other.refreshIntervalHours
                && useForgeSettings == other.useForgeSettings && extraFeedUrls.equals(other.extraFeedUrls)
                && defaultStoreUrl.equals(other.defaultStoreUrl);
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

    /**
     * @return the shortest time between the later of the start and the end of the previous check and the start of an
     * on-demand check
     */
    public Duration getOnDemandMinInterval() {
        return Duration.ofSeconds(onDemandMinIntervalSeconds);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        for (String part : value.split(URL_SEPARATORS)) {
            if (!part.isEmpty()) {
                urls.add(part);
            }
        }
        return urls;
    }

    private static List<String> cappedUrls(String value, Consumer<String> warnings) {
        List<String> urls = split(value);
        if (urls.size() <= MAX_EXTRA_FEED_URLS) {
            return urls;
        }
        warnings.accept(SettingsKeys.EXTRA_FEED_URLS + " lists " + urls.size() + " URLs: only the first " + MAX_EXTRA_FEED_URLS
                + " are used");
        return urls.subList(0, MAX_EXTRA_FEED_URLS);
    }

    /** The configured value as it may appear in a log line: printable characters only, bounded. */
    static String shown(Object value) {
        String text = String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length() && sb.length() < MAX_SHOWN_VALUE; i++) {
            char c = text.charAt(i);
            sb.append(Character.isISOControl(c) ? '?' : c);
        }
        return sb.toString();
    }

    /** Lenient typed reads of configuration properties, with one warning per value not used as is. */
    private static final class Reader {
        private final Map<String, ?> properties;
        private final Consumer<String> warnings;

        Reader(Map<String, ?> properties, Consumer<String> warnings) {
            this.properties = properties;
            this.warnings = warnings;
        }

        String text(String key, String defaultValue) {
            Object value = properties.get(key);
            return value == null ? defaultValue : String.valueOf(value);
        }

        boolean bool(String key, boolean defaultValue) {
            Object value = properties.get(key);
            if (value == null) {
                return defaultValue;
            }
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(text) || "false".equals(text)) {
                return Boolean.parseBoolean(text);
            }
            warnings.accept("Invalid " + key + " '" + shown(value) + USING + defaultValue);
            return defaultValue;
        }

        int number(String key, int defaultValue, int min, int max) {
            Object value = properties.get(key);
            if (value == null) {
                return defaultValue;
            }
            int number;
            try {
                number = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                warnings.accept("Invalid " + key + " '" + shown(value) + USING + defaultValue);
                return defaultValue;
            }
            int clamped = clamp(number, min, max);
            if (clamped != number) {
                warnings.accept(key + " " + number + " is out of range (" + min + " to " + max + "): using " + clamped);
            }
            return clamped;
        }
    }
}
