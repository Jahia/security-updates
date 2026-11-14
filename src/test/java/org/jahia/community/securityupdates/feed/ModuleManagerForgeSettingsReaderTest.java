package org.jahia.community.securityupdates.feed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ModuleManagerForgeSettingsReader")
class ModuleManagerForgeSettingsReaderTest {

    private static final String FILTER = "(service.factoryPid=" + ModuleManagerForgeSettingsReader.FACTORY_PID + ")";

    private ConfigurationAdmin configAdmin;
    private List<StoreTarget> jcrStores;
    private RepositoryException jcrFailure;
    private ModuleManagerForgeSettingsReader reader;

    @BeforeEach
    void setUp() {
        configAdmin = mock(ConfigurationAdmin.class);
        jcrStores = List.of();
        jcrFailure = null;
        reader = new ModuleManagerForgeSettingsReader(() -> {
            if (jcrFailure != null) {
                throw jcrFailure;
            }
            return jcrStores;
        });
        reader.setConfigurationAdmin(configAdmin);
    }

    private static Configuration config(String pid, String url, String user, String password) {
        Configuration configuration = mock(Configuration.class);
        Dictionary<String, Object> properties = new Hashtable<>();
        if (url != null) {
            properties.put("url", url);
        }
        if (user != null) {
            properties.put("user", user);
        }
        if (password != null) {
            properties.put("password", password);
        }
        when(configuration.getPid()).thenReturn(pid);
        when(configuration.getProperties()).thenReturn(properties);
        return configuration;
    }

    @Test
    @DisplayName("module manager 2.14+: reads the forge factory configurations, with their credentials, in PID order")
    void readsFactoryConfigurations() throws Exception {
        Configuration[] configurations = {
                config("org.jahia.modules.modulemanager.forge.configuration.b", "https://store.example.org", "jdoe", "secret"),
                config("org.jahia.modules.modulemanager.forge.configuration.a", "https://store.jahia.com/en/sites/private-app-store",
                        null, null),
                config("org.jahia.modules.modulemanager.forge.configuration.c", null, "x", "y")};
        when(configAdmin.listConfigurations(FILTER)).thenReturn(configurations);
        jcrStores = List.of(StoreTarget.store("https://jcr.example.org", null, null));

        List<StoreTarget> targets = reader.readForges();

        assertThat(targets).extracting(StoreTarget::getUrl)
                .containsExactly("https://store.jahia.com/en/sites/private-app-store", "https://store.example.org");
        assertThat(targets.get(1).getUser()).isEqualTo("jdoe");
        assertThat(targets.get(1).getPassword()).isEqualTo("secret");
        assertThat(targets.get(0).hasCredentials()).isFalse();
    }

    @Test
    @DisplayName("module manager 2.12 and earlier: no factory configuration, the JCR forge settings are read")
    void fallsBackToTheJcr() throws Exception {
        when(configAdmin.listConfigurations(FILTER)).thenReturn(null);
        jcrStores = List.of(StoreTarget.store("https://jcr.example.org", "u", "p"));

        assertThat(reader.readForges()).extracting(StoreTarget::getUrl).containsExactly("https://jcr.example.org");
    }

    @Test
    @DisplayName("no store anywhere: empty (the default store is then used)")
    void noStoreAnywhere() throws Exception {
        when(configAdmin.listConfigurations(FILTER)).thenReturn(new Configuration[0]);

        assertThat(reader.readForges()).isEmpty();
    }

    @Test
    @DisplayName("the configurations cannot be listed and the JCR has no store: unreadable, not a silent fallback")
    void configurationsUnreadableAndJcrEmpty() throws Exception {
        when(configAdmin.listConfigurations(anyString())).thenThrow(new IOException("store broken"));

        assertThatThrownBy(reader::readForges).isInstanceOf(ForgeSettingsException.class);
    }

    @Test
    @DisplayName("the configurations cannot be listed but the JCR lists stores: the JCR stores are used")
    void configurationsUnreadableButJcrHasStores() throws Exception {
        when(configAdmin.listConfigurations(anyString())).thenThrow(new InvalidSyntaxException("bad", FILTER));
        jcrStores = List.of(StoreTarget.store("https://jcr.example.org", null, null));

        assertThat(reader.readForges()).hasSize(1);
    }

    @Test
    @DisplayName("neither source can be read: unreadable")
    void nothingReadable() throws Exception {
        when(configAdmin.listConfigurations(FILTER)).thenReturn(null);
        jcrFailure = new RepositoryException("down");

        assertThatThrownBy(reader::readForges).isInstanceOf(ForgeSettingsException.class)
                .hasMessageContaining("module manager");
    }

    @Test
    @DisplayName("ConfigurationAdmin not bound and the JCR empty: unreadable")
    void notBound() {
        ModuleManagerForgeSettingsReader unbound = new ModuleManagerForgeSettingsReader(List::of);

        assertThatThrownBy(unbound::readForges).isInstanceOf(ForgeSettingsException.class);
    }

    @Test
    @DisplayName("a configuration with an empty url is ignored")
    void emptyUrl() throws Exception {
        Configuration[] configurations = {config("p1", " ", null, null), config("p2", "https://store.example.org", null, null)};
        when(configAdmin.listConfigurations(FILTER)).thenReturn(configurations);

        assertThat(reader.readForges()).extracting(StoreTarget::getUrl).containsExactly("https://store.example.org");
    }
}
