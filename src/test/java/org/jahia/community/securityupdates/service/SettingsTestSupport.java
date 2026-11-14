package org.jahia.community.securityupdates.service;

/** Gives tests of other packages access to package-private settings factories. */
public final class SettingsTestSupport {

    private SettingsTestSupport() {
    }

    public static Settings defaults() {
        return Settings.defaults();
    }
}
