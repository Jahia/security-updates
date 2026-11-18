package org.jahia.community.securityupdates.service;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * The dismissal tokens of this Jahia node: HMAC-SHA256 under a random 256-bit secret, cut to 128 bits (32 hexadecimal
 * characters). The secret is created on first use in {@code dismissal-secret}, in the data area of the bundle, readable
 * by its owner only where the file system has POSIX permissions, and kept across restarts. Each cluster node has its
 * own secret: a banner hidden on one node shows again on another. When the file cannot be read or written, the secret
 * lives in memory, with one WARN, and a hidden banner shows again after a restart.
 */
@Component(service = DismissalTokens.class)
public class FileDismissalTokens implements DismissalTokens {

    static final String FILE_NAME = "dismissal-secret";
    static final int SECRET_BYTES = 32;
    private static final int TOKEN_BYTES = 16;
    private static final String HMAC = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Logger logger = LoggerFactory.getLogger(FileDismissalTokens.class);

    private final SecureRandom random = new SecureRandom();
    private Path file;
    private byte[] secret;

    /** Declarative Services constructor: the file is set on activation. */
    public FileDismissalTokens() {
    }

    /** @param file the secret file, or null to keep the secret in memory only */
    FileDismissalTokens(Path file) {
        this.file = file;
    }

    @Activate
    void activate(BundleContext context) {
        java.io.File dataFile = context.getDataFile(FILE_NAME);
        synchronized (this) {
            file = dataFile == null ? null : dataFile.toPath();
            secret = null;
        }
    }

    @Override
    public String token(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret(), HMAC));
            byte[] digest = mac.doFinal(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[TOKEN_BYTES * 2];
            for (int i = 0; i < TOKEN_BYTES; i++) {
                hex[2 * i] = HEX[(digest[i] >> 4) & 0xF];
                hex[2 * i + 1] = HEX[digest[i] & 0xF];
            }
            return new String(hex);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private synchronized byte[] secret() {
        if (secret == null) {
            secret = load();
        }
        return secret;
    }

    private byte[] load() {
        if (file != null) {
            try {
                byte[] stored = Files.readAllBytes(file);
                if (stored.length == SECRET_BYTES) {
                    return stored;
                }
                logger.warn("The banner dismissal secret is not valid: a new one is created, and hidden banners show again");
            } catch (NoSuchFileException e) {
                // first use: created below
            } catch (IOException e) {
                logger.warn("Could not read the banner dismissal secret ({}): a new one is created", e.getClass().getSimpleName());
            }
        }
        byte[] created = new byte[SECRET_BYTES];
        random.nextBytes(created);
        store(created);
        return created;
    }

    private void store(byte[] created) {
        if (file == null) {
            logger.warn("No data area for the banner dismissal secret: it is kept in memory, and hidden banners show again "
                    + "after a restart");
            return;
        }
        Path temp = null;
        try {
            Path dir = file.toAbsolutePath().getParent();
            temp = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                    ? Files.createTempFile(dir, FILE_NAME, ".tmp", ownerOnly())
                    : Files.createTempFile(dir, FILE_NAME, ".tmp");
            Files.write(temp, created);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not save the banner dismissal secret ({}): it is kept in memory, and hidden banners show "
                    + "again after a restart", e.getClass().getSimpleName());
        } finally {
            deleteQuietly(temp);
        }
    }

    private static FileAttribute<?> ownerOnly() {
        return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            logger.debug("Could not delete {}", temp, e);
        }
    }
}
