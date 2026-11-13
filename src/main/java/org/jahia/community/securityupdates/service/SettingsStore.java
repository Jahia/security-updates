package org.jahia.community.securityupdates.service;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reads and writes the module configuration through {@link ConfigurationAdmin}, so that it survives restarts.
 *
 * <p>The configuration is taken with location {@code "?"} (not bound to a bundle, as Jahia's own ConfigService
 * does) and updated with every key at once, keeping the properties already there, in particular
 * {@code felix.fileinstall.filename} and the hidden {@code onDemandMinIntervalSeconds}: Felix FileInstall then
 * writes the new values back into {@code karaf/etc/org.jahia.community.securityupdates.cfg}, keeping its comments
 * and its first line {@code # default configuration - won't be overridden}, so that redeploying the module does
 * not overwrite the file. Jahia shares {@code karaf/etc} files across a cluster, so every node gets the change. The
 * service picks the change up through its {@code @Modified} method.
 *
 * <p>When no configuration exists yet (no file in {@code karaf/etc}), the file is written first, starting with
 * the default-configuration marker, and FileInstall is pointed at it; the file is removed again when the update
 * fails. Each save is logged at INFO with the user and the keys changed (old -> new).
 */
@Component(service = SettingsStore.class)
public class SettingsStore {

    static final String FILEINSTALL_FILENAME = "felix.fileinstall.filename";
    static final String MARKER = "# default configuration - won't be overridden";
    private static final String KARAF_ETC = "karaf.etc";
    private static final String UNSET = "(unset)";
    private static final Logger logger = LoggerFactory.getLogger(SettingsStore.class);

    private final Supplier<String> karafEtc;
    private final Consumer<String> auditLog;
    private ConfigurationAdmin configurationAdmin;

    public SettingsStore() {
        this(() -> System.getProperty(KARAF_ETC), logger::info);
    }

    SettingsStore(Supplier<String> karafEtc, Consumer<String> auditLog) {
        this.karafEtc = karafEtc;
        this.auditLog = auditLog;
    }

    /** Static mandatory reference, set by Declarative Services before the component is used. */
    @Reference
    void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    /**
     * @return the stored value of every key of {@link SettingsKeys#ALL}, as the service applies them (a missing or
     * unreadable value reads as its default): unlike the settings of the running service, this includes a change
     * saved a moment ago and not yet delivered to it
     * @throws IOException when the configuration cannot be read
     */
    public synchronized Map<String, String> load() throws IOException {
        Dictionary<String, Object> properties = configuration().getProperties();
        Map<String, Object> values = new HashMap<>();
        if (properties != null) {
            for (String key : Collections.list(properties.keys())) {
                values.put(key, properties.get(key));
            }
        }
        return Settings.fromProperties(values, warning -> logger.debug("Stored settings: {}", warning)).toValues();
    }

    /**
     * @param values every key of {@link SettingsKeys#ALL}, already validated by {@link SettingsValidator}
     * @param user   the Jahia user saving the settings, for the audit log
     * @throws IllegalArgumentException when a key is missing (nothing is written)
     * @throws IOException              when the configuration cannot be written
     */
    public synchronized void save(Map<String, String> values, String user) throws IOException {
        for (String key : SettingsKeys.ALL) {
            if (values.get(key) == null) {
                throw new IllegalArgumentException("Missing setting " + key);
            }
        }
        Configuration configuration = configuration();
        Dictionary<String, Object> current = configuration.getProperties();
        MapDictionary next = new MapDictionary();
        Path created = null;
        if (current == null) {
            created = writeNewFile(values);
            next.put(FILEINSTALL_FILENAME, created.toUri().toString());
        } else {
            Enumeration<String> keys = current.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                next.put(key, current.get(key));
            }
        }
        for (String key : SettingsKeys.ALL) {
            next.put(key, values.get(key));
        }
        try {
            configuration.update(next);
        } catch (IOException | RuntimeException e) {
            if (created != null) {
                Files.deleteIfExists(created);
            }
            throw e;
        }
        auditLog.accept("Security updates settings saved by " + Settings.shown(user) + ": " + changes(current, values));
    }

    private Configuration configuration() throws IOException {
        ConfigurationAdmin admin = configurationAdmin;
        if (admin == null) {
            throw new IOException("ConfigurationAdmin not available");
        }
        return admin.getConfiguration(SecurityUpdateService.PID, "?");
    }

    /** The keys whose value changes, as {@code key: old -> new}. The values are not secret (no credential). */
    private static String changes(Dictionary<String, Object> current, Map<String, String> values) {
        List<String> changes = new ArrayList<>();
        for (String key : SettingsKeys.ALL) {
            Object old = current == null ? null : current.get(key);
            String oldValue = old == null ? UNSET : String.valueOf(old);
            if (!oldValue.equals(values.get(key))) {
                changes.add(key + ": " + logged(oldValue) + " -> " + logged(values.get(key)));
            }
        }
        return changes.isEmpty() ? "no change" : String.join("; ", changes);
    }

    /** A value as it may appear in a log line: control characters replaced, so that no line can be forged. */
    private static String logged(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(Character.isISOControl(c) ? '?' : c);
        }
        return sb.toString();
    }

    /** Writes a new .cfg file (temporary file then atomic move), starting with the default-configuration marker. */
    private Path writeNewFile(Map<String, String> values) throws IOException {
        String etc = karafEtc.get();
        if (etc == null || etc.trim().isEmpty()) {
            throw new IOException("karaf.etc is not set");
        }
        Path directory = Paths.get(etc.trim());
        Path file = directory.resolve(SecurityUpdateService.PID + ".cfg");
        Path temp = Files.createTempFile(directory, SecurityUpdateService.PID, ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.ISO_8859_1)) {
                writer.write(MARKER);
                writer.newLine();
                for (String key : SettingsKeys.ALL) {
                    writer.write(key + "=" + escape(values.get(key)));
                    writer.newLine();
                }
            }
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                // as the other karaf/etc files; the file holds no secret
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-r--r--"));
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return file;
    }

    /**
     * Escapes a value for a .properties line. The values are validated (ASCII URLs, colours, numbers, words: no line
     * break); only a backslash needs escaping, and a '#' or ':' after the '=' is part of the value.
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\");
    }
}
