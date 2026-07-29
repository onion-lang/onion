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

    // ------------------------------------------------------------- losslessness

    /**
     * Whether this shape is <em>lossless</em>: whether it can satisfy L2 by carrying the
     * unconsumed parts of the input as a {@link Residue}. Most shapes are not, and this
     * default says so instead of letting them pretend.
     */
    default boolean isLossless() {
        return false;
    }

    /**
     * Reads a value <em>and</em> the residue of everything around it, such that
     * {@code printLossless(r.value(), r.residue())} reproduces the input byte for byte.
     *
     * @throws UnsupportedOperationException when {@link #isLossless()} is false —
     *         asking a lossy shape for a residue is a programming error, not a data
     *         defect, and answering with an empty residue would quietly break L2.
     */
    default Outcome<Lossless<T>> parseLossless(String text, Origin origin) {
        throw new UnsupportedOperationException(
            "shape " + describe() + " is not lossless; parseLossless has no meaning for it");
    }

    /** {@link #parseLossless} against text with no known provenance. */
    default Outcome<Lossless<T>> parseLossless(String text) {
        return parseLossless(text, Origin.atLine("<input>", 1));
    }

    /**
     * Renders a value through the residue of the text it was read from: unchanged parts
     * are reproduced exactly — comments, spacing, key order, even the original spelling
     * of an unchanged value — and only deliberately changed values re-render.
     *
     * @throws UnsupportedOperationException when {@link #isLossless()} is false
     * @throws IllegalArgumentException when the residue was produced by another shape
     */
    default String printLossless(T value, Residue residue) {
        throw new UnsupportedOperationException(
            "shape " + describe() + " is not lossless; printLossless has no meaning for it");
    }

    // ------------------------------------------------------------- combinators

    /**
     * Reads one value per line, keeping <strong>both</strong> the values and the reasons
     * the other lines could not be read.
     *
     * <p>Each line's defects are positioned on that line, so a failure says which line of
     * which file. Pair with {@link Outcome#values} and {@link Outcome#defects} to get the
     * rows and the problems separately:
     *
     * <pre>
     *   List&lt;Outcome&lt;Row&gt;&gt; each = rowShape.eachLine(text, origin);
     *   List&lt;Row&gt;    rows    = Outcome.values(each);    // the 995 that read
     *   List&lt;Defect&gt; bad     = Outcome.defects(each);   // the 5 that did not
     * </pre>
     *
     * <p>This is deliberately not an {@code Outcome<List<T>>}: a log file where 995 of
     * 1000 lines parsed is exactly the case where a partial result is worth having, and
     * where today's {@code parseAll} returns those 995 rows with no trace that the other
     * five existed. Use {@link #lines()} when a partial result is meaningless instead.
     */
    default java.util.List<Outcome<T>> eachLine(String text, Origin origin) {
        java.util.List<Outcome<T>> results = new java.util.ArrayList<>();
        if (text == null) return results;
        String[] split = text.split("\\r?\\n", -1);
        // A trailing newline is a line terminator, not an empty final record.
        int count = (split.length > 0 && split[split.length - 1].isEmpty()) ? split.length - 1 : split.length;
        String source = origin == null ? "<input>" : origin.source();
        for (int i = 0; i < count; i++) {
            results.add(parse(split[i], Origin.atLine(source, i + 1)).onLine(i + 1));
        }
        return results;
    }

    /** {@link #eachLine} against text with no known provenance. */
    default java.util.List<Outcome<T>> eachLine(String text) {
        return eachLine(text, Origin.atLine("<input>", 1));
    }

    /**
     * One value per line, all or nothing — every bad line's defect is reported together.
     *
     * <p>Prints one value per line, so L1 holds for the list as long as it holds for the
     * element. An element whose rendering contains a newline cannot be represented at
     * all — it would split into a bogus extra line — so {@link #print} refuses
     * ({@link IllegalArgumentException}) rather than corrupt the document.
     */
    default Shape<java.util.List<T>> lines() {
        return new LineShape<>(this);
    }

    /**
     * Repetition with a literal separator, all or nothing.
     *
     * <p>The separator is literal text, not a pattern: a shape describes a correspondence
     * and a regex separator would have no unique rendering, so {@link #print} could not
     * exist. Defects are positioned on the element they came from by index rather than by
     * line, since a separated list need not be laid out one per line. An element whose
     * rendering contains the separator is indistinguishable from a real boundary on
     * reparse, so {@link #print} refuses ({@link IllegalArgumentException}) rather than
     * corrupt the document.
     */
    default Shape<java.util.List<T>> sepBy(String separator) {
        return new SepByShape<>(this, separator);
    }

    /**
     * Transports this shape along an isomorphism.
     *
     * <p>Both directions are required: a one-way {@code map} would silently destroy
     * {@link #print}, which is the asymmetry a bidirectional description has to respect.
     */
    default <U> Shape<U> xmap(Function1<T, U> forward, Function1<U, T> backward) {
        return new XmapShape<>(this, forward, backward);
    }

    /**
     * This shape, or {@code other} when it does not read.
     *
     * <p>When neither reads, the defects of both are reported: the user is being told
     * which spellings were tried, and hiding one of them helps nobody. Prints with this
     * shape.
     */
    default Shape<T> orElse(Shape<T> other) {
        return new OrElseShape<>(this, other);
    }
}

