package org.jahia.community.securityupdates.graphql;

/**
 * Permissions of the API.
 * <ul>
 *     <li>{@link #ADMIN_TEMPLATES} (module management) reads the report, the banner and the settings and runs a
 *     check.</li>
 *     <li>{@link #ADMIN} (server administration) changes the settings. In Jahia's permission tree,
 *     {@code adminTemplates} is a child of {@code admin} (module manager {@code permissions.xml}): the
 *     server-administrator role, which holds {@code admin}, has both; the web-designer role, which holds only
 *     {@code adminTemplates}, cannot change the settings (root-roles.xml).</li>
 * </ul>
 */
final class Permissions {

    static final String ADMIN_TEMPLATES = "adminTemplates";
    static final String ADMIN = "admin";

    private Permissions() {
    }
}
