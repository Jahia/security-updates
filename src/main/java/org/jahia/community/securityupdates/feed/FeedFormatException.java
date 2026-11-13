package org.jahia.community.securityupdates.feed;

/**
 * A store document that is not in a format this module understands. A report built without the document
 * must say so rather than claim that nothing needs updating.
 */
public class FeedFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public FeedFormatException(String message) {
        super(message);
    }

    public FeedFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
