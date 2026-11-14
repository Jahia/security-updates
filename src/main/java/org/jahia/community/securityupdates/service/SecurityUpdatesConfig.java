package org.jahia.community.securityupdates.service;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

/**
 * Configuration of the security updates check, PID {@code org.jahia.community.securityupdates}. Labels are
 * localised in {@code OSGI-INF/l10n/security-updates/config.properties}.
 */
@ObjectClassDefinition(
        id = SecurityUpdateService.PID,
        name = "%configName",
        description = "%configDesc",
        localization = "OSGI-INF/l10n/security-updates/config")
public @interface SecurityUpdatesConfig {

    @AttributeDefinition(name = "%enabled", description = "%enabled.desc")
    boolean enabled() default true;

    @AttributeDefinition(name = "%refreshIntervalHours", description = "%refreshIntervalHours.desc", min = "1", max = "720")
    int refreshIntervalHours() default Settings.DEFAULT_REFRESH_INTERVAL_HOURS;

    @AttributeDefinition(name = "%useForgeSettings", description = "%useForgeSettings.desc")
    boolean useForgeSettings() default true;

    @AttributeDefinition(name = "%extraFeedUrls", description = "%extraFeedUrls.desc", required = false)
    String extraFeedUrls() default "";

    @AttributeDefinition(name = "%defaultStoreUrl", description = "%defaultStoreUrl.desc", required = false)
    String defaultStoreUrl() default Settings.DEFAULT_STORE_URL;

    @AttributeDefinition(name = "%bannerEnabled", description = "%bannerEnabled.desc")
    boolean bannerEnabled() default true;

    @AttributeDefinition(name = "%bannerMinimumSeverity", description = "%bannerMinimumSeverity.desc", options = {
            @Option(label = "%severity.LOW", value = "LOW"),
            @Option(label = "%severity.MEDIUM", value = "MEDIUM"),
            @Option(label = "%severity.HIGH", value = "HIGH"),
            @Option(label = "%severity.CRITICAL", value = "CRITICAL")})
    String bannerMinimumSeverity() default "HIGH";

    @AttributeDefinition(name = "%bannerIncludeOwnerReports", description = "%bannerIncludeOwnerReports.desc")
    boolean bannerIncludeOwnerReports() default true;

    @AttributeDefinition(name = "%bannerBackgroundColor", description = "%bannerBackgroundColor.desc")
    String bannerBackgroundColor() default BannerSettings.DEFAULT_BACKGROUND_COLOR;

    @AttributeDefinition(name = "%bannerTextColor", description = "%bannerTextColor.desc")
    String bannerTextColor() default BannerSettings.DEFAULT_TEXT_COLOR;

    @AttributeDefinition(name = "%bannerDismissPolicy", description = "%bannerDismissPolicy.desc", options = {
            @Option(label = "%dismiss.SESSION", value = "SESSION"),
            @Option(label = "%dismiss.HOURS", value = "HOURS"),
            @Option(label = "%dismiss.UNTIL_CHANGE", value = "UNTIL_CHANGE"),
            @Option(label = "%dismiss.NEVER", value = "NEVER")})
    String bannerDismissPolicy() default "SESSION";

    @AttributeDefinition(name = "%bannerDismissHours", description = "%bannerDismissHours.desc", min = "1", max = "720")
    int bannerDismissHours() default BannerSettings.DEFAULT_DISMISS_HOURS;

    @AttributeDefinition(name = "%onDemandMinIntervalSeconds", description = "%onDemandMinIntervalSeconds.desc", min = "0",
            max = "3600")
    int onDemandMinIntervalSeconds() default Settings.DEFAULT_ON_DEMAND_MIN_INTERVAL_SECONDS;
}
