package org.jahia.community.securityupdates.feed;

import java.io.IOException;

/**
 * A response body longer than the limit set for the document being read.
 */
public class ResponseTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    public ResponseTooLargeException(long limit) {
        super("Response larger than " + limit + " bytes");
    }
}
