package onion;

/**
 * One thing that was wrong with external data.
 *
 * <p>A defect answers three questions a caller actually has: <em>where</em> in the text
 * ({@link #origin}), <em>where</em> in the value ({@link #path}), and <em>what</em> was
 * expected against what was found. Both "wheres" are needed — one says which byte, the
 * other says which field, and neither substitutes for the other.
 *
 * <p>{@code origin} may be {@code null}: not every failure has a position to point at.
 * A missing key is a defect of the document, not of any particular character in it.
 *
 * @param origin   where in the text, or {@code null} when there is no position
 * @param path     where in the value, e.g. {@code "status"} or {@code "items[3].price"}
 * @param expected what the shape required, e.g. {@code "Int"}
 * @param actual   what was there instead, e.g. {@code "abc"} or {@code "absent"}
 */
public record Defect(Origin origin, String path, String expected, String actual) {

    /** A defect with no position — a missing key, a failed whole-document check. */
    public static Defect of(String path, String expected, String actual) {
        return new Defect(null, path, expected, actual);
    }

    /** A defect at a known position in the text. */
    public static Defect at(Origin origin, String path, String expected, String actual) {
        return new Defect(origin, path, expected, actual);
    }

    /** Whether this defect knows where in the text it happened. */
    public boolean hasOrigin() {
        return origin != null;
    }

    /**
     * The same defect moved to {@code line} of an enclosing text.
     *
     * <p>Parsing a document line by line means each line's defects carry positions
     * relative to that line; this lifts one back into the whole document. A defect with
     * no origin stays without one — inventing a position would be worse than having none.
     */
    public Defect onLine(int line) {
        return origin == null ? this : new Defect(origin.onLine(line), path, expected, actual);
    }

    /** The same defect with {@code prefix} prepended to its path, for nesting. */
    public Defect under(String prefix) {
        if (prefix == null || prefix.isEmpty()) return this;
        String nested = (path == null || path.isEmpty()) ? prefix : prefix + "." + path;
        return new Defect(origin, nested, expected, actual);
    }

    /**
     * {@code file:line: path: expected X, found Y} — position first, the way every
     * compiler and editor already prints a diagnostic. The position is omitted when
     * there is none.
     */
    public String describe() {
        String body = path + ": expected " + expected + ", found " + actual;
        return origin == null ? body : origin.describe() + ": " + body;
    }

    @Override
    public String toString() {
        return describe();
    }
}
