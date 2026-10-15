package onion;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An ascending integer range, the runtime representation of the range
 * literals {@code a..b} (inclusive) and {@code a..<b} (exclusive).
 * Empty when the start is greater than the last value.
 *
 * Usage:
 *   foreach i: Int in 1..5 { IO::println(i) }     // 1 2 3 4 5
 *   foreach i: Int in 0..<arr.length { ... }      // 0 .. length-1
 */
public final class Range implements Iterable<Integer> {
    private final int start;
    private final long endExclusive;

    public Range(int start, int end, boolean inclusive) {
        this.start = start;
        // Kept as a long so an inclusive end of Integer.MAX_VALUE is still produced
        this.endExclusive = inclusive ? (long) end + 1 : end;
    }

    public int start() { return start; }
    /** The exclusive end, clamped to {@code Integer.MAX_VALUE} for an inclusive range ending there. */
    public int endExclusive() { return (int) Math.min(endExclusive, Integer.MAX_VALUE); }
    public boolean isEmpty() { return start >= endExclusive; }
    /** The number of values, clamped to {@code Integer.MAX_VALUE} (as {@code Collection.size()} is). */
    public int size() { return isEmpty() ? 0 : (int) Math.min(endExclusive - start, Integer.MAX_VALUE); }

    public boolean contains(int value) {
        return value >= start && value < endExclusive;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            private long next = start;

            @Override
            public boolean hasNext() {
                return next < endExclusive;
            }

            @Override
            public Integer next() {
                if (!hasNext()) throw new NoSuchElementException();
                return (int) next++;
            }
        };
    }

    @Override
    public String toString() {
        return isEmpty() ? "Range(empty)" : "Range(" + start + "..<" + endExclusive + ")";
    }
}
