package onion;

import java.util.*;

/**
 * Collections module providing immutable collection utilities for Onion programs.
 * All methods are static and can be called using Colls::methodName() syntax.
 *
 * Example usage:
 * <pre>
 * val list = Colls::listOf(1, 2, 3, 4, 5)
 * val doubled = Colls::map(list, (x) => x * 2)
 * val evens = Colls::filter(list, (x) => x % 2 == 0)
 * val sum = Colls::reduce(list, 0, (a, b) => a + b)
 *
 * val set = Colls::setOf("a", "b", "c")
 * val map = Colls::mapOf(Colls::entry("name", "Alice"), Colls::entry("age", "30"))
 * </pre>
 */
public final class Colls {
    private Colls() {
        // Prevent instantiation
    }

    // ===== List Creation =====

    /**
     * Creates an immutable list from the given elements (varargs).
     * @param elements the elements for the list
     * @param <T> the element type
     * @return an immutable list containing the given elements
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        return List.of(elements);
    }

    // Non-varargs overloads for Onion compatibility
    public static <T> List<T> listOf0() {
        return List.of();
    }

    public static <T> List<T> listOf1(T a) {
        return List.of(a);
    }

    public static <T> List<T> listOf2(T a, T b) {
        return List.of(a, b);
    }

    public static <T> List<T> listOf3(T a, T b, T c) {
        return List.of(a, b, c);
    }

    public static <T> List<T> listOf4(T a, T b, T c, T d) {
        return List.of(a, b, c, d);
    }

    public static <T> List<T> listOf5(T a, T b, T c, T d, T e) {
        return List.of(a, b, c, d, e);
    }

    public static <T> List<T> listOf6(T a, T b, T c, T d, T e, T f) {
        return List.of(a, b, c, d, e, f);
    }

    /**
     * Creates an empty immutable list.
     * @param <T> the element type
     * @return an empty immutable list
     */
    public static <T> List<T> emptyList() {
        return List.of();
    }

    /**
     * Creates a mutable ArrayList from the given elements.
     * @param elements the elements for the list
     * @param <T> the element type
     * @return a mutable ArrayList containing the given elements
     */
    @SafeVarargs
    public static <T> ArrayList<T> mutableListOf(T... elements) {
        return new ArrayList<>(Arrays.asList(elements));
    }

