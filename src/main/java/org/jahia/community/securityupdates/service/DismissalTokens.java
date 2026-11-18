package org.jahia.community.securityupdates.service;

/**
 * Turns a banner dismissal key, or a user name, into an opaque token that only this server can compute, so that the
 * browser storage of a hidden banner cannot be read back into component names or user names (SECUPD-SEC-002).
 */
@FunctionalInterface
public interface DismissalTokens {

    /**
     * @param value a dismissal key ({@code <component>|<advisory id>}) or {@code user:<user name>}
     * @return its token: a keyed hash (HMAC) under a secret of the server, in lower-case hexadecimal
     */
    String token(String value);
}
