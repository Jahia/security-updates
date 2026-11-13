package org.jahia.community.securityupdates.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

/**
 * Registers this bundle's GraphQL extensions with the DX GraphQL provider (classes annotated with
 * {@code @GraphQLTypeExtension} are found by bundle scan).
 */
@Component(immediate = true)
public class SecurityUpdatesGraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {
}
