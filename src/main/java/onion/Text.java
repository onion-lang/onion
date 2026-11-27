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
     * Greedily word-wraps {@code text} into lines at most {@code width} terminal
     * columns wide, counted as {@link #table} counts them (a full-width character
     * takes two). Lines break at spaces, and also before or after a full-width
     * character, so Japanese or Chinese text with no spaces wraps too; no space is
     * added at such a break. Closing punctuation ({@code 。、」）}) never starts a
     * line and an opening bracket ({@code 「（}) never ends one. A piece that cannot
     * be broken and is wider than {@code width} still occupies its own line.
     * Existing newlines are treated as hard breaks. Returns the wrapped lines.
     */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        int w = Math.max(1, width);
        for (String paragraph : text.split("\n", -1)) {
            String[] words = paragraph.trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            int lineWidth = 0;
            for (String word : words) {
                if (word.isEmpty()) continue;
                boolean firstPiece = true;
                for (String piece : breakablePieces(word)) {
                    int pw = displayWidth(piece);
                    int sep = (firstPiece && line.length() > 0) ? 1 : 0;
                    if (line.length() == 0) {
                        line.append(piece);
                        lineWidth = pw;
                    } else if (lineWidth + sep + pw <= w) {
                        if (sep == 1) line.append(' ');
                        line.append(piece);
                        lineWidth += sep + pw;
                    } else {
                        // An opening bracket left at the end of the line moves down with the piece.
                        String carry = "";
                        int cut = line.length();
                        while (cut > 0) {
                            int cp = line.codePointBefore(cut);
                            if (NO_LINE_END.indexOf(cp) < 0) break;
                            cut -= Character.charCount(cp);
                        }
                        if (cut > 0 && cut < line.length()) {
                            carry = line.substring(cut);
                            line.setLength(cut);
                        }
                        lines.add(line.toString());
                        line = new StringBuilder(carry).append(piece);
                        lineWidth = displayWidth(carry) + pw;
                    }
                    firstPiece = false;
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Closing punctuation that must not start a line (kinsoku). */
    private static final String NO_LINE_START = "、。，．・：；？！ー）」』】〕〉》〙〗｝］…‥ゝゞヽヾ々〆ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ),.;:!?]}";
    /** Opening brackets that must not end a line. */
    private static final String NO_LINE_END = "（「『【〔〈《〘〖｛［([";

    /**
     * Splits one space-free word into the pieces a line may break between: a run of
     * narrow characters stays whole, and each full-width character is its own piece.
     * Closing punctuation and zero-width marks join the piece before them, and an
     * opening bracket joins the piece after it.
     */
    private static List<String> breakablePieces(String word) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean curWide = false;
        boolean glueNext = false;
        for (int i = 0; i < word.length(); ) {
            int cp = word.codePointAt(i);
            i += Character.charCount(cp);
            boolean wide = isWide(cp);
            boolean breakHere = cur.length() > 0 && (wide || curWide)
                && !glueNext && !isZeroWidth(cp) && NO_LINE_START.indexOf(cp) < 0;
            if (breakHere) {
                pieces.add(cur.toString());
                cur = new StringBuilder();
            }
            cur.appendCodePoint(cp);
            if (!isZeroWidth(cp)) curWide = wide;
            glueNext = NO_LINE_END.indexOf(cp) >= 0 || cp == 0x200D;
        }
        if (cur.length() > 0) pieces.add(cur.toString());
        return pieces;
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
     * padded with empty cells. Widths are measured in terminal columns, so
     * full-width (CJK) characters count as two and combining marks as zero.
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
                widths[c] = Math.max(widths[c], displayWidth(cell));
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
                    for (int p = displayWidth(cell); p < widths[c]; p++) sb.append(' ');
                    sb.append("  ");
                }
            }
        }
        return sb.toString();
    }

    /** Terminal columns a string occupies: 2 per full-width character, 0 per combining mark, else 1. */
    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (isZeroWidth(cp)) continue;
            w += isWide(cp) ? 2 : 1;
        }
        return w;
    }

    private static boolean isZeroWidth(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
            || type == Character.FORMAT;
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)
            || (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F)
            || (cp >= 0xAC00 && cp <= 0xD7A3)
            || (cp >= 0xF900 && cp <= 0xFAFF)
            || (cp >= 0xFE30 && cp <= 0xFE6F)
            || (cp >= 0xFF00 && cp <= 0xFF60)
            || (cp >= 0xFFE0 && cp <= 0xFFE6)
            || (cp >= 0x1F300 && cp <= 0x1F64F)
            || (cp >= 0x1F900 && cp <= 0x1F9FF)
            || (cp >= 0x20000 && cp <= 0x3FFFD);
    }
}
