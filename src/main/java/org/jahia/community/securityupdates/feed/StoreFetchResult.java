package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.StoreError;

import java.util.List;

/**
 * What one store returned: its security feed and module catalogue when they could be read, and the errors.
 */
public final class StoreFetchResult {

    private final String storeUrl;
    private final SecurityFeed feed;
    private final ModuleCatalog catalog;
    private final List<StoreError> errors;

    public StoreFetchResult(String storeUrl, SecurityFeed feed, ModuleCatalog catalog, List<StoreError> errors) {
        this.storeUrl = storeUrl;
        this.feed = feed;
        this.catalog = catalog;
        this.errors = List.copyOf(errors);
    }

    public String getStoreUrl() {
        return storeUrl;
    }

    /** @return the feed, null when it could not be read or understood */
    public SecurityFeed getFeed() {
        return feed;
    }

    /** @return the module catalogue, null when it could not be read (or the target is feed-only) */
    public ModuleCatalog getCatalog() {
        return catalog;
    }

    public List<StoreError> getErrors() {
        return errors;
    }
}
