package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Severity;

import java.util.List;

/**
 * GraphQL view of an {@link AdvisoryRef}. All text comes from the store feed and is untrusted.
 */
@GraphQLName("SecurityUpdatesAdvisory")
@GraphQLDescription("A security advisory, or a module owner report")
public class GqlAdvisory {

    private static final String HTTPS = "https://";

    private final AdvisoryRef advisory;

    public GqlAdvisory(AdvisoryRef advisory) {
        this.advisory = advisory;
    }

    @GraphQLField
    @GraphQLName("id")
    @GraphQLNonNull
    @GraphQLDescription("Advisory identifier, or the owner's references")
    public String getId() {
        return advisory.getId();
    }

    @GraphQLField
    @GraphQLName("title")
    @GraphQLDescription("Title of the advisory, or the owner's note")
    public String getTitle() {
        return advisory.getTitle();
    }

    @GraphQLField
    @GraphQLName("url")
    @GraphQLDescription("Link to the advisory; only https links are exposed")
    public String getUrl() {
        String url = advisory.getUrl();
        return url != null && url.startsWith(HTTPS) ? url : null;
    }

    @GraphQLField
    @GraphQLName("cves")
    @GraphQLNonNull
    @GraphQLDescription("CVE identifiers")
    public List<String> getCves() {
        return advisory.getCves();
    }

    @GraphQLField
    @GraphQLName("severity")
    @GraphQLNonNull
    @GraphQLDescription("Severity of this advisory")
    public Severity getSeverity() {
        return advisory.getSeverity();
    }
}
