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
     * Throws IllegalArgumentException if the array is null or empty.
     */
    public static <T> T choice(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Rand::choice: cannot choose from an empty or null array");
        }
        return array[nextInt(array.length)];
    }

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

    // ========== Primitive-array overloads ==========
    // A primitive array (int[], long[], double[], boolean[]) is not assignment-
    // compatible with the generic T[] (Object[]) on the JVM, so choice/shuffle/
    // sample need explicit overloads. shuffle/sample return a boxed ArrayList,
    // matching the T[] forms (so `.size`/element access work the same way).

    public static int choice(int[] array) {
        if (array == null || array.length == 0) throw new IllegalArgumentException("Rand::choice: cannot choose from an empty or null array");
        return array[nextInt(array.length)];
    }
    public static ArrayList<Integer> shuffle(int[] array) {
        ArrayList<Integer> list = new ArrayList<>();
        if (array != null) for (int item : array) list.add(item);
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }
    public static ArrayList<Integer> sample(int[] array, int n) {
        ArrayList<Integer> list = new ArrayList<>();
        if (array != null) for (int item : array) list.add(item);
        return sample(list, n);
    }

    public static long choice(long[] array) {
        if (array == null || array.length == 0) throw new IllegalArgumentException("Rand::choice: cannot choose from an empty or null array");
        return array[nextInt(array.length)];
    }
    public static ArrayList<Long> shuffle(long[] array) {
        ArrayList<Long> list = new ArrayList<>();
        if (array != null) for (long item : array) list.add(item);
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }
    public static ArrayList<Long> sample(long[] array, int n) {
        ArrayList<Long> list = new ArrayList<>();
        if (array != null) for (long item : array) list.add(item);
        return sample(list, n);
    }

    public static double choice(double[] array) {
        if (array == null || array.length == 0) throw new IllegalArgumentException("Rand::choice: cannot choose from an empty or null array");
        return array[nextInt(array.length)];
    }
    public static ArrayList<Double> shuffle(double[] array) {
        ArrayList<Double> list = new ArrayList<>();
        if (array != null) for (double item : array) list.add(item);
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }
    public static ArrayList<Double> sample(double[] array, int n) {
        ArrayList<Double> list = new ArrayList<>();
        if (array != null) for (double item : array) list.add(item);
        return sample(list, n);
    }

    public static boolean choice(boolean[] array) {
        if (array == null || array.length == 0) throw new IllegalArgumentException("Rand::choice: cannot choose from an empty or null array");
        return array[nextInt(array.length)];
    }
    public static ArrayList<Boolean> shuffle(boolean[] array) {
        ArrayList<Boolean> list = new ArrayList<>();
        if (array != null) for (boolean item : array) list.add(item);
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }
    public static ArrayList<Boolean> sample(boolean[] array, int n) {
        ArrayList<Boolean> list = new ArrayList<>();
        if (array != null) for (boolean item : array) list.add(item);
        return sample(list, n);
    }

    // ========== UUID ==========

    /**
     * Generates a random UUID string.
     */
    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }
}
