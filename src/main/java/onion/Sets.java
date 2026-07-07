package onion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Set utility functions for Onion programs.
 * All methods are static and can be called using Sets::methodName() syntax.
 *
 * <p>Result sets preserve insertion order (backed by {@link LinkedHashSet}).
 */
public final class Sets {
    private Sets() {
        // Prevent instantiation
    }

    /**
     * Creates an empty mutable set (insertion-ordered).
     */
    public static <T> Set<T> newSet() {
        return new LinkedHashSet<>();
    }

    /**
     * Creates a mutable set from the given elements (distinct, insertion-ordered).
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
     * Creates a set from a list (distinct, preserving first-seen order).
     */
    public static <T> Set<T> fromList(List<T> items) {
        Set<T> result = new LinkedHashSet<>();
        if (items != null) result.addAll(items);
        return result;
    }

    /**
     * Returns the set's elements as a list, in iteration order.
     */
    public static <T> List<T> toList(Set<T> set) {
        List<T> result = new ArrayList<>();
        if (set != null) result.addAll(set);
        return result;
    }

    /**
     * Returns the union of two sets (all distinct elements from both).
     */
    public static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }

    /**
     * Returns the intersection of two sets (elements present in both).
     */
    public static <T> Set<T> intersection(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>();
        if (a == null || b == null) return result;
        for (T element : a) {
            if (b.contains(element)) result.add(element);
        }
        return result;
    }

    /**
     * Returns the difference a - b (elements in a but not in b).
     */
    public static <T> Set<T> difference(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>();
        if (a == null) return result;
        for (T element : a) {
            if (b == null || !b.contains(element)) result.add(element);
        }
        return result;
    }

    /**
     * Returns the symmetric difference: elements in exactly one of the two sets.
     */
    public static <T> Set<T> symmetricDifference(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>();
        if (a != null) {
            for (T element : a) {
                if (b == null || !b.contains(element)) result.add(element);
            }
        }
        if (b != null) {
            for (T element : b) {
                if (a == null || !a.contains(element)) result.add(element);
            }
        }
        return result;
    }

    /**
     * Returns true if the first set contains all elements of the second set.
     */
    public static <T> boolean containsAll(Set<T> container, Set<T> subset) {
        if (subset == null || subset.isEmpty()) return true;
        if (container == null) return false;
        return container.containsAll(subset);
    }

    /**
     * True if every element of {@code sub} is in {@code sup}.
     */
    public static <T> boolean isSubsetOf(Set<T> sub, Set<T> sup) {
        return containsAll(sup, sub);
    }

    /**
     * True if every element of {@code sub} is in {@code sup} (superset view).
     */
    public static <T> boolean isSupersetOf(Set<T> sup, Set<T> sub) {
        return containsAll(sup, sub);
    }

    /**
     * True if the two sets share no elements.
     */
    public static <T> boolean isDisjoint(Set<T> a, Set<T> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return true;
        for (T element : a) {
            if (b.contains(element)) return false;
        }
        return true;
    }

    /**
     * Transforms each element, collecting distinct results (insertion-ordered).
     */
    public static <T, R> Set<R> map(Set<T> set, Function1<T, R> mapper) {
        Set<R> result = new LinkedHashSet<>();
        if (set == null) return result;
        for (T element : set) {
            result.add(mapper.call(element));
        }
        return result;
    }

    /**
     * Returns a new set with only the elements that pass the predicate.
     */
    public static <T> Set<T> filter(Set<T> set, Function1<T, Boolean> predicate) {
        Set<T> result = new LinkedHashSet<>();
        if (set == null) return result;
        for (T element : set) {
            Boolean keep = predicate.call(element);
            if (keep != null && keep) result.add(element);
        }
        return result;
    }

    /**
     * Applies {@code action} to every element, in iteration order.
     */
    public static <T> void forEach(Set<T> set, Function1<T, ?> action) {
        if (set == null) return;
        for (T element : set) {
            action.call(element);
        }
    }

    /**
     * Counts the elements that satisfy the predicate.
     */
    public static <T> int count(Set<T> set, Function1<T, Boolean> predicate) {
        int n = 0;
        if (set == null) return 0;
        for (T element : set) {
            Boolean hit = predicate.call(element);
            if (hit != null && hit) n++;
        }
        return n;
    }

    /**
     * True if at least one element satisfies the predicate.
     */
    public static <T> boolean any(Set<T> set, Function1<T, Boolean> predicate) {
        if (set == null) return false;
        for (T element : set) {
            Boolean hit = predicate.call(element);
            if (hit != null && hit) return true;
        }
        return false;
    }

    /**
     * True if every element satisfies the predicate (vacuously true when empty).
     */
    public static <T> boolean all(Set<T> set, Function1<T, Boolean> predicate) {
        if (set == null) return true;
        for (T element : set) {
            Boolean hit = predicate.call(element);
            if (hit == null || !hit) return false;
        }
        return true;
    }

    /**
     * Returns the first element satisfying the predicate, or {@code null} if none.
     */
    public static <T> T find(Set<T> set, Function1<T, Boolean> predicate) {
        if (set == null) return null;
        for (T element : set) {
            Boolean hit = predicate.call(element);
            if (hit != null && hit) return element;
        }
        return null;
    }
}
