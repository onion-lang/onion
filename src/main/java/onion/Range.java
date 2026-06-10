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
    private final int endExclusive;

    public Range(int start, int end, boolean inclusive) {
        this.start = start;
        long e = inclusive ? (long) end + 1 : end;
        // Clamp instead of overflowing when end == Integer.MAX_VALUE inclusive
        this.endExclusive = (int) Math.min(e, Integer.MAX_VALUE);
    }

    public int start() { return start; }
    public int endExclusive() { return endExclusive; }
    public boolean isEmpty() { return start >= endExclusive; }
    public int size() { return isEmpty() ? 0 : endExclusive - start; }

    public boolean contains(int value) {
        return value >= start && value < endExclusive;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            private int next = start;

            @Override
            public boolean hasNext() {
                return next < endExclusive;
            }

            @Override
            public Integer next() {
                if (!hasNext()) throw new NoSuchElementException();
                return next++;
            }
        };
    }

    @Override
    public String toString() {
        return isEmpty() ? "Range(empty)" : "Range(" + start + "..<" + endExclusive + ")";
    }
}
