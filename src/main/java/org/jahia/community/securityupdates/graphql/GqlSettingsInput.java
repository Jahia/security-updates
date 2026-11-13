package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.SettingsKeys;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full set of settings submitted by the settings UI. Every field is required, so that nothing is ever written
 * partially; the values are validated server-side ({@code SettingsValidator}), which is why the enumerations are
 * plain strings here. graphql-java-annotations prefixes input types with "Input": the schema type is
 * {@code InputSecurityUpdatesSettingsValues}.
 */
@GraphQLName("SecurityUpdatesSettingsValues")
@GraphQLDescription("Full set of security updates settings to save")
public class GqlSettingsInput {
    private final Boolean enabled;
    private final Integer refreshIntervalHours;
    private final Boolean useForgeSettings;
    private final String defaultStoreUrl;
    private final String extraFeedUrls;
    private final Boolean bannerEnabled;
    private final String bannerMinimumSeverity;
    private final Boolean bannerIncludeOwnerReports;
    private final String bannerBackgroundColor;
    private final String bannerTextColor;
    private final String bannerDismissPolicy;
    private final Integer bannerDismissHours;

    @SuppressWarnings("java:S107") // one argument per configuration key
    public GqlSettingsInput(
            @GraphQLName("enabled") @GraphQLNonNull @GraphQLDescription("Whether the stores are checked") Boolean enabled,
            @GraphQLName("refreshIntervalHours") @GraphQLNonNull @GraphQLDescription("Hours between two scheduled checks, 1 to 720") Integer refreshIntervalHours,
            @GraphQLName("useForgeSettings") @GraphQLNonNull @GraphQLDescription("Whether the stores of the module manager are checked") Boolean useForgeSettings,
            @GraphQLName("defaultStoreUrl") @GraphQLNonNull @GraphQLDescription("Absolute http(s) URL of the default store, or empty") String defaultStoreUrl,
            @GraphQLName("extraFeedUrls") @GraphQLNonNull @GraphQLDescription("Comma-separated absolute http(s) URLs of additional security feeds") String extraFeedUrls,
            @GraphQLName("bannerEnabled") @GraphQLNonNull @GraphQLDescription("Whether the notice is shown in the Jahia UI") Boolean bannerEnabled,
            @GraphQLName("bannerMinimumSeverity") @GraphQLNonNull @GraphQLDescription("LOW, MEDIUM, HIGH or CRITICAL") String bannerMinimumSeverity,
            @GraphQLName("bannerIncludeOwnerReports") @GraphQLNonNull @GraphQLDescription("Whether owner-reported findings count in the notice") Boolean bannerIncludeOwnerReports,
            @GraphQLName("bannerBackgroundColor") @GraphQLNonNull @GraphQLDescription("#RRGGBB, at least 7:1 against the text colour") String bannerBackgroundColor,
            @GraphQLName("bannerTextColor") @GraphQLNonNull @GraphQLDescription("#RRGGBB, at least 7:1 against the background colour") String bannerTextColor,
            @GraphQLName("bannerDismissPolicy") @GraphQLNonNull @GraphQLDescription("SESSION, HOURS, UNTIL_CHANGE or NEVER") String bannerDismissPolicy,
            @GraphQLName("bannerDismissHours") @GraphQLNonNull @GraphQLDescription("Hours a dismissal lasts with HOURS, 1 to 720") Integer bannerDismissHours) {
        this.enabled = enabled;
        this.refreshIntervalHours = refreshIntervalHours;
        this.useForgeSettings = useForgeSettings;
        this.defaultStoreUrl = defaultStoreUrl;
        this.extraFeedUrls = extraFeedUrls;
        this.bannerEnabled = bannerEnabled;
        this.bannerMinimumSeverity = bannerMinimumSeverity;
        this.bannerIncludeOwnerReports = bannerIncludeOwnerReports;
        this.bannerBackgroundColor = bannerBackgroundColor;
        this.bannerTextColor = bannerTextColor;
        this.bannerDismissPolicy = bannerDismissPolicy;
        this.bannerDismissHours = bannerDismissHours;
    }

