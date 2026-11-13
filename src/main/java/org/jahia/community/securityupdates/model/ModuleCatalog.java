package org.jahia.community.securityupdates.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The versions a store offers for each module, with the minimum Jahia version each one requires, as read
 * from {@code modules-repository.moduleList.json}.
 */
public final class ModuleCatalog {

    private static final ModuleCatalog EMPTY = new ModuleCatalog(Collections.emptyMap());

    /** name -> groupId -> version -> required Jahia version (without the {@code version-} prefix). */
    private final Map<String, Map<String, Map<String, String>>> entries;
    private final int skippedEntries;
    private final boolean truncated;

    /**
     * @param entries module name -> groupId -> version -> required Jahia version; copied
     */
    public ModuleCatalog(Map<String, Map<String, Map<String, String>>> entries) {
        this(entries, 0, false);
    }

    /**
     * @param entries        module name -> groupId -> version -> required Jahia version; copied
     * @param skippedEntries modules of the document that were not read (incomplete, or beyond the limit)
     * @param truncated      true when the document listed more modules than the limit
     */
    public ModuleCatalog(Map<String, Map<String, Map<String, String>>> entries, int skippedEntries, boolean truncated) {
        this.skippedEntries = skippedEntries;
        this.truncated = truncated;
        Map<String, Map<String, Map<String, String>>> copy = new LinkedHashMap<>();
        entries.forEach((name, byGroup) -> {
            Map<String, Map<String, String>> groupCopy = new LinkedHashMap<>();
            byGroup.forEach((group, versions) -> groupCopy.put(group, Collections.unmodifiableMap(new LinkedHashMap<>(versions))));
            copy.put(name, Collections.unmodifiableMap(groupCopy));
        });
        this.entries = Collections.unmodifiableMap(copy);
    }

    public static ModuleCatalog empty() {
        return EMPTY;
    }

    /**
     * Merges catalogues; when two catalogues know the same module version, the first one wins.
     */
    public static ModuleCatalog merge(List<ModuleCatalog> catalogs) {
        Map<String, Map<String, Map<String, String>>> merged = new LinkedHashMap<>();
        for (ModuleCatalog catalog : catalogs) {
            catalog.entries.forEach((name, byGroup) -> byGroup.forEach((group, versions) -> {
                Map<String, String> target = merged.computeIfAbsent(name, k -> new LinkedHashMap<>())
                        .computeIfAbsent(group, k -> new LinkedHashMap<>());
                versions.forEach(target::putIfAbsent);
            }));
        }
        return new ModuleCatalog(merged);
    }

    /**
     * @param name    the module name
     * @param groupId the module groupId; when null, any groupId publishing that name and version matches
     * @param version the module version
     * @return the minimum Jahia version that module version requires, empty when the catalogue does not list it
     */
    public Optional<String> requiredJahiaVersion(String name, String groupId, String version) {
        Map<String, Map<String, String>> byGroup = entries.get(name);
        if (byGroup == null || version == null) {
            return Optional.empty();
        }
        if (groupId != null) {
            Map<String, String> versions = byGroup.get(groupId);
            return versions == null ? Optional.empty() : Optional.ofNullable(versions.get(version));
        }
        for (Map<String, String> versions : byGroup.values()) {
            String required = versions.get(version);
            if (required != null) {
                return Optional.of(required);
            }
        }
        return Optional.empty();
    }

    public int getSkippedEntries() {
        return skippedEntries;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
