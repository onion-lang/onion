package onion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight YAML serializer and parser for Onion programs.
 * No external dependencies - completely self-contained.
 *
 * Scope: flat block mapping only (no nested maps, no sequences, no anchors).
 *
 * YAML values share the same intermediate representation as Json:
 * - YAML mapping:  LinkedHashMap&lt;String, Object&gt;
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
     * Convert a Java object (Map or scalar) to YAML flat block mapping text.
     * Each map entry is rendered as {@code key: value\n}.
     *
     * @param obj Object to serialize. Must be a Map&lt;?,?&gt; or a scalar
     *            (String/Long/Double/Float/Integer/Short/Byte/Boolean/null).
     * @return YAML text
     */
    public static String stringify(Object obj) {
        if (obj == null) {
            return "null\n";
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                sb.append(key);
                sb.append(": ");
                sb.append(renderScalar(entry.getValue()));
                sb.append('\n');
            }
            return sb.toString();
        }
        // Bare scalar at top level
        return renderScalar(obj) + "\n";
    }

    /**
     * Parse a YAML flat block mapping string into a LinkedHashMap.
     *
     * @param text YAML text (key: value lines)
     * @return Parsed LinkedHashMap&lt;String,Object&gt;
     * @throws YamlParseException if any line cannot be parsed
     */
    public static Object parse(String text) throws YamlParseException {
        if (text == null || text.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        String[] rawLines = text.split("\r\n|\r|\n", -1);
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            // Skip blank / all-whitespace lines
            if (rawLine.trim().isEmpty()) {
                continue;
            }

            // Find the first ": " separator outside of any quoted region,
            // or a trailing ":" (value is empty → null).
            int sep = findKeySeparator(rawLine);
            if (sep < 0) {
                throw new YamlParseException("Expected 'key: value' but found no colon: " + rawLine, lineNumber);
            }

            String rawKey = rawLine.substring(0, sep).trim();
            String key = rawKey.startsWith("\"") ? unquote(rawKey, lineNumber) : rawKey;

            // Value: everything after the separator (": " = 2 chars, or ":" alone at end = 1 char)
            int valueStart = sep + (sep == rawLine.length() - 1 ? 1 : 2);
            String rawValue = valueStart <= rawLine.length() ? rawLine.substring(valueStart) : "";
            rawValue = rawValue.trim();

            Object value = parseScalar(rawValue, lineNumber);
            result.put(key, value);
        }

        return result;
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
        // Quick reject: must start with '-' or a digit
        char first = s.charAt(0);
        if (first != '-' && (first < '0' || first > '9')) return false;
        // Try integer pattern: -?\d+
        if (s.matches("-?\\d+")) return true;
        // Try float pattern: -? digits (. digits)? ([eE] [+-]? digits)?
        // Use Double.parseDouble as the canonical check
        try {
            // Only treat as float if it contains '.' or 'e'/'E'
            if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) return false;
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
