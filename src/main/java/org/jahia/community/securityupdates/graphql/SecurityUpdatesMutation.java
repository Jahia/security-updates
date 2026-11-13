package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;

/**
 * Mutation namespace of the module.
 */
@GraphQLName("SecurityUpdatesMutation")
@GraphQLDescription("Security updates mutations")
public class SecurityUpdatesMutation {

    @GraphQLField
    @GraphQLName("refresh")
    @GraphQLNonNull
    @GraphQLDescription("Checks the stores now (at most once every 30 seconds) and returns the resulting report")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSecurityReport refresh() {
        SecurityUpdateService service = ServiceLocator.service();
        return service == null ? GqlSecurityReport.serviceUnavailable() : new GqlSecurityReport(service.refresh());
    }
}
