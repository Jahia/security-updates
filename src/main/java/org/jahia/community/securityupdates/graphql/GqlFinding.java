package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.Severity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL view of a {@link Finding}.
 */
@GraphQLName("SecurityUpdatesFinding")
@GraphQLDescription("An installed component with security updates")
public class GqlFinding {

    private final Finding finding;

    public GqlFinding(Finding finding) {
        this.finding = finding;
    }

    @GraphQLField
    @GraphQLName("component")
    @GraphQLNonNull
    @GraphQLDescription("Module name (bundle symbolic name), or jahia-core for the platform")
    public String getComponent() {
        return finding.getComponent();
    }

    @GraphQLField
    @GraphQLName("groupId")
    @GraphQLDescription("Maven groupId of the module, null for jahia-core")
    public String getGroupId() {
        return finding.getGroupId();
    }

    @GraphQLField
    @GraphQLName("installedVersion")
    @GraphQLDescription("The running version")
    public String getInstalledVersion() {
        return finding.getInstalledVersion();
    }

    @GraphQLField
    @GraphQLName("recommendedVersion")
    @GraphQLDescription("The smallest version that fixes every listed advisory")
    public String getRecommendedVersion() {
        return finding.getRecommendedVersion();
    }

    @GraphQLField
    @GraphQLName("compatibility")
    @GraphQLNonNull
    @GraphQLDescription("Whether the recommended version can be installed on the running Jahia")
    public Compatibility getCompatibility() {
        return finding.getCompatibility();
    }

    @GraphQLField
    @GraphQLName("requiredJahiaVersion")
    @GraphQLDescription("Minimum Jahia version of the recommended version, from the store catalogue")
    public String getRequiredJahiaVersion() {
        return finding.getRequiredJahiaVersion();
    }

    @GraphQLField
    @GraphQLName("severity")
    @GraphQLNonNull
    @GraphQLDescription("Highest severity of the listed advisories")
    public Severity getSeverity() {
        return finding.getSeverity();
    }

    @GraphQLField
    @GraphQLName("source")
    @GraphQLNonNull
    @GraphQLDescription("ADVISORY for Jahia advisories, OWNER_REPORTED for versions flagged by the module owner")
    public FindingSource getSource() {
        return finding.getSource();
    }

    @GraphQLField
    @GraphQLName("advisories")
    @GraphQLNonNull
    @GraphQLDescription("The advisories (or owner reports) concerning the installed version")
    public List<GqlAdvisory> getAdvisories() {
        return finding.getAdvisories().stream().map(GqlAdvisory::new).collect(Collectors.toList());
    }
}
