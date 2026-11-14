package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;

import java.util.List;

/**
 * GraphQL view of a {@link BannerDecision}.
 */
@GraphQLName("SecurityUpdatesBanner")
@GraphQLDescription("Whether the security updates notice is shown in the Jahia UI")
public class GqlBanner {

    private final BannerDecision decision;
    private final String username;

    public GqlBanner(BannerDecision decision) {
        this(decision, null);
    }

    /**
     * @param username the Jahia user the decision is shown to, used by the browser to key a dismissal per user
     */
    public GqlBanner(BannerDecision decision, String username) {
        this.decision = decision;
        this.username = username == null ? "" : username;
    }

    @GraphQLField
    @GraphQLName("username")
    @GraphQLNonNull
    @GraphQLDescription("The current Jahia user, so that a dismissal stored in the browser is kept per user")
    public String getUsername() {
        return username;
    }

    @GraphQLField
    @GraphQLName("visible")
    @GraphQLDescription("True when the banner is enabled and at least one finding qualifies")
    public boolean isVisible() {
        return decision.isVisible();
    }

    @GraphQLField
    @GraphQLName("count")
    @GraphQLDescription("Number of distinct components with a finding at or above the minimum severity, from the counted sources")
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
    @GraphQLDescription("The distinct components counted, sorted")
    public List<String> getComponents() {
        return decision.getComponents();
    }

    @GraphQLField
    @GraphQLName("dismissalKeys")
    @GraphQLNonNull
    @GraphQLDescription("One key per counted advisory, <component>|<advisory id> (the owner report id for an owner report), "
            + "distinct and sorted: a dismissed banner comes back when a key is not among the dismissed ones")
    public List<String> getDismissalKeys() {
        return decision.getDismissalKeys();
    }

    @GraphQLField
    @GraphQLName("backgroundColor")
    @GraphQLNonNull
    @GraphQLDescription("Background colour, #RRGGBB, validated (at least 7:1 against the text colour)")
    public String getBackgroundColor() {
        return decision.getBackgroundColor();
    }

    @GraphQLField
    @GraphQLName("textColor")
    @GraphQLNonNull
    @GraphQLDescription("Text colour, #RRGGBB, validated")
    public String getTextColor() {
        return decision.getTextColor();
    }

    @GraphQLField
    @GraphQLName("dismissPolicy")
    @GraphQLNonNull
    @GraphQLDescription("How long a dismissed notice stays hidden")
    public DismissPolicy getDismissPolicy() {
        return decision.getDismissPolicy();
    }

    @GraphQLField
    @GraphQLName("dismissHours")
    @GraphQLDescription("Hours a dismissal lasts with the HOURS policy")
    public int getDismissHours() {
        return decision.getDismissHours();
    }
}
