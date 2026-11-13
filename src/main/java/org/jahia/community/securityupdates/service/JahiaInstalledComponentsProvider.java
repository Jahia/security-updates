package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.services.templates.JahiaTemplateManagerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the started modules from Jahia's template package registry (one active version per module id,
 * which is the bundle symbolic name the store uses as module name).
 */
@Component(service = InstalledComponentsProvider.class)
public class JahiaInstalledComponentsProvider implements InstalledComponentsProvider {

    private JahiaTemplateManagerService templateManagerService;

    /** Static mandatory reference, set by Declarative Services before the component is used. */
    @Reference
    void setTemplateManagerService(JahiaTemplateManagerService templateManagerService) {
        this.templateManagerService = templateManagerService;
    }

    @Override
    public List<InstalledComponent> installedModules() {
        List<InstalledComponent> modules = new ArrayList<>();
        for (JahiaTemplatesPackage pkg : templateManagerService.getTemplatePackageRegistry().getRegisteredModules().values()) {
            if (pkg != null && pkg.getId() != null && pkg.getVersion() != null) {
                modules.add(new InstalledComponent(pkg.getId(), pkg.getGroupId(), pkg.getVersion().toString()));
            }
        }
        return modules;
    }
}
