package org.jahia.community.securityupdates.feed;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Reads the stores ("forges") configured in Jahia's module manager.
 */
public interface ForgeSettingsReader {

    /**
     * @return one store target per configured forge, with its credentials; empty when none is configured
     * @throws RepositoryException when the settings cannot be read
     */
    List<StoreTarget> readForges() throws RepositoryException;
}
