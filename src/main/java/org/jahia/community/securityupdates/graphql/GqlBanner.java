package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.Severity;

import java.util.List;

/**
 * GraphQL view of a {@link BannerDecision}.
 */
@GraphQLName("SecurityUpdatesBanner")
@GraphQLDescription("Whether the security updates notice is shown in the Jahia UI")
public class GqlBanner {

    private final BannerDecision decision;

    public GqlBanner(BannerDecision decision) {
        this.decision = decision;
    }

    @GraphQLField
    @GraphQLName("visible")
    @GraphQLDescription("True when the notice is enabled and at least one finding qualifies")
    public boolean isVisible() {
        return decision.isVisible();
    }

    @GraphQLField
    @GraphQLName("count")
    @GraphQLDescription("Number of finding rows at or above the minimum severity, from the counted sources")
    public int getCount() {
        return decision.getCount();
    }

    @GraphQLField
    @GraphQLName("minimumSeverity")
    @GraphQLNonNull
    @GraphQLDescription("The configured minimum severity")
    public Severity getMinimumSeverity() {
        return decision.getMinimumSeverity();
    }

    @GraphQLField
    @GraphQLName("components")
    @GraphQLNonNull
    @GraphQLDescription("Component of each counted row, sorted (one entry per row), used as the dismissal key")
    public List<String> getComponents() {
        return decision.getComponents();
    }
}
