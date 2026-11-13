package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.ReportView;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL view of a {@link SecurityReport}.
 */
@GraphQLName("SecurityUpdatesReport")
@GraphQLDescription("Security updates of the installed Jahia components, as checked by this Jahia node")
public class GqlSecurityReport {

    private final ReportView view;
    private final SecurityReport report;

    public GqlSecurityReport(ReportView view) {
        this.view = view;
        this.report = view.getReport();
    }

    static GqlSecurityReport serviceUnavailable() {
        return new GqlSecurityReport(new ReportView(new SecurityReport(ReportStatus.UNAVAILABLE, null, null, null,
                Collections.singletonList(new StoreError(null, StoreErrorKind.UNAVAILABLE,
                        "The security updates service is not running")),
                Collections.emptyList()), false, null, false));
    }

    @GraphQLField
    @GraphQLName("status")
    @GraphQLNonNull
    @GraphQLDescription("OK only when every store returned a feed this module understands, every component was compared "
            + "and the report is not stale")
    public ReportStatus getStatus() {
        return report.getStatus();
    }

    @GraphQLField
    @GraphQLName("inProgress")
    @GraphQLNonNull
    @GraphQLDescription("True while a check runs on this Jahia node; poll the report until it is false")
    public boolean isInProgress() {
        return view.isInProgress();
    }

    @GraphQLField
    @GraphQLName("nextCheckAt")
    @GraphQLDescription("ISO-8601 time the next scheduled check is due, null when the check is disabled or not scheduled")
    public String getNextCheckAt() {
        return iso(view.getNextCheckAt());
    }

    @GraphQLField
    @GraphQLName("stale")
    @GraphQLNonNull
    @GraphQLDescription("True when the last check is older than twice the refresh interval: the status is then at best "
            + "PARTIAL and the errors hold a STALE entry")
    public boolean isStale() {
        return view.isStale();
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
    @GraphQLDescription("Problems met during the last check, per store; a STALE entry when the report is stale")
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
        @GraphQLName("detail")
        @GraphQLDescription("Precise cause of the problem, null when the kind says it all")
        public StoreErrorDetail getDetail() {
            return error.getDetail();
        }

        @GraphQLField
        @GraphQLName("message")
        @GraphQLDescription("Short technical description in English, made of status codes and this module's own words "
                + "(never text received from a store); the UI explains the kind and detail")
        public String getMessage() {
            return error.getMessage();
        }
    }
}
