package onion;

/**
 * Time measurement utilities for benchmarking and performance analysis.
 *
 * Usage:
 *   val start = Timing::nanos();
 *   // ... do work ...
 *   val elapsed = Timing::elapsedNanos(start);
 *   IO::println("Elapsed: " + Timing::formatNanos(elapsed));
 */
public final class Timing {
    private Timing() {} // Prevent instantiation

    // ========== Time Sources ==========

    /**
     * Returns the current time in nanoseconds for elapsed time measurement.
     * Uses System.nanoTime() for high precision.
     */
    public static long nanos() {
        return System.nanoTime();
    }

    /**
     * Returns the current time in milliseconds.
     */
    public static long millis() {
        return System.currentTimeMillis();
    }

    // ========== Elapsed Time ==========

    /**
     * Returns the elapsed time in nanoseconds since the given start time.
     * @param startNanos the start time from nanos()
     */
    public static long elapsedNanos(long startNanos) {
        return System.nanoTime() - startNanos;
    }

    /**
     * Returns the elapsed time in milliseconds since the given start time.
     * @param startMillis the start time from millis()
     */
    public static long elapsedMillis(long startMillis) {
        return System.currentTimeMillis() - startMillis;
    }

    /**
     * Returns the elapsed time as a double in milliseconds.
     * Useful for sub-millisecond precision.
     * @param startNanos the start time from nanos()
     */
    public static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    // ========== Formatting ==========

    /**
     * Formats nanoseconds to a human-readable string.
     * Examples: "500ns", "1.23μs", "4.56ms", "1.23s"
     */
    public static String formatNanos(long nanos) {
        if (nanos < 1_000) {
            return nanos + "ns";
        } else if (nanos < 1_000_000) {
            return String.format("%.2fus", nanos / 1_000.0);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.2fms", nanos / 1_000_000.0);
        } else {
            return String.format("%.2fs", nanos / 1_000_000_000.0);
        }
    }

    /**
     * Formats milliseconds to a human-readable string.
     * Examples: "500ms", "1.23s", "2m30s"
     */
    public static String formatMillis(long millis) {
        if (millis < 1_000) {
            return millis + "ms";
        } else if (millis < 60_000) {
            return String.format("%.2fs", millis / 1_000.0);
        } else {
            long mins = millis / 60_000;
            long secs = (millis % 60_000) / 1_000;
            return mins + "m" + secs + "s";
        }
    }

    // ========== Utilities ==========

    /**
     * Sleeps for the specified number of milliseconds.
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleeps for the specified number of nanoseconds.
     */
    public static void sleepNanos(long nanos) {
        try {
            Thread.sleep(nanos / 1_000_000, (int)(nanos % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== Measurement with Functions ==========

    /**
     * Measures execution time of a function and prints the result.
     * Returns the function's return value.
     *
     * Usage:
     *   val result = Timing::measure(() -> heavyComputation());
     */
    public static <T> T measure(Function0<T> fn) {
        long start = System.nanoTime();
        T result = fn.call();
        long elapsed = System.nanoTime() - start;
        System.out.println("Elapsed: " + formatNanos(elapsed));
        return result;
    }

    /**
     * Measures execution time of a function and prints with a label.
     * Returns the function's return value.
     *
     * Usage:
     *   val result = Timing::measure("task", () -> heavyComputation());
     */
    public static <T> T measure(String label, Function0<T> fn) {
        long start = System.nanoTime();
        T result = fn.call();
        long elapsed = System.nanoTime() - start;
        System.out.println(label + ": " + formatNanos(elapsed));
        return result;
    }

    /**
     * Measures execution time of a void function and prints the result.
     *
     * Usage:
     *   Timing::measureVoid(() -> { doSomething(); });
     */
    public static void measureVoid(Runnable fn) {
        long start = System.nanoTime();
        fn.run();
        long elapsed = System.nanoTime() - start;
        System.out.println("Elapsed: " + formatNanos(elapsed));
    }

    /**
     * Measures execution time of a void function and prints with a label.
     *
     * Usage:
     *   Timing::measureVoid("task", () -> { doSomething(); });
     */
    public static void measureVoid(String label, Runnable fn) {
        long start = System.nanoTime();
        fn.run();
        long elapsed = System.nanoTime() - start;
        System.out.println(label + ": " + formatNanos(elapsed));
    }

    /**
     * Measures execution time and returns elapsed nanoseconds.
     * Does not print anything.
     *
     * Usage:
     *   val elapsed = Timing::time(() -> heavyComputation());
     */
    public static <T> long time(Function0<T> fn) {
        long start = System.nanoTime();
        fn.call();
        return System.nanoTime() - start;
    }
}
