package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.community.securityupdates.service.SettingsStore;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Query namespace of the module.
 */
@GraphQLName("SecurityUpdatesQuery")
@GraphQLDescription("Security updates queries")
public class SecurityUpdatesQuery {

    private static final Logger logger = LoggerFactory.getLogger(SecurityUpdatesQuery.class);

    @GraphQLField
    @GraphQLName("report")
    @GraphQLNonNull
    @GraphQLDescription("The latest security report of this Jahia node (built by the scheduled check; see the refresh mutation)")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSecurityReport getReport() {
        return ServiceLocator.withService(service -> new GqlSecurityReport(service.getReportView()),
                GqlSecurityReport::serviceUnavailable);
    }

    @GraphQLField
    @GraphQLName("banner")
    @GraphQLNonNull
    @GraphQLDescription("Whether the banner is shown in the Jahia UI, decided from the current report and settings")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlBanner getBanner() {
        return new GqlBanner(ServiceLocator.withService(SecurityUpdateService::getBanner, BannerDecision::hidden),
                ServiceLocator.username());
    }

    @GraphQLField
    @GraphQLName("settings")
    @GraphQLNonNull
    @GraphQLDescription("The stored settings (a change just saved included), and whether the current user may change them")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSettings getSettings() {
        SettingsStore store = ServiceLocator.store();
        if (store == null) {
            throw new DataFetchingException("The security updates settings are not available");
        }
        Map<String, String> values;
        try {
            values = store.load();
        } catch (IOException e) {
            logger.error("Could not read the security updates settings", e);
            throw new DataFetchingException("The security updates settings could not be read; see the Jahia log");
        }
        return new GqlSettings(values, ServiceLocator.canEditSettings());
    }
}
