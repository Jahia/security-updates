package org.jahia.community.securityupdates.service;

/**
 * A setting refused by {@link SettingsValidator}: the key, a stable code for the UI, and an English message.
 */
public final class FieldError {

    private final String field;
    private final String code;
    private final String message;

    public FieldError(String field, String code, String message) {
        this.field = field;
        this.code = code;
        this.message = message;
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
