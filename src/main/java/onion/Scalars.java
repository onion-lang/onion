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

    /**
     * Reads {@code text} as the scalar kind named by {@code tag}, reporting a positioned
     * defect rather than throwing.
     *
     * <p>The tag vocabulary is the compiler-side conversion table's, so a shape and the
     * derivations that generate one speak the same names.
     *
     * @param tag    one of String, Int, Long, Double, Float, Boolean, Short, Byte
     * @param text   the text to read
     * @param origin where that text came from, or {@code null}
     * @param path   where in the value this belongs, used as the defect's path
     */
    public static Outcome<Object> read(String tag, String text, Origin origin, String path) {
        try {
            switch (tag) {
                case "String":  return Outcome.ok((Object) text);
                case "Int":     return Outcome.ok((Object) Integer.valueOf(text));
                case "Long":    return Outcome.ok((Object) Long.valueOf(text));
                case "Double":  return Outcome.ok((Object) Double.valueOf(text));
                case "Float":   return Outcome.ok((Object) Float.valueOf(text));
                case "Boolean": return Outcome.ok((Object) Boolean.valueOf(toBoolean(text)));
                case "Short":   return Outcome.ok((Object) Short.valueOf(text));
                case "Byte":    return Outcome.ok((Object) Byte.valueOf(text));
                default:
                    return Outcome.bad(Defect.at(origin, path, "a supported scalar type", tag));
            }
        } catch (IllegalArgumentException e) {
            // NumberFormatException is a subtype, so every numeric kind lands here too.
            return Outcome.bad(Defect.at(origin, path, tag, text));
        }
    }

    /**
     * Coerces an already-parsed document value (from JSON, YAML, …) to the scalar kind
     * named by {@code tag}, reporting a positioned defect rather than throwing.
     *
     * <p>Unlike {@link #read}, the value arrives typed: a JSON number is already a
     * {@code Number}, so this is a narrowing rather than a parse. A value of the wrong
     * shape entirely — a string where an {@code Int} was required — is a defect, not a
     * silent {@code null}.
     */
    public static Outcome<Object> coerce(String tag, Object value, Origin origin, String path) {
        if (value == null) return Outcome.bad(Defect.at(origin, path, tag, "absent"));
        try {
            switch (tag) {
                case "String":
                    if (value instanceof String) return Outcome.ok(value);
                    break;
                case "Boolean":
                    if (value instanceof Boolean) return Outcome.ok(value);
                    if (value instanceof String && isBoolean((String) value))
                        return Outcome.ok((Object) Boolean.valueOf(toBoolean((String) value)));
                    break;
                case "Int": case "Long": case "Double": case "Float": case "Short": case "Byte":
                    if (value instanceof Number) return Outcome.ok(narrow(tag, (Number) value));
                    if (value instanceof String) return read(tag, (String) value, origin, path);
                    break;
                default:
                    return Outcome.bad(Defect.at(origin, path, "a supported scalar type", tag));
            }
        } catch (IllegalArgumentException e) {
            return Outcome.bad(Defect.at(origin, path, tag, describe(value)));
        }
        return Outcome.bad(Defect.at(origin, path, tag, describe(value)));
    }

    private static Object narrow(String tag, Number n) {
        switch (tag) {
            case "Int":    return Integer.valueOf(n.intValue());
            case "Long":   return Long.valueOf(n.longValue());
            case "Double": return Double.valueOf(n.doubleValue());
            case "Float":  return Float.valueOf(n.floatValue());
            case "Short":  return Short.valueOf(n.shortValue());
            default:       return Byte.valueOf(n.byteValue());
        }
    }

    /** What was found, for a defect's `actual` -- the value's shape, not a dump of it. */
    private static String describe(Object value) {
        if (value instanceof String) return "\"" + value + "\"";
        if (value instanceof java.util.Map) return "an object";
        if (value instanceof java.util.List) return "an array";
        return String.valueOf(value);
    }

    /** Whether {@code value} is a boolean spelling {@link #toBoolean} accepts. */
    public static boolean isBoolean(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"));
    }
}
