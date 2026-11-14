package org.jahia.community.securityupdates.graphql;

import graphql.annotations.AnnotationsSchemaCreator;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the module's schema with graphql-java-annotations (as the DX GraphQL provider does, under the same
 * {@code securityUpdates} namespace field) and validates the documents the admin UI sends against it.
 */
@DisplayName("Security updates GraphQL schema")
class SecurityUpdatesSchemaTest {

    private static final String REPORT_FIELDS = "fragment SecurityUpdatesReportFields on SecurityUpdatesReport {"
            + " status inProgress stale checkedAt lastSuccess nextCheckAt jahiaVersion"
            + " errors { storeUrl kind detail message }"
            + " findings { component groupId installedVersion recommendedVersion compatibility requiredJahiaVersion severity"
            + " source advisories { id title url cves severity storeHost } } }";
    private static final String SETTINGS_FIELDS = "fragment SecurityUpdatesSettingsFields on SecurityUpdatesSettings {"
            + " enabled refreshIntervalHours useForgeSettings defaultStoreUrl extraFeedUrls bannerEnabled bannerMinimumSeverity"
            + " bannerIncludeOwnerReports bannerBackgroundColor bannerTextColor bannerDismissPolicy bannerDismissHours"
            + " contrastRatio canEdit }";
    private static final String SETTINGS_RESULT = "{ ok errors { field code message suggestions { field value ratio } }"
            + " settings { ...SecurityUpdatesSettingsFields } }";

    private static GraphQLSchema schema;

    /** Stands for Jahia's Query type: the module adds one field to it. */
    @GraphQLName("Query")
    public static class QueryRoot {
        @GraphQLField
        @GraphQLName("securityUpdates")
        public SecurityUpdatesQuery securityUpdates() {
            return new SecurityUpdatesQuery();
        }
    }

    /** Stands for Jahia's Mutation type. */
    @GraphQLName("Mutation")
    public static class MutationRoot {
        @GraphQLField
        @GraphQLName("securityUpdates")
        public SecurityUpdatesMutation securityUpdates() {
            return new SecurityUpdatesMutation();
        }
    }

    @BeforeAll
    static void buildSchema() {
        schema = AnnotationsSchemaCreator.newAnnotationsSchema().query(QueryRoot.class).mutation(MutationRoot.class).build();
    }

    private static List<ValidationError> validate(String document) {
        return new Validator().validateDocument(schema, Parser.parse(document), Locale.ENGLISH);
    }

    @Test
    @DisplayName("the report query and the refresh mutation of the UI are valid")
    void reportDocuments() {
        assertThat(validate("query SecurityUpdatesReport { securityUpdates { report { ...SecurityUpdatesReportFields }"
                + " banner { visible count minimumSeverity } } } "
                + REPORT_FIELDS)).isEmpty();
        assertThat(validate("mutation SecurityUpdatesRefresh { securityUpdates { refresh { ...SecurityUpdatesReportFields } } } "
                + REPORT_FIELDS)).isEmpty();
    }

    @Test
    @DisplayName("the settings query and the update and reset mutations of the UI are valid, canEdit included")
    void settingsDocuments() {
        assertThat(validate("query SecurityUpdatesSettings { securityUpdates { settings { ...SecurityUpdatesSettingsFields } } } "
                + SETTINGS_FIELDS)).isEmpty();
        assertThat(validate("mutation SecurityUpdatesUpdateSettings($input: InputSecurityUpdatesSettingsValues!) {"
                + " securityUpdates { updateSettings(input: $input) " + SETTINGS_RESULT + " } } " + SETTINGS_FIELDS)).isEmpty();
        assertThat(validate("mutation SecurityUpdatesResetSettings { securityUpdates { resetSettings " + SETTINGS_RESULT
                + " } } " + SETTINGS_FIELDS)).isEmpty();
    }

    @Test
    @DisplayName("the banner query of the UI is valid (its jcr part belongs to Jahia's schema)")
    void bannerDocument() {
        assertThat(validate("query SecurityUpdatesBanner { securityUpdates { banner { visible count minimumSeverity components"
                + " dismissalKeys backgroundColor textColor dismissPolicy dismissHours username } } }")).isEmpty();
    }

    @Test
    @DisplayName("an unknown field is refused, so the checks above are meaningful")
    void unknownFieldIsRefused() {
        assertThat(validate("query { securityUpdates { report { notAField } } }")).isNotEmpty();
    }

    @Test
    @DisplayName("the enumerations carry the values the UI localises")
    void enumerations() {
        assertThat(schema.getType("SecurityUpdatesErrorDetail")).isNotNull();
        assertThat(((graphql.schema.GraphQLEnumType) schema.getType("SecurityUpdatesErrorDetail")).getValues())
                .extracting(graphql.schema.GraphQLEnumValueDefinition::getName)
                .containsExactly("NOT_HTTPS", "SSRF_BLOCKED", "TIMEOUT", "CONNECTION", "REDIRECT", "AUTH", "HTTP_STATUS",
                        "TOO_LARGE", "INVALID_JSON", "SCHEMA_UNSUPPORTED", "ENTRIES_SKIPPED", "FORGE_SETTINGS_UNREADABLE",
                        "CHECK_FAILED");
        assertThat(((graphql.schema.GraphQLEnumType) schema.getType("SecurityUpdatesErrorKind")).getValues())
                .extracting(graphql.schema.GraphQLEnumValueDefinition::getName).contains("STALE", "INVALID_URL");
    }
}
