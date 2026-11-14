package org.jahia.community.securityupdates.feed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoundedReader")
class BoundedReaderTest {

    private static final long FAR = System.nanoTime() + 60_000_000_000L;

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsUtf8Body() throws IOException {
        assertThat(BoundedReader.read(stream("{\"t\":\"été\"}"), 100, FAR, false)).isEqualTo("{\"t\":\"été\"}");
    }

    @Test
    void readsBodyOfExactlyTheLimit() throws IOException {
        assertThat(BoundedReader.read(stream("12345"), 5, FAR, false)).isEqualTo("12345");
    }

    @Test
    @DisplayName("refuses a body over the limit")
    void refusesOversizedBody() {
        assertThatThrownBy(() -> BoundedReader.read(stream("123456"), 5, FAR, false))
                .isInstanceOf(ResponseTooLargeException.class);
    }

    @Test
    @DisplayName("truncates instead when asked (error bodies)")
    void truncates() throws IOException {
        assertThat(BoundedReader.read(stream("123456"), 5, FAR, true)).isEqualTo("12345");
    }

    @Test
    @DisplayName("gives up once the deadline has passed")
    void deadline() {
        long past = System.nanoTime() - 1;
        assertThatThrownBy(() -> BoundedReader.read(stream("x".repeat(100_000)), 1_000_000, past, false))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    void nullStreamIsEmpty() throws IOException {
        assertThat(BoundedReader.read(null, 5, FAR, false)).isEmpty();
    }

    @Test
    @DisplayName("a slow stream that crosses the deadline between two reads fails as a timeout at the next read")
    void slowStreamCrossesTheDeadline() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50);
        int[] reads = new int[1];
        InputStream slow = new InputStream() {
            @Override
            public int read() {
                return 'x';
            }

            @Override
            public int read(byte[] b, int off, int len) {
                reads[0]++;
                if (reads[0] == 2) {
                    // the second chunk arrives after the deadline; parks until then (no condition to wait for)
                    while (System.nanoTime() - deadline <= 0) {
                        java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10));
                    }
                }
                b[off] = 'x';
                return 1;
            }
        };

        assertThatThrownBy(() -> BoundedReader.read(slow, 1_000_000, deadline, false))
                .isInstanceOf(java.net.SocketTimeoutException.class)
                .hasMessage("Response read deadline exceeded");
        assertThat(reads[0]).isEqualTo(2);
    }
}
