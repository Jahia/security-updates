package org.jahia.community.securityupdates.feed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a response body as UTF-8 with a size limit and a wall-clock deadline, so that neither a huge nor a
 * trickling response can hold the refresh thread or the heap.
 */
public final class BoundedReader {

    private static final int BUFFER = 8192;

    private BoundedReader() {
    }

    /**
     * @param in            the body; null reads as empty
     * @param maxBytes      the largest body accepted
     * @param deadlineNanos a {@link System#nanoTime()} value after which reading stops with an error
     * @param truncate      true to return the first {@code maxBytes} bytes of a longer body instead of failing
     * @throws ResponseTooLargeException when the body is longer than {@code maxBytes} and {@code truncate} is false
     * @throws IOException               when the deadline passes or the stream fails
     */
    public static String read(InputStream in, int maxBytes, long deadlineNanos, boolean truncate) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, BUFFER));
        byte[] buffer = new byte[BUFFER];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new IOException("Response read deadline exceeded");
            }
            int room = maxBytes - out.size();
            if (read > room) {
                if (!truncate) {
                    throw new ResponseTooLargeException(maxBytes);
                }
                out.write(buffer, 0, room);
                break;
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
