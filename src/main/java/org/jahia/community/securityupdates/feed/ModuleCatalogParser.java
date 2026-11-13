package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses {@code modules-repository.moduleList.json}: a JSON array whose first element holds {@code modules},
 * each with {@code name}, {@code groupId} and {@code versions[] {version, requiredVersion}} (the fields
 * Jahia's module manager reads). Incomplete modules and versions are skipped.
 */
public final class ModuleCatalogParser {

    private static final String REQUIRED_VERSION_PREFIX = "version-";
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
        for (int i = 0; i < modules.length(); i++) {
            Object value = modules.opt(i);
            if (!(value instanceof JSONObject)) {
                continue;
            }
            JSONObject module = (JSONObject) value;
            String name = Json.string(module, "name", MAX_NAME);
            String groupId = Json.string(module, "groupId", MAX_NAME);
            JSONArray versions = module.optJSONArray("versions");
            if (name == null || groupId == null || versions == null) {
                continue;
            }
            Map<String, String> byVersion = entries.computeIfAbsent(name, k -> new LinkedHashMap<>())
                    .computeIfAbsent(groupId, k -> new LinkedHashMap<>());
            readVersions(versions, byVersion);
        }
        return new ModuleCatalog(entries);
    }

    private static JSONArray modules(String json) throws FeedFormatException {
        if (json == null || json.trim().isEmpty()) {
            throw new FeedFormatException("Empty module list");
        }
        try {
            Object value = new JSONTokener(json).nextValue();
            if (!(value instanceof JSONArray)) {
                throw new FeedFormatException("The module list is not a JSON array");
            }
            JSONArray root = (JSONArray) value;
            if (root.isEmpty()) {
                return new JSONArray();
            }
            Object first = root.opt(0);
            if (!(first instanceof JSONObject) || !(((JSONObject) first).opt("modules") instanceof JSONArray)) {
                throw new FeedFormatException("The module list has no 'modules' array");
            }
            return ((JSONObject) first).getJSONArray("modules");
        } catch (JSONException e) {
            throw new FeedFormatException("The module list is not valid JSON", e);
        } catch (StackOverflowError e) {
            throw new FeedFormatException("The module list is nested too deeply");
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
