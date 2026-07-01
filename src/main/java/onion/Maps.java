package onion;

import java.util.HashMap;
import java.util.Map;

/**
 * Map utility functions for Onion programs.
 * All methods are static and can be called using Maps::methodName() syntax.
 */
public final class Maps {
    private Maps() {
        // Prevent instantiation
    }

    /**
     * Returns the value for the given key, or defaultValue if the key is absent.
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map == null) return defaultValue;
        V value = map.get(key);
        return value != null ? value : (map.containsKey(key) ? value : defaultValue);
    }

    /**
     * Returns a new map containing only the entries whose keys pass the predicate.
     */
    public static <K, V> Map<K, V> filterKeys(Map<K, V> map, Function1<K, Boolean> predicate) {
        Map<K, V> result = new HashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Boolean keep = predicate.call(entry.getKey());
            if (keep != null && keep) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Returns a new map containing only the entries whose values pass the predicate.
     */
    public static <K, V> Map<K, V> filterValues(Map<K, V> map, Function1<V, Boolean> predicate) {
        Map<K, V> result = new HashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Boolean keep = predicate.call(entry.getValue());
            if (keep != null && keep) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Transforms each value in the map using the given function.
     */
    public static <K, V, R> Map<K, R> mapValues(Map<K, V> map, Function1<V, R> mapper) {
        Map<K, R> result = new HashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(entry.getKey(), mapper.call(entry.getValue()));
        }
        return result;
    }

    /**
     * Merges two maps into a new map. Values from the second map take precedence.
     */
    public static <K, V> Map<K, V> merge(Map<K, V> first, Map<K, V> second) {
        Map<K, V> result = new HashMap<>();
        if (first != null) result.putAll(first);
        if (second != null) result.putAll(second);
        return result;
    }

    /**
     * Creates a new empty mutable HashMap.
     */
    public static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }
}
