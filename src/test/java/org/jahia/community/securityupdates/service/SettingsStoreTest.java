package org.jahia.community.securityupdates.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SettingsStore")
class SettingsStoreTest {

    private ConfigurationAdmin configAdmin;
    private Configuration configuration;
    private SettingsStore store;
    private final List<String> audit = new ArrayList<>();

    @TempDir
    Path etc;

    @BeforeEach
    void setUp() throws IOException {
        configAdmin = mock(ConfigurationAdmin.class);
        configuration = mock(Configuration.class);
        when(configAdmin.getConfiguration(SecurityUpdateService.PID, "?")).thenReturn(configuration);
        store = new SettingsStore(() -> etc.toString(), audit::add);
        store.setConfigurationAdmin(configAdmin);
    }

    @SuppressWarnings("unchecked")
    private Dictionary<String, Object> written() throws IOException {
        ArgumentCaptor<Dictionary<String, Object>> captor = ArgumentCaptor.forClass(Dictionary.class);
        verify(configuration).update(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("updates the unbound configuration with every key, keeping the FileInstall file name")
    void updatesExistingConfiguration() throws IOException {
        Dictionary<String, Object> current = new Hashtable<>();
        current.put("felix.fileinstall.filename", "file:/var/jahia/karaf/etc/org.jahia.community.securityupdates.cfg");
        current.put("enabled", "true");
        current.put("someOtherKey", "kept");
        when(configuration.getProperties()).thenReturn(current);
        Map<String, String> values = Settings.defaults().toValues();

        store.save(values, "jdoe");

        Dictionary<String, Object> written = written();
        assertThat(written.get("felix.fileinstall.filename")).isEqualTo("file:/var/jahia/karaf/etc/org.jahia.community.securityupdates.cfg");
        assertThat(written.get("someOtherKey")).isEqualTo("kept");
        for (String key : SettingsKeys.ALL) {
            assertThat(written.get(key)).as(key).isEqualTo(values.get(key));
        }
    }

    @Test
    @DisplayName("without a configuration yet, writes the .cfg (with the default-configuration marker) and points FileInstall at it")
    void createsTheFile() throws IOException {
        when(configuration.getProperties()).thenReturn(null);

        store.save(Settings.defaults().toValues(), "jdoe");

        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).startsWith("# default configuration - won't be overridden").contains("bannerDismissPolicy=SESSION");
        assertThat(written().get("felix.fileinstall.filename")).isEqualTo(file.toUri().toString());
    }

    @Test
    @DisplayName("refuses a partial set of values and writes nothing")
    void refusesPartialValues() throws IOException {
        Map<String, String> values = new HashMap<>(Settings.defaults().toValues());
        values.remove(SettingsKeys.ENABLED);

        assertThatThrownBy(() -> store.save(values, "jdoe")).isInstanceOf(IllegalArgumentException.class);
        verify(configuration, never()).update(any());
    }

    @Test
    @DisplayName("a ConfigurationAdmin failure is reported to the caller")
    void updateFailure() throws IOException {
        when(configuration.getProperties()).thenReturn(new Hashtable<>());
        doThrow(new IOException("disk full")).when(configuration).update(any());

        assertThatThrownBy(() -> store.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("fails cleanly when ConfigurationAdmin is not bound")
    void notBound() {
        SettingsStore unbound = new SettingsStore(() -> etc.toString(), audit::add);
        assertThatThrownBy(() -> unbound.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("logs who changed which keys, old -> new, and nothing else")
    void auditsTheChange() throws IOException {
        Dictionary<String, Object> current = new Hashtable<>(Settings.defaults().toValues());
        when(configuration.getProperties()).thenReturn(current);
        Map<String, String> values = new HashMap<>(Settings.defaults().toValues());
        values.put(SettingsKeys.REFRESH_INTERVAL_HOURS, "6");
        values.put(SettingsKeys.DEFAULT_STORE_URL, "https://store.example.org");

        store.save(values, "jdoe");

        assertThat(audit).singleElement().satisfies(line -> assertThat(line)
                .contains("jdoe")
                .contains("refreshIntervalHours: 24 -> 6")
                .contains("defaultStoreUrl: https://store.jahia.com -> https://store.example.org")
                .doesNotContain("bannerTextColor"));
    }

    @Test
    @DisplayName("a save that changes nothing is logged as such; a missing old value shows as unset")
    void auditsNoChangeAndUnsetValues() throws IOException {
        Dictionary<String, Object> current = new Hashtable<>(Settings.defaults().toValues());
        when(configuration.getProperties()).thenReturn(current);
        store.save(Settings.defaults().toValues(), "jdoe");
        assertThat(audit).singleElement().satisfies(line -> assertThat(line).contains("jdoe").contains("no change"));

        current.remove(SettingsKeys.BANNER_TEXT_COLOR);
        store.save(Settings.defaults().toValues(), "root");
        assertThat(audit.get(1)).contains("bannerTextColor: (unset) -> #FFFFFF");
    }

    @Test
    @DisplayName("a failed update leaves no audit line and removes the file it had just created")
    void failedUpdateRemovesTheNewFile() throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        doThrow(new IOException("refused")).when(configuration).update(any());

        assertThatThrownBy(() -> store.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class);

        assertThat(etc.resolve(SecurityUpdateService.PID + ".cfg")).doesNotExist();
        assertThat(audit).isEmpty();
    }

    @Test
    @DisplayName("a failed write leaves no temporary file behind")
    void failedWriteLeavesNoTemporaryFile() throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        // a non-empty directory where the .cfg file should go: the final move fails
        Files.createDirectories(etc.resolve(SecurityUpdateService.PID + ".cfg").resolve("child"));

        assertThatThrownBy(() -> store.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class);

        try (java.util.stream.Stream<Path> files = Files.list(etc)) {
            assertThat(files.map(f -> f.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp"));
        }
        verify(configuration, never()).update(any());
    }

    @Test
    @DisplayName("without karaf.etc, a new configuration cannot be written")
    void noKarafEtc() {
        when(configuration.getProperties()).thenReturn(null);
        SettingsStore noEtc = new SettingsStore(() -> " ", audit::add);
        noEtc.setConfigurationAdmin(configAdmin);

        assertThatThrownBy(() -> noEtc.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class)
                .hasMessageContaining("karaf.etc");
        SettingsStore nullEtc = new SettingsStore(() -> null, audit::add);
        nullEtc.setConfigurationAdmin(configAdmin);
        assertThatThrownBy(() -> nullEtc.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("loads the stored values, the defaults when nothing is stored")
    void loadsStoredValues() throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        assertThat(store.load()).isEqualTo(Settings.defaults().toValues());

        Dictionary<String, Object> current = new Hashtable<>(Settings.defaults().toValues());
        current.put(SettingsKeys.REFRESH_INTERVAL_HOURS, "6");
        current.put(SettingsKeys.BANNER_BACKGROUND_COLOR, "#0b3d91");
        when(configuration.getProperties()).thenReturn(current);

        Map<String, String> loaded = store.load();

        assertThat(loaded).containsEntry(SettingsKeys.REFRESH_INTERVAL_HOURS, "6")
                .containsEntry(SettingsKeys.BANNER_BACKGROUND_COLOR, "#0B3D91")
                .containsOnlyKeys(SettingsKeys.ALL);
    }

    @Test
    @DisplayName("load fails cleanly when ConfigurationAdmin is not bound")
    void loadNotBound() {
        SettingsStore unbound = new SettingsStore(() -> etc.toString(), audit::add);
        assertThatThrownBy(unbound::load).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("the dictionary handed to ConfigurationAdmin behaves as a dictionary")
    void mapDictionary() {
        MapDictionary dictionary = new MapDictionary();
        assertThat(dictionary.isEmpty()).isTrue();
        dictionary.put("a", "1");
        dictionary.put("b", "2");

        assertThat(dictionary.size()).isEqualTo(2);
        assertThat(dictionary.get("a")).isEqualTo("1");
        assertThat(java.util.Collections.list(dictionary.keys())).containsExactly("a", "b");
        assertThat(java.util.Collections.list(dictionary.elements())).containsExactly("1", "2");
        assertThat(dictionary.remove("a")).isEqualTo("1");
        assertThat(dictionary.size()).isEqualTo(1);
    }
}
