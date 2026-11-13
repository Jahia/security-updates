package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.services.templates.JahiaTemplateManagerService;
import org.jahia.services.templates.ModuleVersion;
import org.jahia.services.templates.TemplatePackageRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JahiaInstalledComponentsProvider")
class JahiaInstalledComponentsProviderTest {

    @Test
    @DisplayName("lists the started modules with groupId and active version")
    void listsRegisteredModules() {
        Map<String, JahiaTemplatesPackage> registered = new LinkedHashMap<>();
        registered.put("graphql-dxm-provider", pkg("graphql-dxm-provider", "org.jahia.modules", "3.4.0"));
        registered.put("no-version", pkg("no-version", "org.jahia.modules", null));
        registered.put("jcontent", pkg("jcontent", "org.jahia.modules", "3.5.0-SNAPSHOT"));
        TemplatePackageRegistry registry = mock(TemplatePackageRegistry.class);
        when(registry.getRegisteredModules()).thenReturn(registered);
        JahiaTemplateManagerService templates = mock(JahiaTemplateManagerService.class);
        when(templates.getTemplatePackageRegistry()).thenReturn(registry);
        JahiaInstalledComponentsProvider provider = new JahiaInstalledComponentsProvider();
        provider.setTemplateManagerService(templates);

        List<InstalledComponent> modules = provider.installedModules();

        assertThat(modules).extracting(InstalledComponent::toString)
                .containsExactly("graphql-dxm-provider@3.4.0", "jcontent@3.5.0-SNAPSHOT");
        assertThat(modules.get(0).getGroupId()).isEqualTo("org.jahia.modules");
    }

    private static JahiaTemplatesPackage pkg(String id, String groupId, String version) {
        JahiaTemplatesPackage pkg = mock(JahiaTemplatesPackage.class);
        when(pkg.getId()).thenReturn(id);
        when(pkg.getGroupId()).thenReturn(groupId);
        when(pkg.getVersion()).thenReturn(version == null ? null : new ModuleVersion(version));
        return pkg;
    }
}
