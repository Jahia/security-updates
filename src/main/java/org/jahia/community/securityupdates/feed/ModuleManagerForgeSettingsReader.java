package org.jahia.community.securityupdates.feed;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.List;

/**
 * Reads the stores of Jahia's module manager, wherever its version keeps them:
 * <ul>
 *     <li>module manager 2.14 and later: the factory configurations {@value #FACTORY_PID} (files
 *     {@code karaf/etc/org.jahia.modules.modulemanager.forge.configuration-<name>.cfg}, keys {@code url},
 *     {@code user}, {@code password}), read through {@link ConfigurationAdmin}, in PID order;</li>
 *     <li>earlier versions: the {@code jnt:forgeServerSettings} nodes of the JCR ({@link JcrForgeSettingsReader}),
 *     used when no factory configuration exists.</li>
 * </ul>
 * When the configurations cannot be listed and the JCR lists no store, or when the JCR cannot be read either, the
 * stores are unknown: a {@link ForgeSettingsException} says so, rather than a silent fall back to the default
 * store. The passwords are handed to the store client only and never logged.
 *
 * <p>Whoever can edit the module manager stores (the {@code adminTemplates} permission) decides which stores are
 * read and with which credentials: that is the module manager's own trust boundary. Only https stores are read.
 */
@Component(service = ForgeSettingsReader.class)
public class ModuleManagerForgeSettingsReader implements ForgeSettingsReader {

    /** Factory PID of the module manager forge configurations (module manager 2.14 and later). */
    static final String FACTORY_PID = "org.jahia.modules.modulemanager.forge.configuration";
    private static final String FILTER = "(service.factoryPid=" + FACTORY_PID + ")";
    private static final String UNREADABLE = "The stores of the module manager could not be read";
    private static final Logger logger = LoggerFactory.getLogger(ModuleManagerForgeSettingsReader.class);

    /** Reads the JCR forge settings (module manager 2.12 and earlier). */
    @FunctionalInterface
    interface JcrForges {
        List<StoreTarget> read() throws RepositoryException;
    }

    private final JcrForges jcrForges;
    private ConfigurationAdmin configurationAdmin;

    public ModuleManagerForgeSettingsReader() {
        this(new JcrForgeSettingsReader()::readForges);
    }

    ModuleManagerForgeSettingsReader(JcrForges jcrForges) {
        this.jcrForges = jcrForges;
    }

    /** Static mandatory reference, set by Declarative Services before the component is used. */
    @Reference
    void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Override
    public List<StoreTarget> readForges() throws ForgeSettingsException {
        Exception configurationFailure = null;
        try {
            List<StoreTarget> configured = fromConfigurations();
            if (!configured.isEmpty()) {
                return configured;
            }
        } catch (IOException | InvalidSyntaxException | RuntimeException e) {
            configurationFailure = e;
            logger.debug("Could not list the module manager forge configurations", e);
        }
        List<StoreTarget> fromJcr;
        try {
            fromJcr = jcrForges.read();
        } catch (RepositoryException | RuntimeException e) {
            throw new ForgeSettingsException(UNREADABLE, e);
        }
        if (configurationFailure != null && fromJcr.isEmpty()) {
            throw new ForgeSettingsException(UNREADABLE, configurationFailure);
        }
        return fromJcr;
    }

    private List<StoreTarget> fromConfigurations() throws IOException, InvalidSyntaxException {
        ConfigurationAdmin admin = configurationAdmin;
        if (admin == null) {
            throw new IOException("ConfigurationAdmin not available");
        }
        Configuration[] configurations = admin.listConfigurations(FILTER);
        List<StoreTarget> targets = new ArrayList<>();
        if (configurations == null) {
            return targets;
        }
        Configuration[] sorted = configurations.clone();
        Arrays.sort(sorted, Comparator.comparing(Configuration::getPid));
        for (Configuration configuration : sorted) {
            Dictionary<String, Object> properties = configuration.getProperties();
            String url = properties == null ? null : text(properties.get("url"));
            if (url != null) {
                targets.add(StoreTarget.store(url, text(properties.get("user")), text(properties.get("password"))));
            }
        }
        return targets;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
