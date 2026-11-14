package org.jahia.community.securityupdates.feed;

/**
 * The HTTP status codes this module tells apart.
 */
final class HttpStatus {

    static final int OK = 200;
    static final int UNAUTHORIZED = 401;
    static final int FORBIDDEN = 403;
    static final int NOT_FOUND = 404;
    static final int REDIRECT_MIN = 300;
    static final int REDIRECT_MAX = 399;

    private HttpStatus() {
    }

    static boolean isRedirect(int status) {
        return status >= REDIRECT_MIN && status <= REDIRECT_MAX;
    }

    static boolean isAuthFailure(int status) {
        return status == UNAUTHORIZED || status == FORBIDDEN;
    }
}
