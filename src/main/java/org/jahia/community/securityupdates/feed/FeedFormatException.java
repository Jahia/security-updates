package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.StoreErrorDetail;

/**
 * A store document that is not in a format this module understands. A report built without the document
 * must say so rather than claim that nothing needs updating. The message is made of this module's own words:
 * it never repeats a value read from the document.
 */
public class FeedFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    private final StoreErrorDetail detail;

    /**
     * @param detail {@link StoreErrorDetail#INVALID_JSON} or {@link StoreErrorDetail#SCHEMA_UNSUPPORTED}
     */
    public FeedFormatException(StoreErrorDetail detail, String message) {
        super(message);
        this.detail = detail;
    }

    public FeedFormatException(StoreErrorDetail detail, String message, Throwable cause) {
        super(message, cause);
        this.detail = detail;
    }

    public StoreErrorDetail getDetail() {
        return detail;
    }
}
