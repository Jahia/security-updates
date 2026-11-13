package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.FieldError;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL view of a refused setting.
 */
@GraphQLName("SecurityUpdatesFieldError")
@GraphQLDescription("A setting that was refused")
public class GqlFieldError {

    private final FieldError error;

    public GqlFieldError(FieldError error) {
        this.error = error;
    }

    @GraphQLField
    @GraphQLName("field")
    @GraphQLDescription("The configuration key, null for an error that concerns no single field")
    public String getField() {
        return error.getField();
    }

    @GraphQLField
    @GraphQLName("code")
    @GraphQLNonNull
    @GraphQLDescription("MISSING, INVALID_BOOLEAN, INVALID_NUMBER, OUT_OF_RANGE, INVALID_URL, INVALID_VALUE, INVALID_COLOR, "
            + "LOW_CONTRAST or WRITE_FAILED")
    public String getCode() {
        return error.getCode();
    }

    @GraphQLField
    @GraphQLName("message")
    @GraphQLNonNull
    @GraphQLDescription("Description of the problem, in English")
    public String getMessage() {
        return error.getMessage();
    }

    @GraphQLField
    @GraphQLName("suggestions")
    @GraphQLNonNull
    @GraphQLDescription("Values that would fix the error (for LOW_CONTRAST: a text colour and a background colour), "
            + "possibly none")
    public List<GqlSuggestion> getSuggestions() {
        return error.getSuggestions().stream().map(GqlSuggestion::new).collect(Collectors.toList());
    }
}