    /**
     * Creates a list containing a range of integers from start (inclusive) to end (exclusive).
     * @param start the starting value (inclusive)
     * @param end the ending value (exclusive)
     * @return a list containing integers from start to end-1
     */
    public static List<Integer> range(int start, int end) {
        List<Integer> result = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            result.add(i);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Creates a list containing a range of integers from start (inclusive) to end (exclusive) with a step.
     * @param start the starting value (inclusive)
     * @param end the ending value (exclusive)
     * @param step the step between consecutive values
     * @return a list containing integers from start to end-1 with the given step
     */
    public static List<Integer> rangeWithStep(int start, int end, int step) {
        if (step == 0) throw new IllegalArgumentException("Step cannot be zero");
        List<Integer> result = new ArrayList<>();
        if (step > 0) {
            for (int i = start; i < end; i += step) {
                result.add(i);
            }
        } else {
            for (int i = start; i > end; i += step) {
                result.add(i);
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }

    // ===== Set Creation =====

    /**
     * Creates an immutable set from the given elements.
     * @param elements the elements for the set
     * @param <T> the element type
     * @return an immutable set containing the given elements
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        return Set.of(elements);
    }

    // Non-varargs overloads for Onion compatibility
    public static <T> Set<T> setOf0() {
        return Set.of();
    }

    public static <T> Set<T> setOf1(T a) {
        return Set.of(a);
    }

    public static <T> Set<T> setOf2(T a, T b) {
        return Set.of(a, b);
    }

    public static <T> Set<T> setOf3(T a, T b, T c) {
        return Set.of(a, b, c);
    }

    public static <T> Set<T> setOf4(T a, T b, T c, T d) {
        return Set.of(a, b, c, d);
    }

    public static <T> Set<T> setOf5(T a, T b, T c, T d, T e) {
        return Set.of(a, b, c, d, e);
    }

    /**
     * Creates an empty immutable set.
     * @param <T> the element type
     * @return an empty immutable set
     */
    public static <T> Set<T> emptySet() {
        return Set.of();
    }

    /**
     * Creates a mutable HashSet from the given elements.
     * @param elements the elements for the set
     * @param <T> the element type
     * @return a mutable HashSet containing the given elements
     */
    @SafeVarargs
    public static <T> HashSet<T> mutableSetOf(T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    // Non-varargs overloads for Onion compatibility
    public static <T> HashSet<T> mutableSetOf0() {
        return new HashSet<>();
    }

    public static <T> HashSet<T> mutableSetOf1(T a) {
        HashSet<T> set = new HashSet<>();
        set.add(a);
        return set;
    }

    public static <T> HashSet<T> mutableSetOf2(T a, T b) {
        HashSet<T> set = new HashSet<>();
        set.add(a);
        set.add(b);
        return set;
    }

    public static <T> HashSet<T> mutableSetOf3(T a, T b, T c) {
        HashSet<T> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(c);
        return set;
    }

    // ===== Map Creation =====

    /**
     * Creates a map entry (key-value pair) for use with mapOf.
     * @param key the key
     * @param value the value
     * @param <K> the key type
     * @param <V> the value type
     * @return a map entry
     */
    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    /**
     * Creates an immutable map from the given entries.
     * @param entries the entries for the map
     * @param <K> the key type
     * @param <V> the value type
     * @return an immutable map containing the given entries
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Map.Entry<K, V>... entries) {
        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    // Non-varargs overloads for Onion compatibility
    public static <K, V> Map<K, V> mapOf0() {
        return Map.of();
    }

    public static <K, V> Map<K, V> mapOf1(Map.Entry<K, V> e1) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(e1.getKey(), e1.getValue());
        return java.util.Collections.unmodifiableMap(result);
    }

    public static <K, V> Map<K, V> mapOf2(Map.Entry<K, V> e1, Map.Entry<K, V> e2) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(e1.getKey(), e1.getValue());
        result.put(e2.getKey(), e2.getValue());
        return java.util.Collections.unmodifiableMap(result);
    }

    public static <K, V> Map<K, V> mapOf3(Map.Entry<K, V> e1, Map.Entry<K, V> e2, Map.Entry<K, V> e3) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(e1.getKey(), e1.getValue());
        result.put(e2.getKey(), e2.getValue());
        result.put(e3.getKey(), e3.getValue());
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Creates an empty immutable map.
     * @param <K> the key type
     * @param <V> the value type
     * @return an empty immutable map
     */
    public static <K, V> Map<K, V> emptyMap() {
        return Map.of();
    }

    /**
     * Creates a mutable HashMap from the given entries.
     * @param entries the entries for the map
     * @param <K> the key type
     * @param <V> the value type
     * @return a mutable HashMap containing the given entries
     */
    @SafeVarargs
    public static <K, V> HashMap<K, V> mutableMapOf(Map.Entry<K, V>... entries) {
        HashMap<K, V> result = new HashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ===== List Operations =====

    /**
     * Transforms each element of a list using the given function.
     * @param list the input list
     * @param f the transformation function
     * @param <T> the input element type
     * @param <U> the output element type
     * @return a new list with transformed elements
     */
    public static <T, U> List<U> map(List<T> list, Function1<T, U> f) {
        List<U> result = new ArrayList<>(list.size());
        for (T element : list) {
            result.add(f.call(element));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Filters elements of a list that match the given predicate.
     * @param list the input list
     * @param predicate the filter predicate
     * @param <T> the element type
     * @return a new list containing only elements that match the predicate
     */
    public static <T> List<T> filter(List<T> list, Function1<T, Boolean> predicate) {
        List<T> result = new ArrayList<>();
        for (T element : list) {
            if (predicate.call(element)) {
                result.add(element);
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Reduces a list to a single value using the given function.
     * @param list the input list
     * @param initial the initial value
     * @param f the reduction function
     * @param <T> the element type
     * @param <U> the result type
     * @return the reduced value
     */
    public static <T, U> U reduce(List<T> list, U initial, Function2<U, T, U> f) {
        U result = initial;
        for (T element : list) {
            result = f.call(result, element);
        }
        return result;
    }

    /**
     * Performs an action for each element in a list.
     * @param list the input list
     * @param action the action to perform
     * @param <T> the element type
     */
    public static <T> void forEach(List<T> list, Function1<T, ?> action) {
        for (T element : list) {
            action.call(element);
        }
    }

    /**
     * Returns the first element of a list, or null if empty.
     * @param list the input list
     * @param <T> the element type
     * @return the first element, or null if the list is empty
     */
    public static <T> T first(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Returns the last element of a list, or null if empty.
     * @param list the input list
     * @param <T> the element type
     * @return the last element, or null if the list is empty
     */
    public static <T> T last(List<T> list) {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    /**
     * Returns the first element matching the predicate, or null if none found.
     * @param list the input list
     * @param predicate the search predicate
     * @param <T> the element type
     * @return the first matching element, or null if not found
     */
    public static <T> T find(List<T> list, Function1<T, Boolean> predicate) {
        for (T element : list) {
            if (predicate.call(element)) {
                return element;
            }
        }
        return null;
    }

    /**
     * Checks if any element matches the predicate.
     * @param list the input list
     * @param predicate the predicate to test
     * @param <T> the element type
     * @return true if any element matches
     */
    public static <T> boolean any(List<T> list, Function1<T, Boolean> predicate) {
        for (T element : list) {
            if (predicate.call(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if all elements match the predicate.
     * @param list the input list
     * @param predicate the predicate to test
     * @param <T> the element type
     * @return true if all elements match
     */
    public static <T> boolean all(List<T> list, Function1<T, Boolean> predicate) {
        for (T element : list) {
            if (!predicate.call(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if no element matches the predicate.
     * @param list the input list
     * @param predicate the predicate to test
     * @param <T> the element type
     * @return true if no element matches
     */
    public static <T> boolean none(List<T> list, Function1<T, Boolean> predicate) {
        return !any(list, predicate);
    }

    /**
     * Counts elements matching the predicate.
     * @param list the input list
     * @param predicate the predicate to test
     * @param <T> the element type
     * @return the count of matching elements
     */
    public static <T> int count(List<T> list, Function1<T, Boolean> predicate) {
        int count = 0;
        for (T element : list) {
            if (predicate.call(element)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Takes the first n elements from a list.
     * @param list the input list
     * @param n the number of elements to take
     * @param <T> the element type
     * @return a new list containing the first n elements
     */
    public static <T> List<T> take(List<T> list, int n) {
        if (n <= 0) return List.of();
        if (n >= list.size()) return list;
        return java.util.Collections.unmodifiableList(new ArrayList<>(list.subList(0, n)));
    }

    /**
     * Drops the first n elements from a list.
     * @param list the input list
     * @param n the number of elements to drop
     * @param <T> the element type
     * @return a new list without the first n elements
     */
    public static <T> List<T> drop(List<T> list, int n) {
        if (n <= 0) return list;
        if (n >= list.size()) return List.of();
        return java.util.Collections.unmodifiableList(new ArrayList<>(list.subList(n, list.size())));
    }

    /**
     * Reverses a list.
     * @param list the input list
     * @param <T> the element type
     * @return a new list with elements in reverse order
     */
    public static <T> List<T> reverse(List<T> list) {
        List<T> result = new ArrayList<>(list);
        java.util.Collections.reverse(result);
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Sorts a list of comparable elements.
     * @param list the input list
     * @param <T> the element type (must be Comparable)
     * @return a new sorted list
     */
    public static <T extends Comparable<T>> List<T> sorted(List<T> list) {
        List<T> result = new ArrayList<>(list);
        java.util.Collections.sort(result);
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Returns distinct elements from a list (preserves order).
     * @param list the input list
     * @param <T> the element type
     * @return a new list with distinct elements
     */
    public static <T> List<T> distinct(List<T> list) {
        List<T> result = new ArrayList<>();
        Set<T> seen = new LinkedHashSet<>();
        for (T element : list) {
            if (seen.add(element)) {
                result.add(element);
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Flattens a list of lists into a single list.
     * @param list the input list of lists
     * @param <T> the element type
     * @return a flattened list
     */
    public static <T> List<T> flatten(List<List<T>> list) {
        List<T> result = new ArrayList<>();
        for (List<T> inner : list) {
            result.addAll(inner);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Maps each element to a list and flattens the result.
     * @param list the input list
     * @param f the mapping function
     * @param <T> the input element type
     * @param <U> the output element type
     * @return a flattened list
     */
    public static <T, U> List<U> flatMap(List<T> list, Function1<T, List<U>> f) {
        List<U> result = new ArrayList<>();
        for (T element : list) {
            result.addAll(f.call(element));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Zips two lists together into a list of pairs (as List of 2 elements).
     * @param list1 the first list
     * @param list2 the second list
     * @param <T> the first element type
     * @param <U> the second element type
     * @return a list of pairs (each pair is a List containing two elements)
     */
    public static <T, U> List<List<Object>> zip(List<T> list1, List<U> list2) {
        int size = java.lang.Math.min(list1.size(), list2.size());
        List<List<Object>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(List.of(list1.get(i), list2.get(i)));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Groups elements by a key function.
     * @param list the input list
     * @param keySelector the function to extract the group key
     * @param <T> the element type
     * @param <K> the key type
     * @return a map from keys to lists of elements
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function1<T, K> keySelector) {
        Map<K, List<T>> result = new LinkedHashMap<>();
        for (T element : list) {
            K key = keySelector.call(element);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(element);
        }
        // Make the inner lists immutable
        Map<K, List<T>> immutableResult = new LinkedHashMap<>();
        for (Map.Entry<K, List<T>> entry : result.entrySet()) {
            immutableResult.put(entry.getKey(), java.util.Collections.unmodifiableList(entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(immutableResult);
    }

    /**
     * Partitions a list into two lists based on a predicate.
     * @param list the input list
     * @param predicate the partition predicate
     * @param <T> the element type
     * @return a list of two lists: [matching, non-matching]
     */
    public static <T> List<List<T>> partition(List<T> list, Function1<T, Boolean> predicate) {
        List<T> matching = new ArrayList<>();
        List<T> nonMatching = new ArrayList<>();
        for (T element : list) {
            if (predicate.call(element)) {
                matching.add(element);
            } else {
                nonMatching.add(element);
            }
        }
        return List.of(
            java.util.Collections.unmodifiableList(matching),
            java.util.Collections.unmodifiableList(nonMatching)
        );
    }

    /**
     * Concatenates two lists.
     * @param list1 the first list
     * @param list2 the second list
     * @param <T> the element type
     * @return a new list containing all elements from both lists
     */
    public static <T> List<T> concat(List<T> list1, List<T> list2) {
        List<T> result = new ArrayList<>(list1.size() + list2.size());
        result.addAll(list1);
        result.addAll(list2);
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Returns the size of a collection.
     * @param collection the collection
     * @param <T> the element type
     * @return the size of the collection
     */
    public static <T> int size(Collection<T> collection) {
        return collection.size();
    }

    /**
     * Checks if a collection is empty.
     * @param collection the collection
     * @param <T> the element type
     * @return true if the collection is empty
     */
    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection.isEmpty();
    }

    /**
     * Checks if a collection is not empty.
     * @param collection the collection
     * @param <T> the element type
     * @return true if the collection is not empty
     */
    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return !collection.isEmpty();
    }

    /**
     * Checks if a collection contains the given element.
     * @param collection the collection
     * @param element the element to search for
     * @param <T> the element type
     * @return true if the collection contains the element
     */
    public static <T> boolean contains(Collection<T> collection, T element) {
        return collection.contains(element);
    }

    // ===== Map Operations =====

    /**
     * Gets a value from a map, or returns null if not found.
     * @param map the map
     * @param key the key
     * @param <K> the key type
     * @param <V> the value type
     * @return the value, or null if not found
     */
    public static <K, V> V get(Map<K, V> map, K key) {
        return map.get(key);
    }

    /**
     * Gets a value from a map, or returns a default if not found.
     * @param map the map
     * @param key the key
     * @param defaultValue the default value
     * @param <K> the key type
     * @param <V> the value type
     * @return the value, or defaultValue if not found
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * Returns the keys of a map as a list.
     * @param map the map
     * @param <K> the key type
     * @param <V> the value type
     * @return a list of keys
     */
    public static <K, V> List<K> keys(Map<K, V> map) {
        return java.util.Collections.unmodifiableList(new ArrayList<>(map.keySet()));
    }

    /**
     * Returns the values of a map as a list.
     * @param map the map
     * @param <K> the key type
     * @param <V> the value type
     * @return a list of values
     */
    public static <K, V> List<V> values(Map<K, V> map) {
        return java.util.Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    /**
     * Checks if a map contains the given key.
     * @param map the map
     * @param key the key to search for
     * @param <K> the key type
     * @param <V> the value type
     * @return true if the map contains the key
     */
    public static <K, V> boolean containsKey(Map<K, V> map, K key) {
        return map.containsKey(key);
    }

    /**
     * Transforms map values using the given function.
     * @param map the input map
     * @param f the transformation function
     * @param <K> the key type
     * @param <V> the input value type
     * @param <U> the output value type
     * @return a new map with transformed values
     */
    public static <K, V, U> Map<K, U> mapValues(Map<K, V> map, Function1<V, U> f) {
        Map<K, U> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(entry.getKey(), f.call(entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Filters map entries that match the given predicate.
     * @param map the input map
     * @param predicate the filter predicate (receives key and value)
     * @param <K> the key type
     * @param <V> the value type
     * @return a new map containing only matching entries
     */
    public static <K, V> Map<K, V> filterMap(Map<K, V> map, Function2<K, V, Boolean> predicate) {
        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (predicate.call(entry.getKey(), entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    // ===== Set Operations =====

    /**
     * Returns the union of two sets.
     * @param set1 the first set
     * @param set2 the second set
     * @param <T> the element type
     * @return a new set containing elements from both sets
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        Set<T> result = new LinkedHashSet<>(set1);
        result.addAll(set2);
        return java.util.Collections.unmodifiableSet(result);
    }

    /**
     * Returns the intersection of two sets.
     * @param set1 the first set
     * @param set2 the second set
     * @param <T> the element type
     * @return a new set containing elements present in both sets
     */
    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
        Set<T> result = new LinkedHashSet<>(set1);
        result.retainAll(set2);
        return java.util.Collections.unmodifiableSet(result);
    }

    /**
     * Returns the difference of two sets (elements in set1 but not in set2).
     * @param set1 the first set
     * @param set2 the second set
     * @param <T> the element type
     * @return a new set containing elements in set1 but not in set2
     */
    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        Set<T> result = new LinkedHashSet<>(set1);
        result.removeAll(set2);
        return java.util.Collections.unmodifiableSet(result);
    }

    /**
     * Converts a list to a set.
     * @param list the input list
     * @param <T> the element type
     * @return a set containing unique elements from the list
     */
    public static <T> Set<T> toSet(List<T> list) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(list));
    }

    /**
     * Converts a set to a list.
     * @param set the input set
     * @param <T> the element type
     * @return a list containing elements from the set
     */
    public static <T> List<T> toList(Set<T> set) {
        return java.util.Collections.unmodifiableList(new ArrayList<>(set));
    }
}
