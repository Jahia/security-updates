package org.jahia.community.securityupdates.service;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link Dictionary} over a {@link LinkedHashMap}, for {@code Configuration.update}. OSGi R7 (Jahia 8.2) has
 * no {@code FrameworkUtil.asDictionary}; this avoids the synchronized {@code Hashtable}. Not thread-safe: built
 * and handed over by one thread.
 */
final class MapDictionary extends Dictionary<String, Object> {

    private final Map<String, Object> map = new LinkedHashMap<>();

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Enumeration<String> keys() {
        return Collections.enumeration(map.keySet());
    }

    @Override
    public Enumeration<Object> elements() {
        return Collections.enumeration(map.values());
    }

    @Override
    public Object get(Object key) {
        return map.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return map.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return map.remove(key);
    }
}