    /** @return the submitted values as configuration strings (a null value is left out) */
    public Map<String, String> toValues() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, SettingsKeys.ENABLED, enabled);
        put(values, SettingsKeys.REFRESH_INTERVAL_HOURS, refreshIntervalHours);
        put(values, SettingsKeys.USE_FORGE_SETTINGS, useForgeSettings);
        put(values, SettingsKeys.DEFAULT_STORE_URL, defaultStoreUrl);
        put(values, SettingsKeys.EXTRA_FEED_URLS, extraFeedUrls);
        put(values, SettingsKeys.BANNER_ENABLED, bannerEnabled);
        put(values, SettingsKeys.BANNER_MINIMUM_SEVERITY, bannerMinimumSeverity);
        put(values, SettingsKeys.BANNER_INCLUDE_OWNER_REPORTS, bannerIncludeOwnerReports);
        put(values, SettingsKeys.BANNER_BACKGROUND_COLOR, bannerBackgroundColor);
        put(values, SettingsKeys.BANNER_TEXT_COLOR, bannerTextColor);
        put(values, SettingsKeys.BANNER_DISMISS_POLICY, bannerDismissPolicy);
        put(values, SettingsKeys.BANNER_DISMISS_HOURS, bannerDismissHours);
        return values;
    }

    private static void put(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, String.valueOf(value));
        }
    }

    @GraphQLField
    @GraphQLName("enabled")
    @GraphQLNonNull
    @GraphQLDescription("Whether the stores are checked")
    public Boolean getEnabled() {
        return enabled;
    }

    @GraphQLField
    @GraphQLName("refreshIntervalHours")
    @GraphQLNonNull
    @GraphQLDescription("Hours between two scheduled checks, 1 to 720")
    public Integer getRefreshIntervalHours() {
        return refreshIntervalHours;
    }

    @GraphQLField
    @GraphQLName("useForgeSettings")
    @GraphQLNonNull
    @GraphQLDescription("Whether the stores of the module manager are checked")
    public Boolean getUseForgeSettings() {
        return useForgeSettings;
    }

    @GraphQLField
    @GraphQLName("defaultStoreUrl")
    @GraphQLNonNull
    @GraphQLDescription("Absolute http(s) URL of the default store, or empty")
    public String getDefaultStoreUrl() {
        return defaultStoreUrl;
    }

    @GraphQLField
    @GraphQLName("extraFeedUrls")
    @GraphQLNonNull
    @GraphQLDescription("Comma-separated absolute http(s) URLs of additional security feeds")
    public String getExtraFeedUrls() {
        return extraFeedUrls;
    }

    @GraphQLField
    @GraphQLName("bannerEnabled")
    @GraphQLNonNull
    @GraphQLDescription("Whether the notice is shown in the Jahia UI")
    public Boolean getBannerEnabled() {
        return bannerEnabled;
    }

    @GraphQLField
    @GraphQLName("bannerMinimumSeverity")
    @GraphQLNonNull
    @GraphQLDescription("LOW, MEDIUM, HIGH or CRITICAL")
    public String getBannerMinimumSeverity() {
        return bannerMinimumSeverity;
    }

    @GraphQLField
    @GraphQLName("bannerIncludeOwnerReports")
    @GraphQLNonNull
    @GraphQLDescription("Whether owner-reported findings count in the notice")
    public Boolean getBannerIncludeOwnerReports() {
        return bannerIncludeOwnerReports;
    }

    @GraphQLField
    @GraphQLName("bannerBackgroundColor")
    @GraphQLNonNull
    @GraphQLDescription("#RRGGBB, at least 7:1 against the text colour")
    public String getBannerBackgroundColor() {
        return bannerBackgroundColor;
    }

    @GraphQLField
    @GraphQLName("bannerTextColor")
    @GraphQLNonNull
    @GraphQLDescription("#RRGGBB, at least 7:1 against the background colour")
    public String getBannerTextColor() {
        return bannerTextColor;
    }

    @GraphQLField
    @GraphQLName("bannerDismissPolicy")
    @GraphQLNonNull
    @GraphQLDescription("SESSION, HOURS, UNTIL_CHANGE or NEVER")
    public String getBannerDismissPolicy() {
        return bannerDismissPolicy;
    }

    @GraphQLField
    @GraphQLName("bannerDismissHours")
    @GraphQLNonNull
    @GraphQLDescription("Hours a dismissal lasts with HOURS, 1 to 720")
    public Integer getBannerDismissHours() {
        return bannerDismissHours;
    }
}
