package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;

/**
 * The single field this module adds to {@code Query}.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Security updates queries")
public final class SecurityUpdatesQueryExtension {

    private SecurityUpdatesQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("securityUpdates")
    @GraphQLDescription("Security updates of the installed Jahia components (requires adminTemplates)")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public static SecurityUpdatesQuery securityUpdates() {
        return new SecurityUpdatesQuery();
    }
}
