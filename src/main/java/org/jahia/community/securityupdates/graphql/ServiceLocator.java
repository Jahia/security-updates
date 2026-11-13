package org.jahia.community.securityupdates.graphql;

import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.community.securityupdates.service.SettingsStore;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;

import java.util.function.Supplier;

/**
 * Finds the {@link SecurityUpdateService} for the GraphQL types, which graphql-java-annotations
 * instantiates outside OSGi injection.
 */
final class ServiceLocator {

    private static final Supplier<SecurityUpdateService> OSGI =
            () -> BundleUtils.getOsgiService(SecurityUpdateService.class, null);
    private static final Supplier<SettingsStore> OSGI_STORE = () -> BundleUtils.getOsgiService(SettingsStore.class, null);
    /** The Jahia user of the current request, null when there is none. */
    private static final Supplier<String> JAHIA_USER = () -> {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        return user == null ? null : user.getUsername();
    };
    private static volatile Supplier<String> usernameSupplier = JAHIA_USER;
    private static volatile Supplier<SecurityUpdateService> supplier = OSGI;
    private static volatile Supplier<SettingsStore> storeSupplier = OSGI_STORE;

    private ServiceLocator() {
    }

    /** @return the service, or null when it is not running */
    static SecurityUpdateService service() {
        return supplier.get();
    }

    /** @return the settings store, or null when it is not running */
    static SettingsStore store() {
        return storeSupplier.get();
    }

    /** @return the name of the Jahia user of the current request, or null */
    static String username() {
        return usernameSupplier.get();
    }

    /** Test seam. */
    static void overrideUsername(Supplier<String> testSupplier) {
        usernameSupplier = testSupplier;
    }

    /** Test seam. */
    static void overrideStore(Supplier<SettingsStore> testSupplier) {
        storeSupplier = testSupplier;
    }

    /** Test seam. */
    static void override(Supplier<SecurityUpdateService> testSupplier) {
        supplier = testSupplier;
    }

    static void reset() {
        supplier = OSGI;
        storeSupplier = OSGI_STORE;
        usernameSupplier = JAHIA_USER;
    }
}
