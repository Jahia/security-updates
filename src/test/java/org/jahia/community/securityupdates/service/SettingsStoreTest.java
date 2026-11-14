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
    @DisplayName("logs a multi-valued value edited by hand as its values joined, never as an array reference")
    void auditsAMultiValuedValueJoined() throws IOException {
        Dictionary<String, Object> current = new Hashtable<>(Settings.defaults().toValues());
        current.put(SettingsKeys.EXTRA_FEED_URLS, new String[]{"https://a.example.org/f.json", "https://b.example.org/f.json"});
        when(configuration.getProperties()).thenReturn(current);
        Map<String, String> values = new HashMap<>(Settings.defaults().toValues());
        values.put(SettingsKeys.EXTRA_FEED_URLS, "https://a.example.org/f.json");

        store.save(values, "jdoe");

        assertThat(audit).singleElement().satisfies(line -> assertThat(line)
                .contains("extraFeedUrls: https://a.example.org/f.json, https://b.example.org/f.json -> https://a.example.org/f.json")
                .doesNotContain("[Ljava"));
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
    @DisplayName("load never returns the user information of a stored URL")
    void loadHidesUserInfo() throws IOException {
        Dictionary<String, Object> current = new Hashtable<>(Settings.defaults().toValues());
        current.put(SettingsKeys.DEFAULT_STORE_URL, "https://user:token@store.example.org/store");
        current.put(SettingsKeys.EXTRA_FEED_URLS, "https://a:b@mirror.example.org/s.json, https://mirror2.example.org/s.json");
        when(configuration.getProperties()).thenReturn(current);

        Map<String, String> loaded = store.load();

        assertThat(loaded).containsEntry(SettingsKeys.DEFAULT_STORE_URL, "https://store.example.org/store")
                .containsEntry(SettingsKeys.EXTRA_FEED_URLS, "https://mirror.example.org/s.json, https://mirror2.example.org/s.json");
        assertThat(String.valueOf(loaded)).doesNotContain("token").doesNotContain("a:b");
    }

    @Test
    @DisplayName("the audit line never shows the user information of a stored URL")
    void auditHidesUserInfo() throws IOException {
        Dictionary<String, Object> current = new Hashtable<>(Settings.defaults().toValues());
        current.put(SettingsKeys.DEFAULT_STORE_URL, "https://user:token@store.example.org");
        current.put(SettingsKeys.EXTRA_FEED_URLS, "https://a:b@mirror.example.org/s.json");
        when(configuration.getProperties()).thenReturn(current);

        store.save(Settings.defaults().toValues(), "jdoe");

        assertThat(audit).singleElement().satisfies(line -> assertThat(line)
                .contains("defaultStoreUrl: https://store.example.org -> https://store.jahia.com")
                .contains("extraFeedUrls: https://mirror.example.org/s.json -> ")
                .doesNotContain("token").doesNotContain("a:b"));
    }

    @Test
    @DisplayName("a .cfg file not loaded yet by ConfigurationAdmin is never overwritten: the save fails, to be retried")
    void existingFileNotLoadedYet() throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        String handWritten = SettingsStore.MARKER + "\n# my comment\nonDemandMinIntervalSeconds=0\nenabled=false\n";
        Files.writeString(file, handWritten, StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> store.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(IOException.class)
                .hasMessageContaining("try again");

        assertThat(Files.readString(file, StandardCharsets.ISO_8859_1)).isEqualTo(handWritten);
        try (java.util.stream.Stream<Path> files = Files.list(etc)) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly(SecurityUpdateService.PID + ".cfg");
        }
        verify(configuration, never()).update(any());
        assertThat(audit).isEmpty();
    }

    @Test
    @DisplayName("a .cfg file created between the write of the new file and its naming is never replaced")
    void fileCreatedMeanwhileIsNeverReplaced() throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        String other = "# written by someone else\n";
        SettingsStore racing = new SettingsStore(() -> etc.toString(), audit::add, (link, existing) -> {
            Files.writeString(link, other, StandardCharsets.ISO_8859_1);
            Files.createLink(link, existing);
        });
        racing.setConfigurationAdmin(configAdmin);

        assertThatThrownBy(() -> racing.save(Settings.defaults().toValues(), "jdoe")).isInstanceOf(java.nio.file.FileAlreadyExistsException.class)
                .hasMessageContaining("try again");

        assertThat(Files.readString(file, StandardCharsets.ISO_8859_1)).isEqualTo(other);
        try (java.util.stream.Stream<Path> files = Files.list(etc)) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly(SecurityUpdateService.PID + ".cfg");
        }
        verify(configuration, never()).update(any());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "without hard links ({0}), the new file is moved into place")
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void withoutHardLinks(boolean unsupported) throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        SettingsStore noLinks = new SettingsStore(() -> etc.toString(), audit::add, (link, existing) -> {
            if (unsupported) {
                throw new UnsupportedOperationException("no links");
            }
            throw new java.nio.file.FileSystemException(link.toString(), null, "Operation not permitted");
        });
        noLinks.setConfigurationAdmin(configAdmin);

        noLinks.save(Settings.defaults().toValues(), "jdoe");

        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        assertThat(Files.readString(file, StandardCharsets.ISO_8859_1)).startsWith(SettingsStore.MARKER);
        try (java.util.stream.Stream<Path> files = Files.list(etc)) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly(SecurityUpdateService.PID + ".cfg");
        }
    }

    @Test
    @DisplayName("without hard links, an existing .cfg file is still never replaced")
    void withoutHardLinksExistingFileIsKept() throws IOException {
        when(configuration.getProperties()).thenReturn(null);
        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        Files.writeString(file, "enabled=false\n", StandardCharsets.ISO_8859_1);
        SettingsStore noLinks = new SettingsStore(() -> etc.toString(), audit::add, (link, existing) -> {
            throw new UnsupportedOperationException("no links");
        });
        noLinks.setConfigurationAdmin(configAdmin);

        assertThatThrownBy(() -> noLinks.save(Settings.defaults().toValues(), "jdoe")).hasMessageContaining("try again");

        assertThat(Files.readString(file, StandardCharsets.ISO_8859_1)).isEqualTo("enabled=false\n");
        try (java.util.stream.Stream<Path> files = Files.list(etc)) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly(SecurityUpdateService.PID + ".cfg");
        }
    }

    @Test
    @DisplayName("two saves at once, the first creating the file, leave a complete file, no temporary file, and both updates")
    void concurrentSaves() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Dictionary<String, Object>> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(configuration.getProperties()).thenAnswer(invocation -> stored.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            Dictionary<String, Object> next = invocation.getArgument(0);
            // Widens the window in which the two saves overlap.
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(20));
            stored.set(next);
            return null;
        }).when(configuration).update(any());
        Map<String, String> first = new HashMap<>(Settings.defaults().toValues());
        first.put(SettingsKeys.REFRESH_INTERVAL_HOURS, "6");
        Map<String, String> second = new HashMap<>(Settings.defaults().toValues());
        second.put(SettingsKeys.REFRESH_INTERVAL_HOURS, "12");
        java.util.concurrent.ExecutorService callers = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Future<?> a = callers.submit(() -> {
                go.await();
                store.save(first, "a");
                return null;
            });
            java.util.concurrent.Future<?> b = callers.submit(() -> {
                go.await();
                store.save(second, "b");
                return null;
            });
            go.countDown();
            a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            b.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }

        Path file = etc.resolve(SecurityUpdateService.PID + ".cfg");
        List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        assertThat(lines.get(0)).isEqualTo(SettingsStore.MARKER);
        for (String key : SettingsKeys.ALL) {
            assertThat(lines).as(key).filteredOn(line -> line.startsWith(key + "=")).hasSize(1);
        }
        try (java.util.stream.Stream<Path> files = Files.list(etc)) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly(SecurityUpdateService.PID + ".cfg");
        }
        verify(configuration, org.mockito.Mockito.times(2)).update(any());
        assertThat(stored.get().get(SettingsStore.FILEINSTALL_FILENAME)).isEqualTo(file.toUri().toString());
        assertThat(audit).hasSize(2);
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
