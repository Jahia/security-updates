package org.jahia.community.securityupdates.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * Precise cause of a {@link StoreError}, so that the UI can explain it in the user's language. The error
 * message stays a short English technical text made of this module's own words and status codes: it never
 * repeats text received from a store.
 */
@GraphQLName("SecurityUpdatesErrorDetail")
@GraphQLDescription("Precise cause of a problem met while building the security report")
public enum StoreErrorDetail {
    /** The URL is not an https URL: plain http is refused. */
    NOT_HTTPS,
    /** Jahia refused to connect to the address (outbound request screening, jahia.httpClient.allowedInternalHosts). */
    SSRF_BLOCKED,
    /** The connection or the response timed out. */
    TIMEOUT,
    /** The connection failed (DNS, TLS, refused, reset). */
    CONNECTION,
    /** The store answered with a redirect, which is not followed. */
    REDIRECT,
    /** The store answered 401 or 403: check the store credentials. */
    AUTH,
    /** The store answered with another error status. */
    HTTP_STATUS,
    /** The response is larger than the limit. */
    TOO_LARGE,
    /** The response is not a document of the expected structure. */
    INVALID_JSON,
    /** The feed declares a schemaVersion this module does not understand: a newer module may. */
    SCHEMA_UNSUPPORTED,
    /** Some entries of the document could not be understood and were ignored. */
    ENTRIES_SKIPPED,
    /** The stores of the module manager could not be read. */
    FORGE_SETTINGS_UNREADABLE,
    /** The check failed unexpectedly; see the Jahia log. */
    CHECK_FAILED
}
