package org.jahia.community.securityupdates.feed;

/**
 * The stores configured in the module manager could not be read.
 */
public class ForgeSettingsException extends Exception {

    private static final long serialVersionUID = 1L;

    public ForgeSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
