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
 * (no nested maps/sequences within an element, no anchors, no flow collections).
 * A construct outside that scope raises an error rather than silently losing data:
 * {@link #stringify} throws {@code IllegalArgumentException} on a nested Map/List
 * value, and {@link #parse} throws {@link YamlParseException} on a nested mapping
 * (a line indented deeper than its siblings, also inside a sequence element), a
 * flow collection ({@code [...]}/{@code {...}}) value, an anchor/alias
 * ({@code &name}/{@code *name}) value, or a block scalar ({@code |}/{@code >}).
 * A uniformly indented document is fine. {@link #stringify} quotes any string
 * that starts with such an indicator, so its output always parses back.
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
            if (!isBlankOrComment(rawLine)) {
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

    /** A blank line, or a whole-line {@code # comment}, carries no data. */
    private static boolean isBlankOrComment(String rawLine) {
        String t = rawLine.trim();
        return t.isEmpty() || t.charAt(0) == '#';
    }

    private static boolean isSequenceItem(String trimmedLine) {
        return trimmedLine.equals("-") || trimmedLine.startsWith("- ");
    }

    /**
     * Parse a flat block mapping. The document may be uniformly indented; a line indented
     * deeper than the shallowest line so far is a nested mapping (or a block scalar's
     * body), which is out of scope.
     */
    private static LinkedHashMap<String, Object> parseMapping(String[] rawLines) throws YamlParseException {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int baseIndent = Integer.MAX_VALUE;
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            if (isBlankOrComment(rawLine)) {
                continue;
            }
            int indent = indentOf(rawLine);
            if (indent > baseIndent) {
                throw nestedLine(rawLine, lineNumber);
            }
            baseIndent = indent;
            parseKeyValueLine(rawLine.trim(), lineNumber, result);
        }
        return result;
    }

    /** The number of leading spaces/tabs on a line. */
    private static int indentOf(String rawLine) {
        int i = 0;
        while (i < rawLine.length() && (rawLine.charAt(i) == ' ' || rawLine.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static YamlParseException nestedLine(String rawLine, int lineNumber) {
        return new YamlParseException(
            "Nested mapping is out of scope (Yaml reads flat data only; use Json for nested data): "
                + rawLine.trim(), lineNumber);
    }

    /**
     * Parse a top-level block sequence: each line starting with `-` (`isSequenceItem`)
     * opens a new flat-mapping element, and every following non-`-` line adds another
     * `key: value` entry to that same element until the next `-` line or end of input.
     */
    private static List<Object> parseSequence(String[] rawLines) throws YamlParseException {
        List<Object> result = new ArrayList<>();
        LinkedHashMap<String, Object> current = null;
        // Column of the current element's keys (the first key after `- `); a deeper line is nested.
        int keyColumn = 0;
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            String trimmed = rawLine.trim();
            if (isBlankOrComment(rawLine)) {
                continue;
            }
            if (isSequenceItem(trimmed)) {
                current = new LinkedHashMap<>();
                result.add(current);
                String rest = trimmed.equals("-") ? "" : trimmed.substring(2).trim();
                int dashColumn = indentOf(rawLine);
                keyColumn = rest.isEmpty() ? dashColumn + 2 : rawLine.indexOf(rest, dashColumn + 1);
                if (!rest.isEmpty()) {
                    parseKeyValueLine(rest, lineNumber, current);
                }
            } else {
                if (current == null) {
                    throw new YamlParseException("Expected a sequence item ('- ...') but found: " + rawLine, lineNumber);
                }
                if (indentOf(rawLine) > keyColumn) {
                    throw nestedLine(rawLine, lineNumber);
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
        rawValue = stripComment(rawValue).trim();

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
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            throw new IllegalArgumentException(
                "Yaml::stringify supports only flat mappings/sequences: a nested Map/List value "
                    + "is out of scope (no nested maps/sequences within an element)");
        }
        // Fallback – unknown type, quote to be safe
        return renderString(value.toString());
    }

    /**
     * Decide whether to quote a String value and return the result.
     * Quote if:
     *   - empty string
     *   - leading or trailing whitespace
     *   - starts with a YAML indicator ([ ] { } & * ! | > % @ `)
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

    /** Characters that start a non-plain YAML scalar (flow collection, anchor, alias, tag, block scalar, ...). */
    private static final String INDICATORS = "[]{}&*!|>%@`";

    private static boolean needsQuoting(String s) {
        if (s.isEmpty()) return true;
        if (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' ') return true;
        // A leading ' or " would be read back as a quoted scalar, not a literal quote char
        if (s.charAt(0) == '\'' || s.charAt(0) == '"') return true;
        // A leading YAML indicator would be rejected by parse or misread by other YAML readers
        if (INDICATORS.indexOf(s.charAt(0)) >= 0) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':' || c == '#' || c == '\n' || c == '\r' || c == '\t') return true;
        }
        // Would be mis-parsed as a Boolean or null
        if (s.equals("true") || s.equals("false") || s.equals("null") || s.equals("~")) return true;
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
     * Strip a trailing {@code #...} comment from a raw scalar value, respecting both
     * double- and single-quoted regions (a {@code #} inside either kind of quote is
     * just a character). Per YAML, {@code #} starts a comment only when it begins the
     * value or is preceded by whitespace, so {@code tag: a#b} keeps its {@code #}.
     */
    private static String stripComment(String raw) {
        boolean inDouble = false;
        boolean inSingle = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inDouble) {
                if (c == '\\') { i++; continue; } // skip escaped char
                if (c == '"') { inDouble = false; }
                continue;
            }
            if (inSingle) {
                if (c == '\'') {
                    if (i + 1 < raw.length() && raw.charAt(i + 1) == '\'') { i++; continue; } // '' escape
                    inSingle = false;
                }
                continue;
            }
            if (c == '"') { inDouble = true; continue; }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '#' && (i == 0 || raw.charAt(i - 1) == ' ' || raw.charAt(i - 1) == '\t')) {
                return raw.substring(0, i);
            }
        }
        return raw;
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
     *   ""   or "null" or "~" → null
     *   "true" / "false"     → Boolean
     *   -?\d+                → Long
     *   contains '.' or e/E  → Double
     *   "..." (quoted)        → String (unescaped, no further type inference)
     *   '...' (quoted)        → String ('' is a literal ', no other escapes, no type inference)
     *   other                 → String as-is
     */
    private static Object parseScalar(String raw, int lineNumber) throws YamlParseException {
        if (raw.isEmpty() || raw.equals("null") || raw.equals("~")) return null;
        if (raw.equals("true"))  return Boolean.TRUE;
        if (raw.equals("false")) return Boolean.FALSE;

        // Double-quoted string – unescape and return as String (no type coercion)
        if (raw.startsWith("\"")) {
            return unquote(raw, lineNumber);
        }

        // Single-quoted string – only '' -> ' is special, no other escapes, no type coercion
        if (raw.startsWith("'")) {
            return unquoteSingle(raw, lineNumber);
        }

        // Flow collections ([...]/{...}) are out of scope: without this check they would
        // come back as a literal, misleading String instead of the list/map they denote.
        if (raw.charAt(0) == '[' || raw.charAt(0) == '{') {
            throw new YamlParseException(
                "Flow collections are out of scope (quote the value to keep it as text; use Json for lists and maps): "
                    + raw, lineNumber);
        }

        // Block scalars (a `|`/`>` header, then indented lines) are out of scope.
        if (raw.matches("[|>][-+0-9]*")) {
            throw new YamlParseException(
                "Block scalars are out of scope (write the text as one quoted value, with \\n for line breaks): "
                    + raw, lineNumber);
        }

        // Anchors (&name) and aliases (*name) are out of scope: without this check the
        // anchor/alias marker would come back as part of a literal String value.
        if (raw.charAt(0) == '&' || raw.charAt(0) == '*') {
            throw new YamlParseException(
                "Anchors/aliases are out of scope (quote the value to keep it as text): " + raw, lineNumber);
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

    /**
     * Remove surrounding single-quotes from a YAML single-quoted scalar. YAML single-quoted
     * scalars support only one escape -- a doubled {@code ''} for one literal {@code '} --
     * and no backslash escapes at all, unlike {@link #unquote}.
     */
    private static String unquoteSingle(String raw, int lineNumber) throws YamlParseException {
        if (!raw.startsWith("'") || !raw.endsWith("'") || raw.length() < 2) {
            throw new YamlParseException("Malformed single-quoted string: " + raw, lineNumber);
        }
        String inner = raw.substring(1, raw.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\'' && i + 1 < inner.length() && inner.charAt(i + 1) == '\'') {
                sb.append('\'');
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
