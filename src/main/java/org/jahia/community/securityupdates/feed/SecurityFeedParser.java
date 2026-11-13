package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.matching.Versions;
import org.jahia.community.securityupdates.model.Advisory;
import org.jahia.community.securityupdates.model.AdvisoryFix;
import org.jahia.community.securityupdates.model.FlaggedVersion;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Parses {@code modules-repository.securityUpdates.json}. The document must be a JSON object with
 * {@code schemaVersion} 1, nested at most {@link JsonNesting#MAX_DEPTH} levels; anything else fails closed with
 * a {@link FeedFormatException}. Inside a supported document, incomplete entries are skipped and counted, and
 * unknown keys are ignored. All text is cleaned and bounded. Versions are checked as the feed gives them (trimmed),
 * before any cut: a version longer than {@link #MAX_VERSION} is malformed. At most {@link #MAX_ENTRIES}
 * advisories and as many flagged versions are read, and at most {@link #MAX_ITEMS} fixes per advisory, fixed versions
 * per fix, CVEs per advisory and references per flagged version; a longer list marks the feed truncated, so the
 * report is never complete. A malformed fixed version is dropped and counted as skipped, even when other fixed
 * versions of its fix are kept.
 *
 * <p>A fix entry may give {@code introducedVersion}, the first version that has the issue: a malformed one is
 * ignored (the fix entry is kept, so versions below it are still reported) and counted as skipped.
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
    private static final String INTRODUCED_VERSION = "introducedVersion";

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
            // the value read is not repeated: it is remote text
            throw new FeedFormatException(StoreErrorDetail.SCHEMA_UNSUPPORTED, "Missing or unsupported schemaVersion; "
                    + "this module understands schemaVersion " + SecurityFeed.SUPPORTED_SCHEMA_VERSION);
        }
        Skipped skipped = new Skipped();
        List<Advisory> advisories = entries(optArray(root, "advisories"), value -> advisory(value, skipped), skipped);
        List<FlaggedVersion> flags = entries(optArray(root, "flaggedVersions"), value -> flag(value, skipped), skipped);
        return new SecurityFeed(advisories, flags, skipped.count, skipped.truncated);
    }

    /**
     * Reads at most {@link #MAX_ENTRIES} entries of a list; an entry the parser returns null for is counted as
     * skipped. A list with entries left unread marks the feed truncated.
     */
    private static <T> List<T> entries(JSONArray array, Function<Object, T> parser, Skipped skipped) {
        List<T> entries = new ArrayList<>();
        int i = 0;
        while (i < array.length() && entries.size() < MAX_ENTRIES) {
            T entry = parser.apply(array.opt(i));
            if (entry == null) {
                skipped.add();
            } else {
                entries.add(entry);
            }
            i++;
        }
        if (i < array.length()) {
            skipped.truncate();
        }
        return entries;
    }

    /** Count of the entries ignored while parsing one document, and whether entries were left unread. */
    private static final class Skipped {
        private int count;
        private boolean truncated;

        void add() {
            count++;
        }

        void add(int entries) {
            count += entries;
        }

        void truncate() {
            truncated = true;
        }
    }

    private static JSONObject parseObject(String json) throws FeedFormatException {
        if (json == null || json.trim().isEmpty()) {
            throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "Empty document");
        }
        JsonNesting.check(json, "The security feed");
        try {
            Object value = new JSONTokener(json).nextValue();
            if (!(value instanceof JSONObject)) {
                throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "The security feed is not a JSON object");
            }
            return (JSONObject) value;
        } catch (JSONException e) {
            throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "The security feed is not valid JSON", e);
        }
    }

    private static JSONArray optArray(JSONObject root, String key) throws FeedFormatException {
        Object value = root.opt(key);
        if (value == null) {
            return new JSONArray();
        }
        if (!(value instanceof JSONArray)) {
            throw new FeedFormatException(StoreErrorDetail.INVALID_JSON, "'" + key + "' is not a JSON array");
        }
        return (JSONArray) value;
    }

    private static Advisory advisory(Object value, Skipped skipped) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) value;
        String id = Json.string(object, "id", MAX_ID);
        JSONArray fixArray = object.optJSONArray("fixes");
        if (id == null || fixArray == null) {
            return null;
        }
        List<AdvisoryFix> fixes = fixes(fixArray, skipped);
        if (fixes.isEmpty()) {
            return null;
        }
        String title = Json.string(object, "title", MAX_TEXT);
        return new Advisory(id, title == null ? id : title, Severity.parse(Json.string(object, SEVERITY, MAX_ID)),
                Json.number(object, "cvss"), Json.strings(object, "cves", MAX_ITEMS, MAX_ID, skipped::truncate),
                webUrl(Json.string(object, "url", MAX_URL)), Json.string(object, "published", MAX_ID), fixes);
    }

    /** Reads at most {@link #MAX_ITEMS} fix entries; a longer list marks the feed truncated. */
    private static List<AdvisoryFix> fixes(JSONArray fixArray, Skipped skipped) {
        List<AdvisoryFix> fixes = new ArrayList<>();
        int i = 0;
        while (i < fixArray.length() && fixes.size() < MAX_ITEMS) {
            AdvisoryFix fix = fix(fixArray.opt(i), skipped);
            if (fix == null) {
                skipped.add();
            } else {
                fixes.add(fix);
            }
            i++;
        }
        if (i < fixArray.length()) {
            skipped.truncate();
        }
        return fixes;
    }

    private static AdvisoryFix fix(Object value, Skipped skipped) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) value;
        String component = Json.string(object, "component", MAX_NAME);
        JSONArray versions = object.optJSONArray("fixedVersions");
        List<String> fixedVersions = new ArrayList<>();
        int malformed = versions == null ? 0 : fixedVersions(versions, fixedVersions, skipped);
        // a fix with no comparable version would read as "not affected": it is dropped and counted (once) instead
        if (component == null || fixedVersions.isEmpty()) {
            return null;
        }
        // a malformed fixed version dropped from a kept fix could hide the line the installed version is on (and
        // "installed >= every fixed version" would then read as "not affected"): each one is counted
        skipped.add(malformed);
        return new AdvisoryFix(component, Json.string(object, GROUP_ID, MAX_NAME), fixedVersions,
                introducedVersion(object, skipped));
    }

    /**
     * Adds the well-formed versions among the first {@link #MAX_ITEMS} of {@code versions} to {@code fixedVersions};
     * a longer list marks the feed truncated.
     *
     * @return the number of malformed versions dropped
     */
    private static int fixedVersions(JSONArray versions, List<String> fixedVersions, Skipped skipped) {
        int malformed = 0;
        int i = 0;
        while (i < versions.length() && fixedVersions.size() < MAX_ITEMS) {
            String version = version(versions.opt(i));
            if (version == null) {
                malformed++;
            } else {
                fixedVersions.add(version);
            }
            i++;
        }
        if (i < versions.length()) {
            skipped.truncate();
        }
        return malformed;
    }

    /**
     * @return the well-formed {@code introducedVersion} of a fix entry, or null; a malformed one is counted as
     * skipped and ignored, which reports every version below the fixes (over-reporting rather than hiding)
     */
    private static String introducedVersion(JSONObject fix, Skipped skipped) {
        Object raw = fix.opt(INTRODUCED_VERSION);
        if (raw == null || JSONObject.NULL.equals(raw)) {
            return null;
        }
        String version = version(raw);
        if (version == null) {
            skipped.add();
        }
        return version;
    }

    /**
     * @return the version as the feed gives it, trimmed, when it is a well-formed version; null otherwise. Checked
     * before any cut, so that an overlong version is malformed rather than cut to a valid prefix.
     */
    private static String version(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String version = ((String) value).trim();
        return version.length() <= MAX_VERSION && Versions.isWellFormed(version) ? version : null;
    }

    private static FlaggedVersion flag(Object value, Skipped skipped) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) value;
        String name = Json.string(object, "name", MAX_NAME);
        String version = version(object.opt("version"));
        if (name == null || version == null) {
            return null;
        }
        return new FlaggedVersion(Json.string(object, GROUP_ID, MAX_NAME), name, version,
                Severity.parse(Json.string(object, SEVERITY, MAX_ID)),
                Json.strings(object, "references", MAX_ITEMS, MAX_URL, skipped::truncate), Json.string(object, "note", MAX_TEXT));
    }

    /** Keeps a URL only when it is an http(s) URL; the API and the UI further expose links only for https. */
    private static String webUrl(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https:") || lower.startsWith("http:") ? url : null;
    }
}
