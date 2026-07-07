package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map utility functions for Onion programs.
 * All methods are static and can be called using Maps::methodName() syntax.
 *
 * <p>Result maps preserve insertion order (backed by {@link LinkedHashMap}), so
 * transformations keep a predictable iteration order.
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
        return map.containsKey(key) ? map.get(key) : defaultValue;
    }

    /**
     * Returns the value for the given key, or the result of {@code supplier} if
     * the key is absent. The supplier is only invoked when the key is missing.
     */
    public static <K, V> V getOrElse(Map<K, V> map, K key, Function0<V> supplier) {
        if (map != null && map.containsKey(key)) return map.get(key);
        return supplier.call();
    }

    /**
     * Returns a new map containing only the entries whose keys pass the predicate.
     */
    public static <K, V> Map<K, V> filterKeys(Map<K, V> map, Function1<K, Boolean> predicate) {
        Map<K, V> result = new LinkedHashMap<>();
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
        Map<K, V> result = new LinkedHashMap<>();
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
     * Returns a new map containing only the entries for which the (key, value)
     * predicate holds.
     */
    public static <K, V> Map<K, V> filter(Map<K, V> map, Function2<K, V, Boolean> predicate) {
        Map<K, V> result = new LinkedHashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Boolean keep = predicate.call(entry.getKey(), entry.getValue());
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
        Map<K, R> result = new LinkedHashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(entry.getKey(), mapper.call(entry.getValue()));
        }
        return result;
    }

    /**
     * Transforms each key in the map using the given function. If two keys map to
     * the same new key, the later entry (in iteration order) wins.
     */
    public static <K, V, R> Map<R, V> mapKeys(Map<K, V> map, Function1<K, R> mapper) {
        Map<R, V> result = new LinkedHashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(mapper.call(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Applies {@code action} to every (key, value) pair, in iteration order.
     */
    public static <K, V> void forEach(Map<K, V> map, Function2<K, V, ?> action) {
        if (map == null) return;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            action.call(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Maps each (key, value) pair to a value, collecting the results into a list
     * in iteration order.
     */
    public static <K, V, R> List<R> toList(Map<K, V> map, Function2<K, V, R> mapper) {
        List<R> result = new ArrayList<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.add(mapper.call(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Returns the keys as a list, in iteration order.
     */
    public static <K, V> List<K> keys(Map<K, V> map) {
        List<K> result = new ArrayList<>();
        if (map != null) result.addAll(map.keySet());
        return result;
    }

    /**
     * Returns the values as a list, in iteration order.
     */
    public static <K, V> List<V> values(Map<K, V> map) {
        List<V> result = new ArrayList<>();
        if (map != null) result.addAll(map.values());
        return result;
    }

    /**
     * Returns a new map with keys and values swapped. If two entries share a
     * value, the later entry (in iteration order) wins.
     */
    public static <K, V> Map<V, K> invert(Map<K, V> map) {
        Map<V, K> result = new LinkedHashMap<>();
        if (map == null) return result;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        return result;
    }

    /**
     * Groups the elements of {@code items} by the key produced by {@code keyOf},
     * preserving element order within each group.
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> items, Function1<T, K> keyOf) {
        Map<K, List<T>> result = new LinkedHashMap<>();
        if (items == null) return result;
        for (T item : items) {
            K key = keyOf.call(item);
            List<T> bucket = result.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                result.put(key, bucket);
            }
            bucket.add(item);
        }
        return result;
    }

    /**
     * Counts how many elements of {@code items} fall under each key produced by
     * {@code keyOf} — a frequency map.
     */
    public static <T, K> Map<K, Integer> countBy(List<T> items, Function1<T, K> keyOf) {
        Map<K, Integer> result = new LinkedHashMap<>();
        if (items == null) return result;
        for (T item : items) {
            K key = keyOf.call(item);
            Integer current = result.get(key);
            result.put(key, current == null ? 1 : current + 1);
        }
        return result;
    }

    /**
     * Counts the entries for which the (key, value) predicate holds.
     */
    public static <K, V> int count(Map<K, V> map, Function2<K, V, Boolean> predicate) {
        int n = 0;
        if (map == null) return 0;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Boolean hit = predicate.call(entry.getKey(), entry.getValue());
            if (hit != null && hit) n++;
        }
        return n;
    }

    /**
     * True if at least one entry satisfies the (key, value) predicate.
     */
    public static <K, V> boolean anyEntry(Map<K, V> map, Function2<K, V, Boolean> predicate) {
        if (map == null) return false;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Boolean hit = predicate.call(entry.getKey(), entry.getValue());
            if (hit != null && hit) return true;
        }
        return false;
    }

    /**
     * True if every entry satisfies the (key, value) predicate (vacuously true
     * for an empty map).
     */
    public static <K, V> boolean allEntries(Map<K, V> map, Function2<K, V, Boolean> predicate) {
        if (map == null) return true;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Boolean hit = predicate.call(entry.getKey(), entry.getValue());
            if (hit == null || !hit) return false;
        }
        return true;
    }

    /**
     * Returns a new map with the value at {@code key} replaced by
     * {@code updater(oldValue)}. If the key is absent the original map is copied
     * unchanged.
     */
    public static <K, V> Map<K, V> update(Map<K, V> map, K key, Function1<V, V> updater) {
        Map<K, V> result = new LinkedHashMap<>();
        if (map != null) result.putAll(map);
        if (result.containsKey(key)) {
            result.put(key, updater.call(result.get(key)));
        }
        return result;
    }

    /**
     * Merges two maps into a new map. Values from the second map take precedence.
     */
    public static <K, V> Map<K, V> merge(Map<K, V> first, Map<K, V> second) {
        Map<K, V> result = new LinkedHashMap<>();
        if (first != null) result.putAll(first);
        if (second != null) result.putAll(second);
        return result;
    }

    /**
     * Merges two maps into a new map, resolving a key present in both via
     * {@code combine(firstValue, secondValue)}.
     */
    public static <K, V> Map<K, V> mergeWith(Map<K, V> first, Map<K, V> second, Function2<V, V, V> combine) {
        Map<K, V> result = new LinkedHashMap<>();
        if (first != null) result.putAll(first);
        if (second != null) {
            for (Map.Entry<K, V> entry : second.entrySet()) {
                K key = entry.getKey();
                if (result.containsKey(key)) {
                    result.put(key, combine.call(result.get(key), entry.getValue()));
                } else {
                    result.put(key, entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * Creates a new empty mutable map (insertion-ordered).
     */
    public static <K, V> Map<K, V> newMap() {
        return new LinkedHashMap<>();
    }
}
