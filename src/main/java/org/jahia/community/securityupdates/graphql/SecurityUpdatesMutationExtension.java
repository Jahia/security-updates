package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;

/**
 * The single field this module adds to {@code Mutation}.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Security updates mutations")
public final class SecurityUpdatesMutationExtension {

    private SecurityUpdatesMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("securityUpdates")
    @GraphQLDescription("Security updates mutations (requires adminTemplates)")
    @GraphQLRequiresPermission(Permissions.ADMIN_TEMPLATES)
    public static SecurityUpdatesMutation securityUpdates() {
        return new SecurityUpdatesMutation();
    }
}
