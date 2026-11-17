package org.jahia.community.securityupdates.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FileEmailStateTest {

    private static final Instant SENT = Instant.parse("2026-11-13T10:00:00Z");

    @TempDir
    Path dir;

    @Test
    @DisplayName("no file means no email sent yet")
    void empty() {
        assertThat(new FileEmailState(dir.resolve("email-state.properties")).lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("the time of the last email survives a restart, and no temporary file is left")
    void survivesRestart() throws IOException {
        Path file = dir.resolve("email-state.properties");
        new FileEmailState(file).recordSent(SENT);

        assertThat(new FileEmailState(file).lastSentAt()).contains(SENT);
        try (var files = Files.list(dir)) {
            assertThat(files).containsExactly(file);
        }
    }

    @Test
    @DisplayName("an unreadable value reads as no email sent")
    void unreadable() throws IOException {
        Path file = dir.resolve("email-state.properties");
        Files.write(file, "lastSentAt=yesterday\n".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(new FileEmailState(file).lastSentAt()).isEmpty();
    }

    @Test
    @DisplayName("when the file cannot be written the time is still kept in memory")
    void writeFailureKeepsMemory() throws IOException {
        Path blocked = dir.resolve("not-a-dir");
        Files.write(blocked, new byte[0]);
        FileEmailState state = new FileEmailState(blocked.resolve("email-state.properties"));

        state.recordSent(SENT);

        assertThat(state.lastSentAt()).contains(SENT);
    }

    @Test
    @DisplayName("without a data area the time is kept in memory only")
    void noDataArea() {
        FileEmailState state = new FileEmailState(null);

        state.recordSent(SENT);

        assertThat(state.lastSentAt()).contains(SENT);
    }

    @Test
    // Regression guard: Files.newOutputStream passes this test too on JDK 17; FileOutputStream keeps it on any JDK.
    @DisplayName("an interrupt of the check thread (the module stops) does not lose the time of an email already sent")
    void interruptedThreadStillWrites() {
        Path file = dir.resolve("email-state.properties");
        try {
            Thread.currentThread().interrupt();
            new FileEmailState(file).recordSent(SENT);
        } finally {
            Thread.interrupted();
        }

        assertThat(new FileEmailState(file).lastSentAt()).contains(SENT);
    }
}
