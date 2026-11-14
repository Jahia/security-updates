package org.jahia.community.securityupdates.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of {@link SettingsValidator#validate(Map)}: the normalised values when everything is valid, otherwise
 * the errors (and no value at all, so that nothing can be written partially).
 */
public final class ValidationResult {

    private final Map<String, String> values;
    private final List<FieldError> errors;

    ValidationResult(Map<String, String> values, List<FieldError> errors) {
        this.errors = List.copyOf(errors);
        this.values = errors.isEmpty() ? Collections.unmodifiableMap(new LinkedHashMap<>(values)) : Collections.emptyMap();
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** @return every key with its normalised value, or an empty map when the input is invalid */
    public Map<String, String> getValues() {
        return values;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
