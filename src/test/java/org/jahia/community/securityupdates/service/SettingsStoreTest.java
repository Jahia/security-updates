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
import java.util.Dictionary;
import java.util.Hashtable;
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

    @TempDir
    Path etc;

    @BeforeEach
    void setUp() throws IOException {
        configAdmin = mock(ConfigurationAdmin.class);
        configuration = mock(Configuration.class);
        when(configAdmin.getConfiguration(SecurityUpdateService.PID, "?")).thenReturn(configuration);
        store = new SettingsStore(() -> etc.toString());
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

        store.save(values);

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

        store.save(Settings.defaults().toValues());

        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).startsWith("# default configuration - won't be overridden");
        assertThat(content).contains("bannerDismissPolicy=SESSION");
        assertThat(written().get("felix.fileinstall.filename")).isEqualTo(file.toUri().toString());
    }

    @Test
    @DisplayName("refuses a partial set of values and writes nothing")
    void refusesPartialValues() throws IOException {
        Map<String, String> values = new java.util.HashMap<>(Settings.defaults().toValues());
        values.remove(SettingsKeys.ENABLED);

        assertThatThrownBy(() -> store.save(values)).isInstanceOf(IllegalArgumentException.class);
        verify(configuration, never()).update(any());
    }

    @Test
    @DisplayName("a ConfigurationAdmin failure is reported to the caller")
    void updateFailure() throws IOException {
        when(configuration.getProperties()).thenReturn(new Hashtable<>());
        doThrow(new IOException("disk full")).when(configuration).update(any());

        assertThatThrownBy(() -> store.save(Settings.defaults().toValues())).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("fails cleanly when ConfigurationAdmin is not bound")
    void notBound() {
        SettingsStore unbound = new SettingsStore(() -> etc.toString());
        assertThatThrownBy(() -> unbound.save(Settings.defaults().toValues())).isInstanceOf(IOException.class);
    }
}
