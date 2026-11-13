package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;

/**
 * Query namespace of the module.
 */
@GraphQLName("SecurityUpdatesQuery")
@GraphQLDescription("Security updates queries")
public class SecurityUpdatesQuery {

    @GraphQLField
    @GraphQLName("report")
    @GraphQLNonNull
    @GraphQLDescription("The latest security report (built by the scheduled check; see the refresh mutation)")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public GqlSecurityReport getReport() {
        SecurityUpdateService service = ServiceLocator.service();
        return service == null ? GqlSecurityReport.serviceUnavailable() : new GqlSecurityReport(service.getReport());
    }
}
