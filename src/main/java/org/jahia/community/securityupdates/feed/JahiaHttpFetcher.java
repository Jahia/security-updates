package org.jahia.community.securityupdates.feed;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jahia.services.notification.HttpClientService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@link HttpFetcher} over Jahia's {@link HttpClientService}, so that Jahia's proxy settings (and, on Jahia
 * versions that have it, its outbound request screening) apply. The status code is kept for every
 * response, which {@code HttpClientService.executeGet} does not do. Redirects are not followed and every
 * request is bounded in time and size.
 */
@Component(service = HttpFetcher.class)
public class JahiaHttpFetcher implements HttpFetcher {

    /** Bytes kept of the body of a response whose status is not 200 (enough for a JSON error). */
    static final int MAX_ERROR_BODY_BYTES = 4096;
    static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(10);
    static final Timeout CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(10);
    static final Timeout RESPONSE_TIMEOUT = Timeout.ofSeconds(30);
    /** Wall-clock bound on reading one response body. */
    static final long READ_DEADLINE_SECONDS = 60;
    private static final int SC_OK = 200;

    private volatile HttpClientService httpClientService;

    @Reference
    public void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers, int maxBytes) throws IOException {
        HttpClientService service = httpClientService;
        if (service == null) {
            throw new IOException("Jahia HTTP client service not available");
        }
        CloseableHttpClient client = service.getHttpClient(url);
        HttpGet request = new HttpGet(url);
        RequestConfig config = service.getRequestConfigBuilder(client)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setResponseTimeout(RESPONSE_TIMEOUT)
                .setRedirectsEnabled(false)
                .build();
        request.setConfig(config);
        headers.forEach(request::addHeader);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READ_DEADLINE_SECONDS);
        try (CloseableHttpResponse response = client.execute(request)) {
            int status = response.getCode();
            HttpEntity entity = response.getEntity();
            boolean ok = status == SC_OK;
            if (ok && entity != null && entity.getContentLength() > maxBytes) {
                throw new ResponseTooLargeException(maxBytes);
            }
            InputStream body = entity == null ? null : entity.getContent();
            String text = ok
                    ? BoundedReader.read(body, maxBytes, deadline, false)
                    : BoundedReader.read(body, MAX_ERROR_BODY_BYTES, deadline, true);
            return new HttpResult(status, text);
        }
    }
}
