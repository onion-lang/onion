package onion;

/**
 * A value together with the residue of the text it was read from (issue #362).
 *
 * <p>The pair is what makes L2 — {@code print(parse(t)) == t} — achievable: the value
 * carries what the program cares about, the residue carries everything else, and a
 * lossless shape's {@link Shape#printLossless(Object, Residue)} reassembles the two
 * into the original text, byte for byte, except where the value was deliberately
 * changed.
 */
public final class Lossless<T> {
    private final T value;
    private final Residue residue;

    public Lossless(T value, Residue residue) {
        this.value = value;
        this.residue = residue;
    }

    public T value() { return value; }
    public Residue residue() { return residue; }

    /** The same residue around a new value — the heart of an edit-and-write-back. */
    public Lossless<T> withValue(T newValue) {
        return new Lossless<>(newValue, residue);
    }

    @Override
    public String toString() {
        return "Lossless(" + value + ")";
    }
}
