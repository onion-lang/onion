package onion;

import java.util.ArrayList;
import java.util.List;

/**
 * Text layout helpers for console output, help text and reports (`onion.Text`):
 * word wrapping, indenting, dedenting and simple aligned tables. Complements
 * {@link Format} (which formats numbers) and {@link Strings} (single-string ops).
 */
public final class Text {
    private Text() {
    }

    /**
     * Greedily word-wraps {@code text} into lines no longer than {@code width}
     * (a word longer than {@code width} still occupies its own line). Existing
     * newlines are treated as hard breaks. Returns the wrapped lines.
     */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        int w = Math.max(1, width);
        for (String paragraph : text.split("\n", -1)) {
            String[] words = paragraph.trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (line.length() == 0) {
                    line.append(word);
                } else if (line.length() + 1 + word.length() <= w) {
                    line.append(' ').append(word);
                } else {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Prefixes every line of {@code text} with {@code prefix}. */
    public static String indent(String text, String prefix) {
        if (text == null) return "";
        String p = prefix == null ? "" : prefix;
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(p).append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * Removes the longest run of leading whitespace common to every non-blank
     * line — useful for cleaning up indented multi-line string literals.
     */
    public static String dedent(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        int common = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            int lead = 0;
            while (lead < line.length() && (line.charAt(lead) == ' ' || line.charAt(lead) == '\t')) {
                lead++;
            }
            common = Math.min(common, lead);
        }
        if (common == Integer.MAX_VALUE || common == 0) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            String line = lines[i];
            sb.append(line.length() >= common ? line.substring(common) : line);
        }
        return sb.toString();
    }

    /**
     * Renders rows of cells as a left-justified text table: each column is padded
     * to its widest cell and columns are separated by two spaces. Ragged rows are
     * padded with empty cells.
     */
    public static String table(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) return "";
        int cols = 0;
        for (List<String> row : rows) {
            if (row != null) cols = Math.max(cols, row.size());
        }
        int[] widths = new int[cols];
        for (List<String> row : rows) {
            if (row == null) continue;
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c) == null ? "" : row.get(c);
                widths[c] = Math.max(widths[c], cell.length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (r > 0) sb.append('\n');
            for (int c = 0; c < cols; c++) {
                String cell = row != null && c < row.size() && row.get(c) != null ? row.get(c) : "";
                sb.append(cell);
                if (c < cols - 1) {
                    for (int p = cell.length(); p < widths[c]; p++) sb.append(' ');
                    sb.append("  ");
                }
            }
        }
        return sb.toString();
    }
}
