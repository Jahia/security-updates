package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.securityupdates.service.FieldError;

/**
 * GraphQL view of a value that would fix a refused setting.
 */
@GraphQLName("SecurityUpdatesSuggestedValue")
@GraphQLDescription("A value that would fix a refused setting, such as a colour reaching the 7:1 contrast")
public class GqlSuggestion {

    private final FieldError.SuggestedValue suggestion;

    public GqlSuggestion(FieldError.SuggestedValue suggestion) {
        this.suggestion = suggestion;
    }

    @GraphQLField
    @GraphQLName("field")
    @GraphQLNonNull
    @GraphQLDescription("The configuration key the value is for")
    public String getField() {
        return suggestion.getField();
    }

    @GraphQLField
    @GraphQLName("value")
    @GraphQLNonNull
    @GraphQLDescription("The suggested value")
    public String getValue() {
        return suggestion.getValue();
    }

    @GraphQLField
    @GraphQLName("ratio")
    @GraphQLDescription("For a colour: the contrast ratio it gives against the other colour, two decimals rounded down")
    public String getRatio() {
        return suggestion.getRatio();
    }
}
