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

    static List<String> strings(JSONObject object, String key, int maxItems, int maxLength) {
        Object value = object.opt(key);
        if (!(value instanceof JSONArray)) {
            return Collections.emptyList();
        }
        JSONArray array = (JSONArray) value;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length() && result.size() < maxItems; i++) {
            Object item = array.opt(i);
            if (item instanceof String) {
                String cleaned = UntrustedText.clean((String) item, maxLength);
                if (cleaned != null) {
                    result.add(cleaned);
                }
            }
        }
        return result;
    }

    static Double number(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }
}
