package org.jahia.community.securityupdates.feed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads JSON fixtures from {@code src/test/resources/feeds}. */
final class Fixtures {

    private Fixtures() {
    }

    static String read(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/feeds/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
