package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.matching.Versions;
import org.jahia.community.securityupdates.model.Advisory;
import org.jahia.community.securityupdates.model.AdvisoryFix;
import org.jahia.community.securityupdates.model.FlaggedVersion;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.Severity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses {@code modules-repository.securityUpdates.json}. The document must be a JSON object with
 * {@code schemaVersion} 1; anything else fails closed with a {@link FeedFormatException}. Inside a
 * supported document, incomplete entries are skipped and counted. All text is cleaned and bounded.
 */
public final class SecurityFeedParser {

    /** Longest kept text value (titles, notes). */
    public static final int MAX_TEXT = 500;
    static final int MAX_ID = 100;
    static final int MAX_NAME = 200;
    static final int MAX_URL = 2048;
    static final int MAX_ENTRIES = 10_000;
    static final int MAX_ITEMS = 100;
    static final int MAX_VERSION = 64;

    private static final String SEVERITY = "severity";
    private static final String GROUP_ID = "groupId";

    private SecurityFeedParser() {
    }

    /**
     * @param json the response body
     * @return the parsed feed
     * @throws FeedFormatException when the body is not a JSON object, or its schema version is not 1
     */
    public static SecurityFeed parse(String json) throws FeedFormatException {
        JSONObject root = parseObject(json);
        Object schemaVersion = root.opt("schemaVersion");
        if (!(schemaVersion instanceof Integer) || (Integer) schemaVersion != SecurityFeed.SUPPORTED_SCHEMA_VERSION) {
            throw new FeedFormatException("Unsupported schemaVersion " + describe(schemaVersion)
                    + "; this module understands schemaVersion " + SecurityFeed.SUPPORTED_SCHEMA_VERSION);
        }
        int[] skipped = {0};
        List<Advisory> advisories = new ArrayList<>();
        JSONArray advisoryArray = optArray(root, "advisories");
        for (int i = 0; i < advisoryArray.length() && advisories.size() < MAX_ENTRIES; i++) {
            Advisory advisory = advisory(advisoryArray.opt(i), skipped);
            if (advisory == null) {
                skipped[0]++;
            } else {
                advisories.add(advisory);
            }
        }
        List<FlaggedVersion> flags = new ArrayList<>();
        JSONArray flagArray = optArray(root, "flaggedVersions");
        for (int i = 0; i < flagArray.length() && flags.size() < MAX_ENTRIES; i++) {
            FlaggedVersion flag = flag(flagArray.opt(i));
            if (flag == null) {
                skipped[0]++;
            } else {
                flags.add(flag);
            }
        }
        return new SecurityFeed(advisories, flags, skipped[0]);
    }

    private static JSONObject parseObject(String json) throws FeedFormatException {
        if (json == null || json.trim().isEmpty()) {
            throw new FeedFormatException("Empty document");
        }
        try {
            Object value = new JSONTokener(json).nextValue();
            if (!(value instanceof JSONObject)) {
                throw new FeedFormatException("The security feed is not a JSON object");
            }
            return (JSONObject) value;
        } catch (JSONException e) {
            throw new FeedFormatException("The security feed is not valid JSON", e);
        } catch (StackOverflowError e) {
            // an org.json without a nesting limit recurses on deeply nested input
            throw new FeedFormatException("The security feed is nested too deeply");
        }
    }

    private static JSONArray optArray(JSONObject root, String key) throws FeedFormatException {
        Object value = root.opt(key);
        if (value == null) {
            return new JSONArray();
        }
        if (!(value instanceof JSONArray)) {
            throw new FeedFormatException("'" + key + "' is not a JSON array");
        }
        return (JSONArray) value;
    }

    private static Advisory advisory(Object value, int[] skipped) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) value;
        String id = Json.string(object, "id", MAX_ID);
        JSONArray fixArray = object.optJSONArray("fixes");
        if (id == null || fixArray == null) {
            return null;
        }
        List<AdvisoryFix> fixes = new ArrayList<>();
        for (int i = 0; i < fixArray.length() && fixes.size() < MAX_ITEMS; i++) {
            AdvisoryFix fix = fix(fixArray.opt(i));
            if (fix == null) {
                skipped[0]++;
            } else {
                fixes.add(fix);
            }
        }
        if (fixes.isEmpty()) {
            return null;
        }
        String title = Json.string(object, "title", MAX_TEXT);
        return new Advisory(id, title == null ? id : title, Severity.parse(Json.string(object, SEVERITY, MAX_ID)),
                Json.number(object, "cvss"), Json.strings(object, "cves", MAX_ITEMS, MAX_ID),
                webUrl(Json.string(object, "url", MAX_URL)), Json.string(object, "published", MAX_ID), fixes);
    }

    private static AdvisoryFix fix(Object value) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) value;
        String component = Json.string(object, "component", MAX_NAME);
        List<String> fixedVersions = new ArrayList<>();
        for (String version : Json.strings(object, "fixedVersions", MAX_ITEMS, MAX_VERSION)) {
            if (Versions.isWellFormed(version)) {
                fixedVersions.add(version);
            }
        }
        // a fix with no comparable version would read as "not affected": it is dropped and counted instead
        if (component == null || fixedVersions.isEmpty()) {
            return null;
        }
        return new AdvisoryFix(component, Json.string(object, GROUP_ID, MAX_NAME), fixedVersions);
    }

    private static FlaggedVersion flag(Object value) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) value;
        String name = Json.string(object, "name", MAX_NAME);
        String version = Json.string(object, "version", MAX_VERSION);
        if (name == null || version == null) {
            return null;
        }
        return new FlaggedVersion(Json.string(object, GROUP_ID, MAX_NAME), name, version,
                Severity.parse(Json.string(object, SEVERITY, MAX_ID)),
                Json.strings(object, "references", MAX_ITEMS, MAX_URL), Json.string(object, "note", MAX_TEXT));
    }

    /** Keeps a URL only when it is an http(s) URL; the API and the UI further expose links only for https. */
    private static String webUrl(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https:") || lower.startsWith("http:") ? url : null;
    }

    private static String describe(Object schemaVersion) {
        if (schemaVersion == null || JSONObject.NULL.equals(schemaVersion)) {
            return "(missing)";
        }
        return UntrustedText.clean(String.valueOf(schemaVersion), 20) + (schemaVersion instanceof String ? " (text)" : "");
    }
}
