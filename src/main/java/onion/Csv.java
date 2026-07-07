package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-contained CSV utilities (RFC 4180): quoted fields, embedded commas,
 * embedded newlines, and doubled quotes are handled. No external dependencies,
 * in the same spirit as {@link Json}.
 *
 * Example usage:
 * <pre>
 * val rows = Csv::parse(Files::readText("data.csv"))      // List of List of String
 * val recs = Csv::parseWithHeader(text)                    // List of Map (header -> value)
 * val text = Csv::stringify(rows)
 * </pre>
 */
public final class Csv {
    private Csv() {
    }

    /**
     * Parses CSV text into rows of fields. Handles RFC 4180 quoting:
     * fields may be wrapped in double quotes, inside which commas, CR/LF and
     * doubled quotes ("") are literal. Empty trailing line is ignored.
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) return rows;
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
            } else if (c == '"' && field.length() == 0) {
                inQuotes = true;
                fieldStarted = true;
                i++;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                i++;
            } else if (c == '\r' || c == '\n') {
                // End of record. Swallow a CRLF pair as one terminator.
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                i++;
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
                i++;
            }
        }
        if (field.length() > 0 || fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Parses CSV text whose first row is a header, yielding one ordered map
     * per data row (header name -> field value). Rows shorter than the header
     * map missing fields to ""; extra fields are ignored.
     */
    public static List<Map<String, String>> parseWithHeader(String text) {
        List<List<String>> rows = parse(text);
        List<Map<String, String>> result = new ArrayList<>();
        if (rows.isEmpty()) return result;
        List<String> header = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, String> rec = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                rec.put(header.get(c), c < row.size() ? row.get(c) : "");
            }
            result.add(rec);
        }
        return result;
    }

    /**
     * Serializes rows to CSV text. Fields containing a comma, quote or
     * newline are quoted, with quotes doubled. Records are joined with \n.
     */
    public static String stringify(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) sb.append(',');
                sb.append(quoteIfNeeded(row.get(c)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Serializes header-keyed records to CSV text (the inverse of
     * {@link #parseWithHeader}). The header row is taken from the union of keys
     * across all records, in first-seen order; a record missing a column emits
     * an empty field. Returns "" for an empty list.
     */
    public static String stringifyWithHeader(List<Map<String, String>> records) {
        if (records == null || records.isEmpty()) return "";
        List<String> header = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map<String, String> rec : records) {
            if (rec != null) {
                for (String key : rec.keySet()) {
                    if (seen.add(key)) header.add(key);
                }
            }
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(header);
        for (Map<String, String> rec : records) {
            List<String> row = new ArrayList<>();
            for (String key : header) {
                String v = rec == null ? null : rec.get(key);
                row.add(v == null ? "" : v);
            }
            rows.add(row);
        }
        return stringify(rows);
    }

    /**
     * Extracts a single column (by zero-based index) from parsed rows. A row
     * shorter than {@code index} contributes "".
     */
    public static List<String> column(List<List<String>> rows, int index) {
        List<String> result = new ArrayList<>();
        if (rows == null) return result;
        for (List<String> row : rows) {
            result.add(index >= 0 && index < row.size() ? row.get(index) : "");
        }
        return result;
    }

    /**
     * Extracts a named column from header-keyed records (see
     * {@link #parseWithHeader}). A record without the column contributes "".
     */
    public static List<String> columnByName(List<Map<String, String>> records, String name) {
        List<String> result = new ArrayList<>();
        if (records == null) return result;
        for (Map<String, String> rec : records) {
            String v = rec == null ? null : rec.get(name);
            result.add(v == null ? "" : v);
        }
        return result;
    }

    private static String quoteIfNeeded(String field) {
        if (field == null) return "";
        boolean needs = field.indexOf(',') >= 0 || field.indexOf('"') >= 0
            || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0;
        if (!needs) return field;
        return '"' + field.replace("\"", "\"\"") + '"';
    }
}
