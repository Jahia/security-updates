package org.jahia.community.securityupdates.model;

import java.util.Objects;

/**
 * A problem met while building a report, attached to the store it concerns (null when no store is concerned).
 */
public final class StoreError {

    private final String storeUrl;
    private final StoreErrorKind kind;
    private final String message;

    public StoreError(String storeUrl, StoreErrorKind kind, String message) {
        this.storeUrl = storeUrl;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = message;
    }

    public String getStoreUrl() {
        return storeUrl;
    }

    public StoreErrorKind getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return kind + " " + storeUrl + ": " + message;
    }
}
