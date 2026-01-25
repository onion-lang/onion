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
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /**
     * Returns a random int value between min (inclusive) and max (exclusive).
     */
    public static int nextInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
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
        return ThreadLocalRandom.current().nextLong(bound);
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
        return ThreadLocalRandom.current().nextDouble(bound);
    }

    /**
     * Returns a random double value between min (inclusive) and max (exclusive).
     */
    public static double nextDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    // ========== Boolean Random ==========

    /**
     * Returns a random boolean value.
     */
    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    // ========== Array Operations ==========

    /**
     * Returns a random element from the array.
     * Returns null if array is null or empty.
     */
    public static <T> T choice(T[] array) {
        if (array == null || array.length == 0) return null;
        return array[nextInt(array.length)];
    }

    /**
     * Returns a random element from the list.
     * Returns null if list is null or empty.
     */
    public static <T> T choice(ArrayList<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(nextInt(list.size()));
    }

    /**
     * Returns a new shuffled ArrayList from the array.
     */
    public static <T> ArrayList<T> shuffle(T[] array) {
        ArrayList<T> list = new ArrayList<>();
        if (array != null) {
            for (T item : array) list.add(item);
        }
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }

    /**
     * Returns a new shuffled ArrayList from the list.
     */
    public static <T> ArrayList<T> shuffle(ArrayList<T> list) {
        ArrayList<T> copy = new ArrayList<>(list != null ? list : Collections.emptyList());
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return copy;
    }

    // ========== UUID ==========

    /**
     * Generates a random UUID string.
     */
    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }
}
