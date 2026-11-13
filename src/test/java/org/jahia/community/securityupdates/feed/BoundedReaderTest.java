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
}
