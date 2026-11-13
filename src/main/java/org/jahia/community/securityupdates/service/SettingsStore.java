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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Writes the module configuration through {@link ConfigurationAdmin}, so that it survives restarts.
 *
 * <p>The configuration is taken with location {@code "?"} (not bound to a bundle, as Jahia's own ConfigService
 * does) and updated with every key at once, keeping the properties already there, in particular
 * {@code felix.fileinstall.filename}: Felix FileInstall then writes the new values back into
 * {@code karaf/etc/org.jahia.community.securityupdates.cfg}, keeping its comments and its first line
 * {@code # default configuration - won't be overridden}, so that redeploying the module does not overwrite the
 * file. The service picks the change up through its {@code @Modified} method.
 *
 * <p>When no configuration exists yet (no file in {@code karaf/etc}), the file is written first, starting with
 * the default-configuration marker, and FileInstall is pointed at it.
 */
@Component(service = SettingsStore.class)
public class SettingsStore {

    static final String FILEINSTALL_FILENAME = "felix.fileinstall.filename";
    static final String MARKER = "# default configuration - won't be overridden";
    private static final String KARAF_ETC = "karaf.etc";
    private static final Logger logger = LoggerFactory.getLogger(SettingsStore.class);

    private final Supplier<String> karafEtc;
    private volatile ConfigurationAdmin configurationAdmin;

    public SettingsStore() {
        this(() -> System.getProperty(KARAF_ETC));
    }

    SettingsStore(Supplier<String> karafEtc) {
        this.karafEtc = karafEtc;
    }

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    /**
     * @param values every key of {@link SettingsKeys#ALL}, already validated by {@link SettingsValidator}
     * @throws IllegalArgumentException when a key is missing (nothing is written)
     * @throws IOException              when the configuration cannot be written
     */
    public void save(Map<String, String> values) throws IOException {
        for (String key : SettingsKeys.ALL) {
            if (values.get(key) == null) {
                throw new IllegalArgumentException("Missing setting " + key);
            }
        }
        ConfigurationAdmin admin = configurationAdmin;
        if (admin == null) {
            throw new IOException("ConfigurationAdmin not available");
        }
        Configuration configuration = admin.getConfiguration(SecurityUpdateService.PID, "?");
        Dictionary<String, Object> current = configuration.getProperties();
        Dictionary<String, Object> next = new Hashtable<>();
        if (current == null) {
            Path file = writeNewFile(values);
            next.put(FILEINSTALL_FILENAME, file.toUri().toString());
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
        configuration.update(next);
        logger.info("Security updates settings saved");
    }

    /** Writes a new .cfg file (temporary file then atomic move), starting with the default-configuration marker. */
    private Path writeNewFile(Map<String, String> values) throws IOException {
        String etc = karafEtc.get();
        if (etc == null || etc.isEmpty()) {
            throw new IOException("karaf.etc is not set");
        }
        Path directory = Paths.get(etc);
        Path file = directory.resolve(SecurityUpdateService.PID + ".cfg");
        Path temp = Files.createTempFile(directory, SecurityUpdateService.PID, ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.ISO_8859_1)) {
            writer.write(MARKER);
            writer.newLine();
            for (String key : SettingsKeys.ALL) {
                writer.write(key + "=" + escape(values.get(key)));
                writer.newLine();
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /**
     * Escapes a value for a .properties line. The values are validated (URLs, colours, numbers, words: no line
     * break); only a backslash needs escaping, and a '#' or ':' after the '=' is part of the value.
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\");
    }
}
