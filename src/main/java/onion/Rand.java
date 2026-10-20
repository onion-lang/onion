package onion;

import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Random number generation utilities.
 *
 * Usage:
 *   val n = Rand::nextInt(100);          // 0-99
 *   val d = Rand::nextDouble();          // 0.0-1.0
 *   val b = Rand::nextBoolean();         // true/false
 *   val item = Rand::choice(items);      // random element
 *   val shuffled = Rand::shuffle(items); // shuffled copy
 *   val id = Rand::uuid();               // UUID string
 */
public class Rand {

    // ========== Integer Random ==========

    /**
     * Returns a random int value.
     */
    public static int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    /**
     * Returns a random int value between 0 (inclusive) and bound (exclusive).
     */
    public static int nextInt(int bound) {
        try {
            return ThreadLocalRandom.current().nextInt(bound);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Rand::nextInt: bound must be positive, got " + bound, e);
        }
    }

    /**
     * Returns a random int value between min (inclusive) and max (exclusive).
     */
    public static int nextInt(int min, int max) {
        try {
            return ThreadLocalRandom.current().nextInt(min, max);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Rand::nextInt: max must be greater than min, got min=" + min + ", max=" + max, e);
        }
    }

    // ========== Long Random ==========

    /**
     * Returns a random long value.
     */
    public static long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    /**
     * Returns a random long value between 0 (inclusive) and bound (exclusive).
     */
    public static long nextLong(long bound) {
        try {
            return ThreadLocalRandom.current().nextLong(bound);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Rand::nextLong: bound must be positive, got " + bound, e);
        }
    }

    // ========== Double Random ==========

    /**
     * Returns a random double value between 0.0 (inclusive) and 1.0 (exclusive).
     */
    public static double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /**
     * Returns a random double value between 0.0 (inclusive) and bound (exclusive).
     */
    public static double nextDouble(double bound) {
        try {
            return ThreadLocalRandom.current().nextDouble(bound);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Rand::nextDouble: bound must be finite and positive, got " + bound, e);
        }
    }

    /**
     * Returns a random double value between min (inclusive) and max (exclusive).
     */
    public static double nextDouble(double min, double max) {
        try {
            return ThreadLocalRandom.current().nextDouble(min, max);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Rand::nextDouble: max must be greater than min, got min=" + min + ", max=" + max, e);
        }
    }

    // ========== Boolean Random ==========

    /**
     * Returns a random boolean value.
     */
    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    // ========== List Operations ==========

    /**
     * Returns a random element from the list.
     * Throws IllegalArgumentException if the list is null or empty.
     */
    public static <T> T choice(java.util.List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("Rand::choice: cannot choose from an empty or null list");
        }
        return list.get(nextInt(list.size()));
    }

    /**
     * Returns a new shuffled ArrayList from the list.
     */
    public static <T> ArrayList<T> shuffle(java.util.List<T> list) {
        ArrayList<T> copy = new ArrayList<>(list != null ? list : Collections.emptyList());
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return copy;
    }

    /**
     * Returns {@code n} distinct elements drawn at random without replacement.
     * If {@code n} is at least the list size, returns a shuffled copy of all
     * elements; a non-positive {@code n} yields an empty list.
     */
    public static <T> ArrayList<T> sample(java.util.List<T> list, int n) {
        ArrayList<T> shuffled = shuffle(list);
        if (n <= 0) return new ArrayList<>();
        if (n >= shuffled.size()) return shuffled;
        return new ArrayList<>(shuffled.subList(0, n));
    }

    // ========== UUID ==========

    /**
     * Generates a random UUID string.
     */
    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }
}
