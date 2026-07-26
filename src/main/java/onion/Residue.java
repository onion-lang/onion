package onion;

/**
 * The parts of an input a lossless shape did not consume into the value — comments,
 * blank lines, key order, spacing, quoting and numeric spelling, unknown keys.
 *
 * <p>Opaque by design: a residue is only meaningful to the shape that produced it, and
 * the only correct use is to hand it back to that shape's
 * {@link Shape#printLossless(Object, Residue)}. A shape must reject a residue it did
 * not produce rather than misrender through it.
 */
public interface Residue {
}
