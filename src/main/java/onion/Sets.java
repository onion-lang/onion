package onion;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Set utility functions for Onion programs.
 * All methods are static and can be called using Sets::methodName() syntax.
 */
public final class Sets {
    private Sets() {
        // Prevent instantiation
    }

    /**
     * Creates an empty mutable HashSet.
     */
    public static <T> Set<T> newSet() {
        return new HashSet<>();
    }

    /**
     * Creates a mutable HashSet from the given elements.
     */
    @SafeVarargs
    public static <T> Set<T> of(T... elements) {
        Set<T> result = new LinkedHashSet<>();
        if (elements != null) {
            for (T element : elements) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * Returns the union of two sets (all distinct elements from both).
     */
    public static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.addAll(b);
        return result;
    }

    /**
     * Returns the intersection of two sets (elements present in both).
     */
    public static <T> Set<T> intersection(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    /**
     * Returns the difference a - b (elements in a but not in b).
     */
    public static <T> Set<T> difference(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    /**
     * Returns true if the first set contains all elements of the second set.
     */
    public static <T> boolean containsAll(Set<T> container, Set<T> subset) {
        return container.containsAll(subset);
    }
}
