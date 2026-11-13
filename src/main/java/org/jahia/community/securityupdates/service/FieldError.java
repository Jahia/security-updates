package org.jahia.community.securityupdates.service;

import java.util.Collections;
import java.util.List;

/**
 * A setting refused by {@link SettingsValidator}: the key, a stable code for the UI, and an English message.
 */
public final class FieldError {

    private final String field;
    private final String code;
    private final String message;
    private final List<SuggestedValue> suggestions;

    public FieldError(String field, String code, String message) {
        this(field, code, message, Collections.emptyList());
    }

    public FieldError(String field, String code, String message, List<SuggestedValue> suggestions) {
        this.field = field;
        this.code = code;
        this.message = message;
        this.suggestions = List.copyOf(suggestions);
    }

    /** A value that would fix the error, such as a colour reaching 7:1. */
    public static final class SuggestedValue {
        private final String field;
        private final String value;
        private final String ratio;

        public SuggestedValue(String field, String value, String ratio) {
            this.field = field;
            this.value = value;
            this.ratio = ratio;
        }

        public String getField() {
            return field;
        }

        public String getValue() {
            return value;
        }

        public String getRatio() {
            return ratio;
        }
    }

    /** @return the values that would fix the error (colours for LOW_CONTRAST), possibly none */
    public List<SuggestedValue> getSuggestions() {
        return suggestions;
    }

    /** @return the configuration key, or null for an error that concerns no single field */
    public String getField() {
        return field;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
