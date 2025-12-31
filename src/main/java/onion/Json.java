package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight JSON parser and serializer for Onion programs.
 * No external dependencies - completely self-contained.
 *
 * JSON values are represented as:
 * - JSON object: LinkedHashMap<String, Object>
 * - JSON array: ArrayList<Object>
 * - JSON string: String
 * - JSON number (integer): Long
 * - JSON number (floating-point): Double
 * - JSON boolean: Boolean
 * - JSON null: null
 *
 * Usage:
 *   import static onion.Json::*
 *
 *   Object data = parse("{\"name\": \"John\", \"age\": 30}")
 *   String json = stringify(data)
 */
public final class Json {
    private Json() {} // Prevent instantiation

    // ========== Exceptions ==========

    /**
     * Exception thrown when JSON parsing fails.
     */
    public static class JsonParseException extends Exception {
        private final int position;

        public JsonParseException(String message, int position) {
            super(message + " at position " + position);
            this.position = position;
        }

        public int getPosition() {
            return position;
        }
    }

    // ========== Core API ==========

    /**
     * Parse a JSON string into a Java object.
     * @param json JSON string to parse
     * @return Parsed object (Map, List, String, Long, Double, Boolean, or null)
     * @throws JsonParseException if the JSON is invalid
     */
    public static Object parse(String json) throws JsonParseException {
        if (json == null) {
            throw new JsonParseException("JSON string is null", 0);
        }
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEOF()) {
            throw new JsonParseException("Unexpected characters after JSON value", parser.position);
        }
        return result;
    }

    /**
     * Parse a JSON string, returning null on error instead of throwing.
     * @param json JSON string to parse
     * @return Parsed object, or null if parsing fails
     */
    public static Object parseOrNull(String json) {
        try {
            return parse(json);
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * Convert a Java object to JSON string.
     * @param obj Object to serialize (Map, List, String, Number, Boolean, or null)
     * @return JSON string
     */
    public static String stringify(Object obj) {
        return stringifyInternal(obj, 0, false);
    }

    /**
     * Convert a Java object to pretty-printed JSON string.
     * @param obj Object to serialize
     * @return Pretty-printed JSON string with indentation
     */
    public static String stringifyPretty(Object obj) {
        return stringifyInternal(obj, 0, true);
    }

    // ========== Type-safe accessors ==========

    /**
     * Cast object to Map (JSON object).
     * @param obj Object to cast
     * @return Map if obj is a Map, null otherwise
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }

    /**
     * Cast object to List (JSON array).
     * @param obj Object to cast
     * @return List if obj is a List, null otherwise
     */
    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object obj) {
        return (obj instanceof List) ? (List<Object>) obj : null;
    }

    /**
     * Get a value from a JSON object by key.
     * @param obj JSON object (Map)
     * @param key Key to look up
     * @return Value associated with key, or null
     */
    public static Object get(Object obj, String key) {
        Map<String, Object> map = asObject(obj);
        return (map != null) ? map.get(key) : null;
    }

    /**
     * Get a String value from a JSON object by key.
     * @param obj JSON object
     * @param key Key to look up
     * @return String value, or null if not found or not a String
     */
    public static String getString(Object obj, String key) {
        Object value = get(obj, key);
        return (value instanceof String) ? (String) value : null;
    }

    /**
     * Get an Integer value from a JSON object by key.
     * @param obj JSON object
     * @param key Key to look up
     * @return Integer value, or null if not found or not a number
     */
    public static Integer getInt(Object obj, String key) {
        Object value = get(obj, key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * Get a Double value from a JSON object by key.
     * @param obj JSON object
     * @param key Key to look up
     * @return Double value, or null if not found or not a number
     */
    public static Double getDouble(Object obj, String key) {
        Object value = get(obj, key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    /**
     * Get a Boolean value from a JSON object by key.
     * @param obj JSON object
     * @param key Key to look up
     * @return Boolean value, or null if not found or not a Boolean
     */
    public static Boolean getBoolean(Object obj, String key) {
        Object value = get(obj, key);
        return (value instanceof Boolean) ? (Boolean) value : null;
    }

    // ========== Builder helpers ==========

    /**
     * Create a new empty JSON object (LinkedHashMap).
     * @return Empty map
     */
    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    /**
     * Create a new empty JSON array (ArrayList).
     * @return Empty list
     */
    public static List<Object> array() {
        return new ArrayList<>();
    }

    // ========== JSON Parser (recursive descent) ==========

    private static class JsonParser {
        private final String json;
        private int position = 0;

        JsonParser(String json) {
            this.json = json;
        }

        boolean isEOF() {
            return position >= json.length();
        }

        char peek() throws JsonParseException {
            if (isEOF()) {
                throw new JsonParseException("Unexpected end of JSON", position);
            }
            return json.charAt(position);
        }

        char consume() throws JsonParseException {
            char c = peek();
            position++;
            return c;
        }

        void skipWhitespace() {
            while (!isEOF()) {
                char c = json.charAt(position);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    position++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() throws JsonParseException {
            skipWhitespace();
            if (isEOF()) {
                throw new JsonParseException("Unexpected end of JSON", position);
            }

            char c = peek();
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    return parseTrue();
                case 'f':
                    return parseFalse();
                case 'n':
                    return parseNull();
                case '-':
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    return parseNumber();
                default:
                    throw new JsonParseException("Unexpected character: " + c, position);
            }
        }

        Map<String, Object> parseObject() throws JsonParseException {
            Map<String, Object> result = new LinkedHashMap<>();
            consume(); // consume '{'
            skipWhitespace();

            // Empty object
            if (!isEOF() && peek() == '}') {
                consume();
                return result;
            }

            while (true) {
                skipWhitespace();

                // Parse key (must be string)
                if (peek() != '"') {
                    throw new JsonParseException("Expected string key", position);
                }
                String key = parseString();

                skipWhitespace();

                // Expect ':'
                if (peek() != ':') {
                    throw new JsonParseException("Expected ':' after object key", position);
                }
                consume();

                skipWhitespace();

                // Parse value
                Object value = parseValue();
                result.put(key, value);

                skipWhitespace();

                char c = peek();
                if (c == '}') {
                    consume();
                    break;
                } else if (c == ',') {
                    consume();
                } else {
                    throw new JsonParseException("Expected ',' or '}' in object", position);
                }
            }

            return result;
        }

        List<Object> parseArray() throws JsonParseException {
            List<Object> result = new ArrayList<>();
            consume(); // consume '['
            skipWhitespace();

            // Empty array
            if (!isEOF() && peek() == ']') {
                consume();
                return result;
            }

            while (true) {
                skipWhitespace();
                Object value = parseValue();
                result.add(value);

                skipWhitespace();

                char c = peek();
                if (c == ']') {
                    consume();
                    break;
                } else if (c == ',') {
                    consume();
                } else {
                    throw new JsonParseException("Expected ',' or ']' in array", position);
                }
            }

            return result;
        }

        String parseString() throws JsonParseException {
            consume(); // consume opening '"'
            StringBuilder sb = new StringBuilder();

            while (true) {
                if (isEOF()) {
                    throw new JsonParseException("Unterminated string", position);
                }

                char c = consume();

                if (c == '"') {
                    break; // End of string
                } else if (c == '\\') {
                    // Escape sequence
                    if (isEOF()) {
                        throw new JsonParseException("Unterminated string escape", position);
                    }
                    char escape = consume();
                    switch (escape) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            // Unicode escape: u+4 hex digits
                            if (position + 4 > json.length()) {
                                throw new JsonParseException("Invalid unicode escape", position);
                            }
                            String hex = json.substring(position, position + 4);
                            try {
                                int codePoint = Integer.parseInt(hex, 16);
                                sb.append((char) codePoint);
                                position += 4;
                            } catch (NumberFormatException e) {
                                throw new JsonParseException("Invalid unicode escape", position);
                            }
                            break;
                        default:
                            throw new JsonParseException("Invalid escape sequence: \\" + escape, position);
                    }
                } else if (c < 0x20) {
                    // Control characters must be escaped
                    throw new JsonParseException("Unescaped control character", position);
                } else {
                    sb.append(c);
                }
            }

            return sb.toString();
        }

        Object parseNumber() throws JsonParseException {
            int start = position;

            // Optional minus sign
            if (peek() == '-') {
                consume();
            }

            if (isEOF()) {
                throw new JsonParseException("Invalid number", position);
            }

            // Integer part
            if (peek() == '0') {
                consume();
            } else if (peek() >= '1' && peek() <= '9') {
                while (!isEOF() && peek() >= '0' && peek() <= '9') {
                    consume();
                }
            } else {
                throw new JsonParseException("Invalid number", position);
            }

            boolean isFloat = false;

            // Fractional part
            if (!isEOF() && peek() == '.') {
                isFloat = true;
                consume();
                if (isEOF() || peek() < '0' || peek() > '9') {
                    throw new JsonParseException("Invalid number: expected digit after '.'", position);
                }
                while (!isEOF() && peek() >= '0' && peek() <= '9') {
                    consume();
                }
            }

            // Exponent part
            if (!isEOF() && (peek() == 'e' || peek() == 'E')) {
                isFloat = true;
                consume();
                if (!isEOF() && (peek() == '+' || peek() == '-')) {
                    consume();
                }
                if (isEOF() || peek() < '0' || peek() > '9') {
                    throw new JsonParseException("Invalid number: expected digit in exponent", position);
                }
                while (!isEOF() && peek() >= '0' && peek() <= '9') {
                    consume();
                }
            }

            String numStr = json.substring(start, position);
            try {
                if (isFloat) {
                    return Double.parseDouble(numStr);
                } else {
                    return Long.parseLong(numStr);
                }
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid number format", start);
            }
        }

        Boolean parseTrue() throws JsonParseException {
            if (json.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }
            throw new JsonParseException("Invalid literal", position);
        }

        Boolean parseFalse() throws JsonParseException {
            if (json.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            throw new JsonParseException("Invalid literal", position);
        }

        Object parseNull() throws JsonParseException {
            if (json.startsWith("null", position)) {
                position += 4;
                return null;
            }
            throw new JsonParseException("Invalid literal", position);
        }
    }

    // ========== JSON Stringifier ==========

    private static String stringifyInternal(Object obj, int depth, boolean pretty) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return stringifyString((String) obj);
        } else if (obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj instanceof Map) {
            return stringifyObject((Map<?, ?>) obj, depth, pretty);
        } else if (obj instanceof List) {
            return stringifyArray((List<?>) obj, depth, pretty);
        } else {
            // Fallback for unknown types - convert to string
            return stringifyString(obj.toString());
        }
    }

    private static String stringifyObject(Map<?, ?> map, int depth, boolean pretty) {
        if (map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;

            if (pretty) {
                sb.append('\n');
                sb.append(indent(depth + 1));
            }

            String key = entry.getKey().toString();
            sb.append(stringifyString(key));
            sb.append(':');
            if (pretty) {
                sb.append(' ');
            }
            sb.append(stringifyInternal(entry.getValue(), depth + 1, pretty));
        }

        if (pretty) {
            sb.append('\n');
            sb.append(indent(depth));
        }
        sb.append('}');

        return sb.toString();
    }

    private static String stringifyArray(List<?> list, int depth, boolean pretty) {
        if (list.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('[');

        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;

            if (pretty) {
                sb.append('\n');
                sb.append(indent(depth + 1));
            }

            sb.append(stringifyInternal(item, depth + 1, pretty));
        }

        if (pretty) {
            sb.append('\n');
            sb.append(indent(depth));
        }
        sb.append(']');

        return sb.toString();
    }

    private static String stringifyString(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        // Control characters - use unicode escape
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }

        sb.append('"');
        return sb.toString();
    }

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth * 2; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
