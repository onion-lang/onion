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

    private static String quoteIfNeeded(String field) {
        if (field == null) return "";
        boolean needs = field.indexOf(',') >= 0 || field.indexOf('"') >= 0
            || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0;
        if (!needs) return field;
        return '"' + field.replace("\"", "\"\"") + '"';
    }
}
