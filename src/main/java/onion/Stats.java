package onion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Numeric aggregation over lists for Onion programs. The generic methods accept
 * any list of numbers (`List[Int]`, `List[Long]`, `List[Double]`, ...) and work
 * in double precision; the `sumInt` / `sumLong` variants keep integer precision.
 * Call as {@code Stats::average(nums)}.
 *
 * <p>Aggregates over an empty (or null) list return 0.0, except {@link #median}
 * which also returns 0.0.
 */
public final class Stats {
    private Stats() {
    }

    /** Sum of the values, in double precision. */
    public static <T extends Number> double sum(List<T> nums) {
        double total = 0.0;
        if (nums != null) {
            for (T n : nums) {
                if (n != null) total += n.doubleValue();
            }
        }
        return total;
    }

    /** Exact integer sum of an `List[Int]`. */
    public static int sumInt(List<Integer> nums) {
        int total = 0;
        if (nums != null) {
            for (Integer n : nums) {
                if (n != null) total += n;
            }
        }
        return total;
    }

    /** Exact long sum of an `List[Long]`. */
    public static long sumLong(List<Long> nums) {
        long total = 0L;
        if (nums != null) {
            for (Long n : nums) {
                if (n != null) total += n;
            }
        }
        return total;
    }

    /** Arithmetic mean, or 0.0 for an empty list. */
    public static <T extends Number> double average(List<T> nums) {
        if (nums == null || nums.isEmpty()) return 0.0;
        return sum(nums) / nums.size();
    }

    /** Smallest value, or 0.0 for an empty list. */
    public static <T extends Number> double min(List<T> nums) {
        if (nums == null || nums.isEmpty()) return 0.0;
        double m = Double.POSITIVE_INFINITY;
        for (T n : nums) {
            if (n != null) m = Math.min(m, n.doubleValue());
        }
        return m == Double.POSITIVE_INFINITY ? 0.0 : m;
    }

    /** Largest value, or 0.0 for an empty list. */
    public static <T extends Number> double max(List<T> nums) {
        if (nums == null || nums.isEmpty()) return 0.0;
        double m = Double.NEGATIVE_INFINITY;
        for (T n : nums) {
            if (n != null) m = Math.max(m, n.doubleValue());
        }
        return m == Double.NEGATIVE_INFINITY ? 0.0 : m;
    }

    /** Median value (mean of the two middle values for an even count), or 0.0. */
    public static <T extends Number> double median(List<T> nums) {
        if (nums == null || nums.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(nums.size());
        for (T n : nums) {
            if (n != null) sorted.add(n.doubleValue());
        }
        if (sorted.isEmpty()) return 0.0;
        Collections.sort(sorted);
        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    /** Population variance, or 0.0 for an empty list. */
    public static <T extends Number> double variance(List<T> nums) {
        if (nums == null || nums.isEmpty()) return 0.0;
        double mean = average(nums);
        double acc = 0.0;
        int count = 0;
        for (T n : nums) {
            if (n != null) {
                double d = n.doubleValue() - mean;
                acc += d * d;
                count++;
            }
        }
        return count == 0 ? 0.0 : acc / count;
    }

    /** Population standard deviation, or 0.0 for an empty list. */
    public static <T extends Number> double stddev(List<T> nums) {
        return Math.sqrt(variance(nums));
    }
}
