package org.jahia.community.securityupdates.service;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * Keeps the time of the last security email in {@code email-state.properties}, in the data area of the bundle of this
 * Jahia node (only the processing server sends the email). The file is written to a temporary file, then moved over
 * the old one. When it cannot be read or written, the time is kept in memory and a restart may send the email again.
 */
@Component(service = EmailState.class)
public class FileEmailState implements EmailState {

    static final String FILE_NAME = "email-state.properties";
    private static final String LAST_SENT_AT = "lastSentAt";
    private static final Logger logger = LoggerFactory.getLogger(FileEmailState.class);

    private Path file;
    private boolean loaded;
    private Instant lastSentAt;

    /** Declarative Services constructor: the file is set on activation. */
    public FileEmailState() {
    }

    /** @param file the state file, or null to keep the time in memory only */
    FileEmailState(Path file) {
        this.file = file;
    }

    @Activate
    void activate(BundleContext context) {
        java.io.File dataFile = context.getDataFile(FILE_NAME);
        synchronized (this) {
            file = dataFile == null ? null : dataFile.toPath();
            loaded = false;
        }
    }

    @Override
    public synchronized Optional<Instant> lastSentAt() {
        if (!loaded) {
            lastSentAt = read();
            loaded = true;
        }
        return Optional.ofNullable(lastSentAt);
    }

    @Override
    public synchronized void recordSent(Instant at) {
        lastSentAt = at;
        loaded = true;
        if (file == null) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty(LAST_SENT_AT, at.toString());
        Path temp = null;
        try {
            temp = Files.createTempFile(file.toAbsolutePath().getParent(), FILE_NAME, ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "Security updates: time of the last security email");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not save the time of the last security email ({}): a restart may send it again",
                    e.getClass().getSimpleName());
        } finally {
            deleteQuietly(temp);
        }
    }

    private Instant read() {
        if (file == null) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read the time of the last security email ({})", e.getClass().getSimpleName());
            return null;
        }
        String value = properties.getProperty(LAST_SENT_AT);
        try {
            return value == null ? null : Instant.parse(value.trim());
        } catch (DateTimeException e) {
            logger.warn("Ignoring an unreadable time of the last security email");
            return null;
        }
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
