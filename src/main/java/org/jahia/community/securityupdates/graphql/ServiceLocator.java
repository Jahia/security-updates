package org.jahia.community.securityupdates.graphql;

import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.community.securityupdates.service.SettingsStore;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Gives the GraphQL types, which graphql-java-annotations instantiates outside OSGi injection, the module services,
 * the current Jahia user and whether that user may change the settings. Each lookup goes through the OSGi service
 * registry, so a stopped service reads as absent; tests replace the lookups.
 */
final class ServiceLocator {

    private static final Logger logger = LoggerFactory.getLogger(ServiceLocator.class);
    private static final Supplier<SecurityUpdateService> OSGI =
            () -> BundleUtils.getOsgiService(SecurityUpdateService.class, null);
    private static final Supplier<SettingsStore> OSGI_STORE = () -> BundleUtils.getOsgiService(SettingsStore.class, null);
    /** The Jahia user of the current request, null when there is none. */
    private static final Supplier<String> JAHIA_USER = () -> {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        return user == null ? null : user.getUsername();
    };
    /** Whether the user of the current request holds the permission that changes the settings, on the root node. */
    private static final BooleanSupplier JAHIA_CAN_EDIT =
            () -> holdsAdmin(() -> JCRSessionFactory.getInstance().getCurrentUserSession().getNode("/"));

    private static final AtomicReference<Supplier<SecurityUpdateService>> SERVICE = new AtomicReference<>(OSGI);
    private static final AtomicReference<Supplier<SettingsStore>> STORE = new AtomicReference<>(OSGI_STORE);
    private static final AtomicReference<Supplier<String>> USERNAME = new AtomicReference<>(JAHIA_USER);
    private static final AtomicReference<BooleanSupplier> CAN_EDIT = new AtomicReference<>(JAHIA_CAN_EDIT);

    private ServiceLocator() {
    }

    /** @return the service, or null when it is not running */
    static SecurityUpdateService service() {
        return SERVICE.get().get();
    }

    /**
     * @param action   what to do with the service
     * @param fallback the answer when the service is not running
     */
    static <T> T withService(Function<SecurityUpdateService, T> action, Supplier<T> fallback) {
        SecurityUpdateService service = service();
        return service == null ? fallback.get() : action.apply(service);
    }

    /** @return the settings store, or null when it is not running */
    static SettingsStore store() {
        return STORE.get().get();
    }

    /** @return the name of the Jahia user of the current request, or null */
    static String username() {
        return USERNAME.get().get();
    }

    /** @return true when the user of the current request may change the settings */
    static boolean canEditSettings() {
        return CAN_EDIT.get().getAsBoolean();
    }

    /** Test seam. */
    static void override(Supplier<SecurityUpdateService> testSupplier) {
        SERVICE.set(testSupplier);
    }

    /** Test seam. */
    static void overrideStore(Supplier<SettingsStore> testSupplier) {
        STORE.set(testSupplier);
    }

    /** Test seam. */
    static void overrideUsername(Supplier<String> testSupplier) {
        USERNAME.set(testSupplier);
    }

    /** Test seam. */
    static void overrideCanEdit(BooleanSupplier testSupplier) {
        CAN_EDIT.set(testSupplier);
    }

    /** The root node of the current user's session. */
    @FunctionalInterface
    interface RootNode {
        JCRNodeWrapper get() throws RepositoryException;
    }

    /**
     * @return true when the user holds {@code admin} on the root node; false when that cannot be checked (no
     * session, a repository failure): the settings then show read-only, and the mutations check again
     */
    static boolean holdsAdmin(RootNode root) {
        try {
            return root.get().hasPermission(Permissions.ADMIN);
        } catch (RepositoryException | RuntimeException e) {
            logger.debug("Could not check the settings permission", e);
            return false;
        }
    }

    static void reset() {
        SERVICE.set(OSGI);
        STORE.set(OSGI_STORE);
        USERNAME.set(JAHIA_USER);
        CAN_EDIT.set(JAHIA_CAN_EDIT);
    }
}
