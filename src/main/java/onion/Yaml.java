package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight YAML serializer and parser for Onion programs.
 * No external dependencies - completely self-contained.
 *
 * Scope: a flat block mapping, or a top-level block sequence of flat mappings
 * (no nested maps/sequences within an element, no anchors).
 *
 * YAML values share the same intermediate representation as Json:
 * - YAML mapping:  LinkedHashMap&lt;String, Object&gt;
 * - YAML sequence: ArrayList&lt;Object&gt; (top-level only, elements are mappings)
 * - scalar string: String
 * - scalar integer: Long
 * - scalar float:  Double
 * - scalar boolean: Boolean
 * - scalar null:   null
 *
 * Usage:
 *   import static onion.Yaml::*
 *
 *   Object data = parse("name: Alice\nage: 30\n")
 *   String yaml = stringify(data)
 */
public final class Yaml {
    private Yaml() {}

    // ========== Exception ==========

    /**
     * Exception thrown when YAML parsing fails.
     * Mirrors Json.JsonParseException in shape.
     */
    public static final class YamlParseException extends Exception {
        private final int line;

        public YamlParseException(String message, int line) {
            super(message + " at line " + line);
            this.line = line;
        }

        public int getLine() {
            return line;
        }
    }

    // ========== Core API ==========

    /**
     * Convert a Java object (List, Map, or scalar) to YAML text. A {@code Map} renders
     * as a flat block mapping, {@code key: value\n} per entry; a {@code List} renders as
     * a top-level block sequence, {@code - key: value\n} per element (a non-Map element
     * renders as a bare {@code - scalar}). Keys go through the same quoting rule as
     * string values ({@link #needsQuoting}), so a key containing {@code :}, {@code #},
     * whitespace at either end, or a newline round-trips through {@link #parse} instead
     * of colliding with the {@code key: value} separator.
     *
     * @param obj Object to serialize. Must be a List&lt;?&gt;, a Map&lt;?,?&gt;, or a scalar
     *            (String/Long/Double/Float/Integer/Short/Byte/Boolean/null).
     * @return YAML text
     */
    public static String stringify(Object obj) {
        if (obj == null) {
            return "null\n";
        }
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                sb.append(renderSequenceItem(item));
            }
            return sb.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(renderMappingLine(entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        }
        // Bare scalar at top level
        return renderScalar(obj) + "\n";
    }

    /** Render one `key: value\n` line, shared by the top-level mapping and sequence-item cases. */
    private static String renderMappingLine(Object key, Object value) {
        return renderString(key.toString()) + ": " + renderScalar(value) + "\n";
    }

