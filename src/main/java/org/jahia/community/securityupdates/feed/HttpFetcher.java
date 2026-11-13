package org.jahia.community.securityupdates.feed;

import java.io.IOException;
import java.util.Map;

/**
 * Performs HTTP GET requests for the store client. Implementations must not follow redirects (so that
 * credentials never reach another host), must apply connect and read timeouts, and must refuse a body
 * longer than {@code maxBytes}.
 */
public interface HttpFetcher {

    /**
     * @param url      an absolute http(s) URL
     * @param headers  request headers; may hold credentials, which must never be logged
     * @param maxBytes the largest body accepted for a 200 response
     * @return the status and body; for a status other than 200 the body is truncated to a few kilobytes
     * @throws ResponseTooLargeException when a 200 body is longer than {@code maxBytes}
     * @throws IOException               on any transport failure or timeout
     */
    HttpResult get(String url, Map<String, String> headers, int maxBytes) throws IOException;
}
