package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.BannerDecision;
import org.jahia.community.securityupdates.model.BannerPosition;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.service.DismissalTokens;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL view of a {@link BannerDecision}.
 */
@GraphQLName("SecurityUpdatesBanner")
@GraphQLDescription("Whether the security updates notice is shown in the Jahia UI")
public class GqlBanner {

    private final BannerDecision decision;
    private final String username;
    private final DismissalTokens tokens;

    public GqlBanner(BannerDecision decision) {
        this(decision, null, null);
    }

    /**
     * @param username the Jahia user the decision is shown to
     * @param tokens   the dismissal tokens of this server, or null when the service is not running (the banner can then
     *                 not be hidden)
     */
    public GqlBanner(BannerDecision decision, String username, DismissalTokens tokens) {
        this.decision = decision;
        this.username = username == null ? "" : username;
        this.tokens = tokens;
    }

    @GraphQLField
    @GraphQLName("dismissalTokens")
    @GraphQLNonNull
    @GraphQLDescription("One opaque token per dismissal key, in the same order: what the browser stores when the banner "
            + "is hidden, so that its storage names no component (a keyed hash under a secret of the server); empty "
            + "when the tokens are not available")
    public List<String> getDismissalTokens() {
        if (tokens == null) {
            return Collections.emptyList();
        }
        return decision.getDismissalKeys().stream().map(tokens::token).collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLName("userToken")
    @GraphQLNonNull
    @GraphQLDescription("An opaque token of the current user, which keys a dismissal stored in the browser per user "
            + "without the user name; empty when the tokens are not available")
    public String getUserToken() {
        return tokens == null ? "" : tokens.token("user:" + username);
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

    @GraphQLField
    @GraphQLName("position")
    @GraphQLNonNull
    @GraphQLDescription("Where the notice appears: TOP or BOTTOM (a full-width bar), TOP_RIGHT or BOTTOM_RIGHT (a small box in "
            + "that corner)")
    public BannerPosition getPosition() {
        return decision.getPosition();
    }
}
