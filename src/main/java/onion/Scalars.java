package onion;

/**
 * Scalar conversions for boundary derivations, where the JDK's own parser is not strict
 * enough to be used as one.
 *
 * <p>Every {@code java.lang.X.parseX} rejects malformed input by throwing — except
 * {@link Boolean#parseBoolean}, which maps everything that is not {@code "true"} to
 * {@code false}. Used as a parser that turns a malformed field into a valid-looking
 * value, which is the one failure mode a parser must never have: {@code "maybe"},
 * {@code "yes"} and {@code "1"} all became {@code false} with nothing to indicate the
 * data was wrong (issue #349).
 */
public final class Scalars {

    private Scalars() {}

    /**
     * {@code "true"} or {@code "false"}, in any case. Anything else — including
     * {@code null}, {@code "yes"}, {@code "1"} and {@code ""} — is a parse failure.
     *
     * <p>Throws {@link IllegalArgumentException}, the supertype of the
     * {@code NumberFormatException} the numeric parsers throw, so a derivation catches
     * both the same way.
     */
    public static boolean toBoolean(String value) {
        if (value != null) {
            if (value.equalsIgnoreCase("true")) return true;
            if (value.equalsIgnoreCase("false")) return false;
        }
        throw new IllegalArgumentException(
            "expected true or false, found " + (value == null ? "null" : "'" + value + "'"));
    }

    /** Whether {@code value} is a boolean spelling {@link #toBoolean} accepts. */
    public static boolean isBoolean(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"));
    }
}
