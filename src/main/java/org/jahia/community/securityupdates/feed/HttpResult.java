package org.jahia.community.securityupdates.feed;

/**
 * Status code and (bounded) body of an HTTP response.
 */
public final class HttpResult {

    private final int status;
    private final String body;

    public HttpResult(int status, String body) {
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
