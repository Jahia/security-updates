package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.FieldError;
import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.community.securityupdates.service.Settings;
import org.jahia.community.securityupdates.service.SettingsStore;
import org.jahia.community.securityupdates.service.SettingsValidator;
import org.jahia.community.securityupdates.service.ValidationResult;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Mutation namespace of the module.
 */
@GraphQLName("SecurityUpdatesMutation")
@GraphQLDescription("Security updates mutations")
public class SecurityUpdatesMutation {

    private static final Logger logger = LoggerFactory.getLogger(SecurityUpdatesMutation.class);

    @GraphQLField
    @GraphQLName("refresh")
    @GraphQLNonNull
    @GraphQLDescription("Checks the stores now (at most once every 30 seconds) and returns the resulting report")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSecurityReport refresh() {
        SecurityUpdateService service = ServiceLocator.service();
        return service == null ? GqlSecurityReport.serviceUnavailable() : new GqlSecurityReport(service.refresh());
    }

    @GraphQLField
    @GraphQLName("updateSettings")
    @GraphQLNonNull
    @GraphQLDescription("Validates and saves every setting at once; an invalid input is refused with per-field errors and "
            + "nothing is saved")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSettingsResult updateSettings(@GraphQLName("input") @GraphQLNonNull GqlSettingsInput input) {
        ValidationResult result = SettingsValidator.validate(input.toValues());
        if (!result.isValid()) {
            return GqlSettingsResult.refused(result.getErrors());
        }
        return save(result.getValues());
    }

    @GraphQLField
    @GraphQLName("resetSettings")
    @GraphQLNonNull
    @GraphQLDescription("Saves the default value of every setting")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSettingsResult resetSettings() {
        return save(Settings.defaults().toValues());
    }

    private static GqlSettingsResult save(Map<String, String> values) {
        SettingsStore store = ServiceLocator.store();
        try {
            if (store == null) {
                throw new IOException("Settings store not available");
            }
            store.save(values);
            return GqlSettingsResult.saved(values);
        } catch (IOException | RuntimeException e) {
            logger.error("Could not save the security updates settings", e);
            return GqlSettingsResult.refused(Collections.singletonList(new FieldError(null, "WRITE_FAILED",
                    "The settings could not be saved; see the Jahia log")));
        }
    }
}
