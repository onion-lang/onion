package onion;

/**
 * A partial, potentially bidirectional correspondence between external text and a typed
 * value — the declarative data boundary.
 *
 * <p>Onion has four mechanisms that each approximate this: {@code record ... from re"..."},
 * {@code derive!(Json, Yaml)}, auto-CLI, and the resource literals. They share no
 * vocabulary, enumerate the same component types separately, and disagree about how
 * failure is reported. {@code Shape} is the abstraction they were approximating.
 *
 * <h2>The two laws, which must not be conflated</h2>
 *
 * <pre>
 *   L1  round-trip      parse(print(v)) == Ok(v)     guaranteed wherever print exists
 *   L2  normalization   print(parse(t)) == t         FALSE in general
 * </pre>
 *
 * <p>L2 fails for ordinary reasons: {@code "007"} is a perfectly good {@code Int} that
 * prints back as {@code "7"}, and <code>{ "a":1 }</code> and <code>{"a": 1}</code> parse
 * alike. A shape that satisfies L2 as well is <em>lossless</em>, which is rare, valuable,
 * and what a lens is built on. Most shapes are L1-only, and saying so is the difference
 * between a reversible language and one that claims to be.
 *
 * <h2>Printing is not always derivable</h2>
 *
 * <p>{@link #canPrint()} exists because a shape can be genuinely read-only — a regex with
 * a {@code \s+} separator has no unique rendering. The present derivation silently omits
 * {@code format} in that case, so a user gets "method not found" for a method they were
 * never told would be missing. A shape says instead.
 *
 * @param <T> the type read out of the text
 */
public interface Shape<T> {

    /**
     * Reads a value, reporting every reason it could not be read.
     *
     * @param text   the text to read
     * @param origin where that text came from, used to position any defect
     */
    Outcome<T> parse(String text, Origin origin);

    /** Reads a value from text with no known provenance. */
    default Outcome<T> parse(String text) {
        return parse(text, Origin.atLine("<input>", 1));
    }

    /**
     * Whether this shape can render a value back — that is, whether L1 is available at
     * all. Check before {@link #print}.
     */
    boolean canPrint();

    /**
     * Renders a value back to text, satisfying L1.
     *
     * @throws UnsupportedOperationException when {@link #canPrint()} is false. The
     *         message names the shape, because "this shape is not invertible" is useless
     *         if you cannot tell which one.
     */
    String print(T value);

    /** A description of what this shape reads, for diagnostics. */
    String describe();
}
