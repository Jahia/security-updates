package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses {@code modules-repository.moduleList.json}: a JSON array whose first element holds {@code modules},
 * each with {@code name}, {@code groupId} and {@code versions[] {version, requiredVersion}} (the fields
 * Jahia's module manager reads). Incomplete modules are skipped and counted; at most {@link #MAX_ENTRIES}
 * modules are read, the others are counted as skipped and the catalogue is marked truncated.
 */
public final class ModuleCatalogParser {

    /** Most modules read from one catalogue (store.jahia.com lists about 1,000 in 2026). */
    static final int MAX_ENTRIES = 10_000;
    private static final String REQUIRED_VERSION_PREFIX = "version-";
    private static final String DOCUMENT = "The module list";
    private static final int MAX_NAME = 200;
    private static final int MAX_VERSION = 64;

    private ModuleCatalogParser() {
    }

    /**
     * @throws FeedFormatException when the document does not have the expected structure
     */
    public static ModuleCatalog parse(String json) throws FeedFormatException {
        JSONArray modules = modules(json);
        Map<String, Map<String, Map<String, String>>> entries = new LinkedHashMap<>();
        int read = Math.min(modules.length(), MAX_ENTRIES);
        int skipped = modules.length() - read;
        for (int i = 0; i < read; i++) {
            if (!addModule(modules.opt(i), entries)) {
                skipped++;
            }
        }
        return new ModuleCatalog(entries, skipped, modules.length() > MAX_ENTRIES);
    }

    /** @return false when the value is not a complete module entry */
    private static boolean addModule(Object value, Map<String, Map<String, Map<String, String>>> entries) {
        if (!(value instanceof JSONObject)) {
            return false;
        }
        JSONObject module = (JSONObject) value;
        String name = Json.string(module, "name", MAX_NAME);
        String groupId = Json.string(module, "groupId", MAX_NAME);
        JSONArray versions = module.optJSONArray("versions");
        if (name == null || groupId == null || versions == null) {
            return false;
        }
        Map<String, String> byVersion = entries.computeIfAbsent(name, k -> new LinkedHashMap<>())
                .computeIfAbsent(groupId, k -> new LinkedHashMap<>());
        readVersions(versions, byVersion);
        return true;
    }

    private static JSONArray modules(String json) throws FeedFormatException {
        if (json == null || json.trim().isEmpty()) {
            throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "Empty module list");
        }
        JsonNesting.check(json, DOCUMENT);
        try {
            Object value = new JSONTokener(json).nextValue();
            if (!(value instanceof JSONArray)) {
                throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "The module list is not a JSON array");
            }
            JSONArray root = (JSONArray) value;
            if (root.isEmpty()) {
                return new JSONArray();
            }
            Object first = root.opt(0);
            if (!(first instanceof JSONObject) || !(((JSONObject) first).opt("modules") instanceof JSONArray)) {
                throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "The module list has no 'modules' array");
            }
            return ((JSONObject) first).getJSONArray("modules");
        } catch (JSONException e) {
            throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "The module list is not valid JSON", e);
        }
    }

    private static void readVersions(JSONArray versions, Map<String, String> byVersion) {
        for (int j = 0; j < versions.length(); j++) {
            Object value = versions.opt(j);
            if (!(value instanceof JSONObject)) {
                continue;
            }
            JSONObject version = (JSONObject) value;
            String number = Json.string(version, "version", MAX_VERSION);
            String required = Json.string(version, "requiredVersion", MAX_VERSION);
            if (number != null && required != null) {
                byVersion.putIfAbsent(number, required.startsWith(REQUIRED_VERSION_PREFIX)
                        ? required.substring(REQUIRED_VERSION_PREFIX.length()) : required);
            }
        }
    }
}
