package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.service.Colors;
import org.jahia.community.securityupdates.service.SettingsKeys;

import java.util.Map;

/**
 * GraphQL view of the module settings: one field per configuration key, as stored, and whether the current user may
 * change them.
 */
@GraphQLName("SecurityUpdatesSettings")
@GraphQLDescription("Settings of the security updates module (configuration org.jahia.community.securityupdates)")
public class GqlSettings {

    private final Map<String, String> values;
    private final boolean canEdit;

    /**
     * @param values  every key of {@link SettingsKeys#ALL}, in their normalised stored form
     * @param canEdit whether the current user may change the settings
     */
    public GqlSettings(Map<String, String> values, boolean canEdit) {
        this.values = Map.copyOf(values);
        this.canEdit = canEdit;
    }

    @GraphQLField
    @GraphQLName("canEdit")
    @GraphQLNonNull
    @GraphQLDescription("True when the current user may change the settings (permission admin, held by server administrators)")
    public boolean isCanEdit() {
        return canEdit;
    }

    private boolean bool(String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    @GraphQLField
    @GraphQLName("enabled")
    @GraphQLDescription("Whether the stores are checked")
    public boolean isEnabled() {
        return bool(SettingsKeys.ENABLED);
    }

    @GraphQLField
    @GraphQLName("refreshIntervalHours")
    @GraphQLDescription("Hours between two scheduled checks")
    public int getRefreshIntervalHours() {
        return Integer.parseInt(values.get(SettingsKeys.REFRESH_INTERVAL_HOURS));
    }

    @GraphQLField
    @GraphQLName("useForgeSettings")
    @GraphQLDescription("Whether the stores of the module manager are checked")
    public boolean isUseForgeSettings() {
        return bool(SettingsKeys.USE_FORGE_SETTINGS);
    }

    @GraphQLField
    @GraphQLName("defaultStoreUrl")
    @GraphQLNonNull
    @GraphQLDescription("Store checked when no store is configured in the module manager; empty for none")
    public String getDefaultStoreUrl() {
        return values.get(SettingsKeys.DEFAULT_STORE_URL);
    }

    @GraphQLField
    @GraphQLName("extraFeedUrls")
    @GraphQLNonNull
    @GraphQLDescription("URLs of additional security feeds, separated by commas")
    public String getExtraFeedUrls() {
        return values.get(SettingsKeys.EXTRA_FEED_URLS);
    }

    @GraphQLField
    @GraphQLName("bannerEnabled")
    @GraphQLDescription("Whether the notice is shown in the Jahia UI")
    public boolean isBannerEnabled() {
        return bool(SettingsKeys.BANNER_ENABLED);
    }

    @GraphQLField
    @GraphQLName("bannerMinimumSeverity")
    @GraphQLNonNull
    @GraphQLDescription("Lowest severity counted by the notice")
    public Severity getBannerMinimumSeverity() {
        return Severity.valueOf(values.get(SettingsKeys.BANNER_MINIMUM_SEVERITY));
    }

    @GraphQLField
    @GraphQLName("bannerIncludeOwnerReports")
    @GraphQLDescription("Whether owner-reported findings count in the notice")
    public boolean isBannerIncludeOwnerReports() {
        return bool(SettingsKeys.BANNER_INCLUDE_OWNER_REPORTS);
    }

    @GraphQLField
    @GraphQLName("bannerBackgroundColor")
    @GraphQLNonNull
    @GraphQLDescription("Background colour of the notice, #RRGGBB")
    public String getBannerBackgroundColor() {
        return values.get(SettingsKeys.BANNER_BACKGROUND_COLOR);
    }

    @GraphQLField
    @GraphQLName("bannerTextColor")
    @GraphQLNonNull
    @GraphQLDescription("Text colour of the notice, #RRGGBB")
    public String getBannerTextColor() {
        return values.get(SettingsKeys.BANNER_TEXT_COLOR);
    }

    @GraphQLField
    @GraphQLName("bannerDismissPolicy")
    @GraphQLNonNull
    @GraphQLDescription("How long a dismissed notice stays hidden")
    public DismissPolicy getBannerDismissPolicy() {
        return DismissPolicy.valueOf(values.get(SettingsKeys.BANNER_DISMISS_POLICY));
    }

    @GraphQLField
    @GraphQLName("bannerDismissHours")
    @GraphQLDescription("Hours a dismissal lasts with the HOURS policy")
    public int getBannerDismissHours() {
        return Integer.parseInt(values.get(SettingsKeys.BANNER_DISMISS_HOURS));
    }

    @GraphQLField
    @GraphQLName("contrastRatio")
    @GraphQLDescription("WCAG contrast ratio of the notice colours (at least 7)")
    public double getContrastRatio() {
        return Colors.contrastRatio(getBannerBackgroundColor(), getBannerTextColor());
    }
}