    /**
     * Render one sequence element: a flat `Map` becomes a `- key: value` block (first
     * entry carries the `- `, later entries are indented two spaces to align under it);
     * any other value is rendered as a bare `- scalar` item.
     */
    private static String renderSequenceItem(Object item) {
        if (item instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "- {}\n";
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(first ? "- " : "  ").append(renderMappingLine(entry.getKey(), entry.getValue()));
                first = false;
            }
            return sb.toString();
        }
        return "- " + renderScalar(item) + "\n";
    }

    /**
     * Parse a YAML flat block mapping, or a top-level block sequence of flat mappings,
     * into a LinkedHashMap or an ArrayList respectively.
     *
     * @param text YAML text (`key: value` lines, or `- key: value` sequence items)
     * @return Parsed LinkedHashMap&lt;String,Object&gt;, or ArrayList&lt;Object&gt; for a sequence
     * @throws YamlParseException if any line cannot be parsed
     */
    public static Object parse(String text) throws YamlParseException {
        if (text == null || text.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }

        String[] rawLines = text.split("\r\n|\r|\n", -1);
        for (String rawLine : rawLines) {
            if (!rawLine.trim().isEmpty()) {
                return isSequenceItem(rawLine.trim()) ? parseSequence(rawLines) : parseMapping(rawLines);
            }
        }
        return new LinkedHashMap<String, Object>();
    }

    /**
     * Parse a YAML string, returning null on error instead of throwing.
     * @param text YAML text to parse
     * @return Parsed object, or null if parsing fails
     */
    public static Object parseOrNull(String text) {
        try {
            return parse(text);
        } catch (YamlParseException e) {
            return null;
        }
    }

    private static boolean isSequenceItem(String trimmedLine) {
        return trimmedLine.equals("-") || trimmedLine.startsWith("- ");
    }

    private static LinkedHashMap<String, Object> parseMapping(String[] rawLines) throws YamlParseException {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            if (rawLine.trim().isEmpty()) {
                continue;
            }
            parseKeyValueLine(rawLine.trim(), lineNumber, result);
        }
        return result;
    }

    /**
     * Parse a top-level block sequence: each line starting with `-` (`isSequenceItem`)
     * opens a new flat-mapping element, and every following non-`-` line adds another
     * `key: value` entry to that same element until the next `-` line or end of input.
     */
    private static List<Object> parseSequence(String[] rawLines) throws YamlParseException {
        List<Object> result = new ArrayList<>();
        LinkedHashMap<String, Object> current = null;
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isSequenceItem(trimmed)) {
                current = new LinkedHashMap<>();
                result.add(current);
                String rest = trimmed.equals("-") ? "" : trimmed.substring(2).trim();
                if (!rest.isEmpty()) {
                    parseKeyValueLine(rest, lineNumber, current);
                }
            } else {
                if (current == null) {
                    throw new YamlParseException("Expected a sequence item ('- ...') but found: " + rawLine, lineNumber);
                }
                parseKeyValueLine(trimmed, lineNumber, current);
            }
        }
        return result;
    }

    /** Parse one already-trimmed `key: value` line and put it into `target`. */
    private static void parseKeyValueLine(String trimmedLine, int lineNumber, LinkedHashMap<String, Object> target)
            throws YamlParseException {
        int sep = findKeySeparator(trimmedLine);
        if (sep < 0) {
            throw new YamlParseException("Expected 'key: value' but found no colon: " + trimmedLine, lineNumber);
        }

        String rawKey = trimmedLine.substring(0, sep).trim();
        String key = rawKey.startsWith("\"") ? unquote(rawKey, lineNumber) : rawKey;

        // Value: everything after the separator (": " = 2 chars, or ":" alone at end = 1 char)
        int valueStart = sep + (sep == trimmedLine.length() - 1 ? 1 : 2);
        String rawValue = valueStart <= trimmedLine.length() ? trimmedLine.substring(valueStart) : "";
        rawValue = rawValue.trim();

        target.put(key, parseScalar(rawValue, lineNumber));
    }

    // ========== Internal helpers ==========

    /**
     * Render a scalar value for YAML output.
     * Strings that would be misread on parse-back are quoted.
     * Numbers and booleans are rendered verbatim (no quotes).
     */
    private static String renderScalar(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return value.toString();          // "true" / "false"
        }
        if (value instanceof Number) {
            return value.toString();          // "100", "3.5", "1.0E10" – never quoted
        }
        if (value instanceof String s) {
            return renderString(s);
        }
        // Fallback – unknown type, quote to be safe
        return renderString(value.toString());
    }

    /**
     * Decide whether to quote a String value and return the result.
     * Quote if:
     *   - empty string
     *   - leading or trailing whitespace
     *   - contains ':', '#', newline, or tab
     *   - looks like a number (would be parsed back as Long or Double)
     *   - equals "true", "false", or "null" (would be parsed back as Boolean/null)
     */
    private static String renderString(String s) {
        if (needsQuoting(s)) {
            return "\"" + escapeString(s) + "\"";
        }
        return s;
    }

    private static boolean needsQuoting(String s) {
        if (s.isEmpty()) return true;
        if (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' ') return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':' || c == '#' || c == '\n' || c == '\r' || c == '\t') return true;
        }
        // Would be mis-parsed as a Boolean or null
        if (s.equals("true") || s.equals("false") || s.equals("null")) return true;
        // Would be mis-parsed as a number
        if (looksLikeNumber(s)) return true;
        return false;
    }

    /**
     * Returns true if the string would be parsed as Long or Double by parseScalar.
     * Mirrors the number detection in parseScalar exactly.
     */
    private static boolean looksLikeNumber(String s) {
        if (s.isEmpty()) return false;
        if (s.matches("-?\\d+")) return true;
        if (s.matches("-?\\d*\\.\\d+([eE][+\\-]?\\d+)?")) return true;
        if (s.matches("-?\\d+\\.?\\d*[eE][+\\-]?\\d+")) return true;
        return false;
    }

    /**
     * Escape special characters for a double-quoted YAML scalar.
     * Handles: \" \\ \n \r \t
     */
    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Find the index of the ':' that terminates a YAML key in rawLine.
     * We look for ": " (colon + space) or ":" at the very end of the line.
     * Respects double-quoted keys (skips content inside "...").
     *
     * @return index of the ':' character, or -1 if not found
     */
    private static int findKeySeparator(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '\\') { i++; continue; } // skip escaped char
                if (c == '"')  { inQuote = false; }
                continue;
            }
            if (c == '"') { inQuote = true; continue; }
            if (c == ':') {
                // Accept ": " (colon-space) or ":" at end of line
                if (i + 1 < line.length() && line.charAt(i + 1) == ' ') return i;
                if (i + 1 == line.length()) return i;
            }
        }
        return -1;
    }

    /**
     * Parse a scalar value string from a YAML value field.
     *
     * Rules (same type inferences as Json.parse):
     *   ""   or "null"       → null
     *   "true" / "false"     → Boolean
     *   -?\d+                → Long
     *   contains '.' or e/E  → Double
     *   "..." (quoted)        → String (unescaped, no further type inference)
     *   other                 → String as-is
     */
    private static Object parseScalar(String raw, int lineNumber) throws YamlParseException {
        if (raw.isEmpty() || raw.equals("null")) return null;
        if (raw.equals("true"))  return Boolean.TRUE;
        if (raw.equals("false")) return Boolean.FALSE;

        // Quoted string – unescape and return as String (no type coercion)
        if (raw.startsWith("\"")) {
            return unquote(raw, lineNumber);
        }

        // Integer?
        if (raw.matches("-?\\d+")) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new YamlParseException("Invalid integer: " + raw, lineNumber);
            }
        }

        // Float? (contains '.' or 'e'/'E', optionally signed)
        if (raw.matches("-?\\d*\\.\\d+([eE][+\\-]?\\d+)?") ||
            raw.matches("-?\\d+\\.?\\d*[eE][+\\-]?\\d+")) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw new YamlParseException("Invalid number: " + raw, lineNumber);
            }
        }

        // Anything else → String
        return raw;
    }

    /**
     * Remove surrounding double-quotes and unescape backslash sequences.
     * Supports: \" \\ \n \r \t
     */
    private static String unquote(String raw, int lineNumber) throws YamlParseException {
        if (!raw.startsWith("\"") || !raw.endsWith("\"") || raw.length() < 2) {
            throw new YamlParseException("Malformed quoted string: " + raw, lineNumber);
        }
        String inner = raw.substring(1, raw.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\') {
                if (i + 1 >= inner.length()) {
                    throw new YamlParseException("Unterminated escape in: " + raw, lineNumber);
                }
                char next = inner.charAt(++i);
                switch (next) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:
                        throw new YamlParseException("Unknown escape \\" + next + " in: " + raw, lineNumber);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
