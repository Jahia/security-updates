package org.jahia.community.securityupdates.service;

/** Gives tests of other packages access to package-private settings factories. */
public final class SettingsTestSupport {

    private SettingsTestSupport() {
    }

    public static Settings defaults() {
        return Settings.defaults();
    }

    /** @return the settings the service applies for the given stored values */
    public static Settings fromValues(java.util.Map<String, String> values) {
        return Settings.fromProperties(values, warning -> { });
    }
}
