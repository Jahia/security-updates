package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL view of a {@link SecurityReport}.
 */
@GraphQLName("SecurityUpdatesReport")
@GraphQLDescription("Security updates of the installed Jahia components")
public class GqlSecurityReport {

    private final SecurityReport report;

    public GqlSecurityReport(SecurityReport report) {
        this.report = report;
    }

    static GqlSecurityReport serviceUnavailable() {
        return new GqlSecurityReport(new SecurityReport(ReportStatus.UNAVAILABLE, null, null, null,
                Collections.singletonList(new StoreError(null, StoreErrorKind.UNAVAILABLE,
                        "The security updates service is not running")),
                Collections.emptyList()));
    }

    @GraphQLField
    @GraphQLName("status")
    @GraphQLNonNull
    @GraphQLDescription("OK only when every store returned a feed this module understands")
    public ReportStatus getStatus() {
        return report.getStatus();
    }

    @GraphQLField
    @GraphQLName("checkedAt")
    @GraphQLDescription("ISO-8601 time of the last check, null before the first one")
    public String getCheckedAt() {
        return iso(report.getCheckedAt());
    }

    @GraphQLField
    @GraphQLName("lastSuccess")
    @GraphQLDescription("ISO-8601 time of the last check where every store feed was read and understood")
    public String getLastSuccess() {
        return iso(report.getLastSuccess());
    }

    @GraphQLField
    @GraphQLName("jahiaVersion")
    @GraphQLDescription("The running Jahia version")
    public String getJahiaVersion() {
        return report.getJahiaVersion();
    }

    @GraphQLField
    @GraphQLName("errors")
    @GraphQLNonNull
    @GraphQLDescription("Problems met during the last check, per store")
    public List<GqlStoreError> getErrors() {
        return report.getErrors().stream().map(GqlStoreError::new).collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLName("findings")
    @GraphQLNonNull
    @GraphQLDescription("One entry per installed component and source with security updates, most severe first")
    public List<GqlFinding> getFindings() {
        return report.getFindings().stream().map(GqlFinding::new).collect(Collectors.toList());
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /**
     * GraphQL view of a {@link StoreError}.
     */
    @GraphQLName("SecurityUpdatesError")
    @GraphQLDescription("A problem met during the check")
    public static class GqlStoreError {

        private final StoreError error;

        public GqlStoreError(StoreError error) {
            this.error = error;
        }

        @GraphQLField
        @GraphQLName("storeUrl")
        @GraphQLDescription("The store concerned, null when no store is concerned")
        public String getStoreUrl() {
            return error.getStoreUrl();
        }

        @GraphQLField
        @GraphQLName("kind")
        @GraphQLNonNull
        @GraphQLDescription("Category of the problem")
        public StoreErrorKind getKind() {
            return error.getKind();
        }

        @GraphQLField
        @GraphQLName("message")
        @GraphQLDescription("Description of the problem, in English")
        public String getMessage() {
            return error.getMessage();
        }
    }
}
