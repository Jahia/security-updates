package org.jahia.community.securityupdates.feed;

import java.util.List;

/**
 * Reads the stores ("forges") configured in Jahia's module manager.
 */
public interface ForgeSettingsReader {

    /**
     * @return one store target per configured forge, with its credentials; empty when none is configured
     * @throws ForgeSettingsException when the settings cannot be read
     */
    List<StoreTarget> readForges() throws ForgeSettingsException;
}
