package org.jahia.community.securityupdates.feed;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lenient readers over org.json values: a value of the wrong type reads as absent. Text is cleaned with
 * {@link UntrustedText}.
 */
final class Json {

    private Json() {
    }

    static String string(JSONObject object, String key, int maxLength) {
        Object value = object.opt(key);
        return value instanceof String ? UntrustedText.clean((String) value, maxLength) : null;
    }

    /**
     * @param truncated run when the array holds more items than {@code maxItems} strings were read from
     * @return the first {@code maxItems} non-empty strings of the array; items of another type are ignored
     */
    static List<String> strings(JSONObject object, String key, int maxItems, int maxLength, Runnable truncated) {
        Object value = object.opt(key);
        if (!(value instanceof JSONArray)) {
            return Collections.emptyList();
        }
        JSONArray array = (JSONArray) value;
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < array.length() && result.size() < maxItems) {
            Object item = array.opt(i);
            String cleaned = item instanceof String ? UntrustedText.clean((String) item, maxLength) : null;
            if (cleaned != null) {
                result.add(cleaned);
            }
            i++;
        }
        if (i < array.length()) {
            truncated.run();
        }
        return result;
    }

    static Double number(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }
}
