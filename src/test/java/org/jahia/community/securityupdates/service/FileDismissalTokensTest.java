package org.jahia.community.securityupdates.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** SECUPD-SEC-002: the banner dismissal state is stored as tokens that only the server can compute. */
class FileDismissalTokensTest {

    private static final String KEY = "jahia-core|JSA-2099-0001";

    @TempDir
    Path dir;

    @Test
    @DisplayName("a token is 32 hexadecimal characters, stable for one secret, and survives a restart")
    void stable() {
        Path file = dir.resolve(FileDismissalTokens.FILE_NAME);
        String first = new FileDismissalTokens(file).token(KEY);

        assertThat(first).matches("[0-9a-f]{32}");
        assertThat(new FileDismissalTokens(file).token(KEY)).isEqualTo(first);
        assertThat(new FileDismissalTokens(file).token("jahia-core|JSA-2099-0002")).isNotEqualTo(first);
    }

    @Test
    @DisplayName("another installation (another secret) gives other tokens")
    void secretPerInstallation() {
        String one = new FileDismissalTokens(dir.resolve("a")).token(KEY);
        String other = new FileDismissalTokens(dir.resolve("b")).token(KEY);

        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("a token is not a plain hash of the key: a dictionary of keys does not invert it")
    void notAPlainHash() throws NoSuchAlgorithmException {
        String token = new FileDismissalTokens(dir.resolve(FileDismissalTokens.FILE_NAME)).token(KEY);
        StringBuilder sha256 = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(KEY.getBytes(StandardCharsets.UTF_8))) {
            sha256.append(String.format("%02x", b));
        }

        assertThat(sha256.toString()).doesNotStartWith(token);
    }

    @Test
    @DisplayName("the secret file is readable by its owner only, where the file system has POSIX permissions")
    void ownerOnly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path file = dir.resolve(FileDismissalTokens.FILE_NAME);

        new FileDismissalTokens(file).token(KEY);

        assertThat(Files.getPosixFilePermissions(file))
                .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    @Test
    @DisplayName("a secret file that is not a secret (too short) is replaced, and the new secret is kept")
    void corruptSecret() throws IOException {
        Path file = dir.resolve(FileDismissalTokens.FILE_NAME);
        Files.write(file, new byte[]{1, 2, 3});

        String token = new FileDismissalTokens(file).token(KEY);

        assertThat(Files.size(file)).isEqualTo(FileDismissalTokens.SECRET_BYTES);
        assertThat(new FileDismissalTokens(file).token(KEY)).isEqualTo(token);
    }

    @Test
    @DisplayName("without a data area the secret lives in memory: tokens still work, until a restart")
    void noDataArea() {
        FileDismissalTokens tokens = new FileDismissalTokens(null);

        assertThat(tokens.token(KEY)).matches("[0-9a-f]{32}").isEqualTo(tokens.token(KEY));
    }

    @Test
    @DisplayName("when the secret file cannot be written the secret lives in memory")
    void unwritable() throws IOException {
        Path blocked = dir.resolve("not-a-dir");
        Files.write(blocked, new byte[0]);
        FileDismissalTokens tokens = new FileDismissalTokens(blocked.resolve(FileDismissalTokens.FILE_NAME));

        assertThat(tokens.token(KEY)).matches("[0-9a-f]{32}").isEqualTo(tokens.token(KEY));
    }
}
