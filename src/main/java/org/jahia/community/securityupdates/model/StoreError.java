package org.jahia.community.securityupdates.model;

import java.util.Objects;

/**
 * A problem met while building a report, attached to the store it concerns (null when no store is concerned).
 */
public final class StoreError {

    private final String storeUrl;
    private final StoreErrorKind kind;
    private final StoreErrorDetail detail;
    private final String message;

    public StoreError(String storeUrl, StoreErrorKind kind, String message) {
        this(storeUrl, kind, null, message);
    }

    /**
     * @param storeUrl the store concerned, null when none
     * @param kind     the category, which decides whether the report can still be complete
     * @param detail   the precise cause, null when the kind says it all
     * @param message  a short English text of this module's own words; never text received from a store
     */
    public StoreError(String storeUrl, StoreErrorKind kind, StoreErrorDetail detail, String message) {
        this.storeUrl = storeUrl;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.detail = detail;
        this.message = message;
    }

    /** @return a copy with {@code suffix} appended to the message */
    public StoreError withMessageSuffix(String suffix) {
        return new StoreError(storeUrl, kind, detail, message + suffix);
    }

    public String getStoreUrl() {
        return storeUrl;
    }

    public StoreErrorKind getKind() {
        return kind;
    }

    public StoreErrorDetail getDetail() {
        return detail;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return kind + (detail == null ? "" : "/" + detail) + " " + storeUrl + ": " + message;
    }
}
