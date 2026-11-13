package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.FieldError;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Outcome of a settings update: the saved settings, or the errors (and nothing written).
 */
@GraphQLName("SecurityUpdatesSettingsResult")
@GraphQLDescription("Outcome of a settings update")
public class GqlSettingsResult {

    private final Map<String, String> saved;
    private final List<FieldError> errors;

    private GqlSettingsResult(Map<String, String> saved, List<FieldError> errors) {
        this.saved = saved;
        this.errors = List.copyOf(errors);
    }

    static GqlSettingsResult saved(Map<String, String> values) {
        return new GqlSettingsResult(values, List.of());
    }

    static GqlSettingsResult refused(List<FieldError> errors) {
        return new GqlSettingsResult(null, errors);
    }

    @GraphQLField
    @GraphQLName("ok")
    @GraphQLDescription("True when the settings were saved")
    public boolean isOk() {
        return errors.isEmpty();
    }

    @GraphQLField
    @GraphQLName("errors")
    @GraphQLNonNull
    @GraphQLDescription("The refused settings; nothing is saved when there is any")
    public List<GqlFieldError> getErrors() {
        return errors.stream().map(GqlFieldError::new).collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLName("settings")
    @GraphQLDescription("The saved settings, normalised (use them as the new form values); null when nothing was saved")
    public GqlSettings getSettings() {
        return saved == null ? null : new GqlSettings(saved, true);
    }
}
