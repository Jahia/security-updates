package org.jahia.community.securityupdates.graphql;

import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.community.securityupdates.service.SettingsStore;
import org.jahia.osgi.BundleUtils;

import java.util.function.Supplier;

/**
 * Finds the {@link SecurityUpdateService} for the GraphQL types, which graphql-java-annotations
 * instantiates outside OSGi injection.
 */
final class ServiceLocator {

    private static final Supplier<SecurityUpdateService> OSGI =
            () -> BundleUtils.getOsgiService(SecurityUpdateService.class, null);
    private static final Supplier<SettingsStore> OSGI_STORE = () -> BundleUtils.getOsgiService(SettingsStore.class, null);
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
    }
}
