package onion;

/**
 * A value together with the residue of the text it was read from, and the shape that
 * read it (issues #362, #363).
 *
 * <p>The pair is what makes L2 — {@code print(parse(t)) == t} — achievable; the shape
 * reference is what makes the pair a <em>lens</em>: {@link #edit} focuses an update on
 * the value, {@link #render} reassembles text through the residue, and everything the
 * update did not touch — comments, spacing, key order, other values' spellings —
 * survives byte for byte.
 *
 * <pre>
 *   val r   = file"app.conf".readLossless(Server::cfg()).get()
 *   val out = r.edit { v -> v.copy(port = 9090) }.render()
 *   // diff app.conf out  ->  one changed line
 * </pre>
 */
public final class Lossless<T> {
    private final T value;
    private final Residue residue;
    private final Shape<T> shape;
    // Text the shape never saw, put back in front on render: the byte order mark that
    // file(p).readLossless strips before parsing, as read(shape) does (#3213).
    private final String prefix;

    public Lossless(T value, Residue residue, Shape<T> shape) {
        this(value, residue, shape, "");
    }

    private Lossless(T value, Residue residue, Shape<T> shape, String prefix) {
        this.value = value;
        this.residue = residue;
        this.shape = shape;
        this.prefix = prefix;
    }

    /** The same pair, rendering with {@code prefix} in front of the shape's text. */
    static <T> Lossless<T> prefixed(Lossless<T> r, String prefix) {
        return new Lossless<>(r.value, r.residue, r.shape, prefix + r.prefix);
    }

    public T value() { return value; }
    public Residue residue() { return residue; }

    /** The same residue around a new value — the heart of an edit-and-write-back. */
    public Lossless<T> withValue(T newValue) {
        return new Lossless<>(newValue, residue, shape, prefix);
    }

    /** Focuses an update on the value: {@code r.edit { v -> v.copy(port = 9090) }}. */
    public Lossless<T> edit(Function1<T, T> update) {
        return withValue(update.call(value));
    }

    /**
     * Renders through the residue: unchanged parts reproduce exactly, edited values
     * re-render in place. Rendering an unedited pair returns the original text (L2).
     */
    public String render() {
        return prefix + shape.printLossless(value, residue);
    }

    @Override
    public String toString() {
        return "Lossless(" + value + ")";
    }
}
