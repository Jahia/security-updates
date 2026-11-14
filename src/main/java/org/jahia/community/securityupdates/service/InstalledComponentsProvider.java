package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.InstalledComponent;

import java.util.List;

/**
 * Lists the modules running on this Jahia.
 */
public interface InstalledComponentsProvider {

    /**
     * @return one entry per started module (its active version)
     */
    List<InstalledComponent> installedModules();
}
