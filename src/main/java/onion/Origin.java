package onion;

/**
 * Where a value came from, in the text it was read out of.
 *
 * <p>This is the runtime counterpart to the compiler's {@code onion.compiler.Location}.
 * Onion had nothing like it: a parse failure could not say which line it happened on even
 * when the underlying parser knew — {@code Json.JsonParseException} carries a character
 * offset and {@code Yaml.YamlParseException} a line, and both were discarded before
 * reaching the caller. Every boundary failure that reports a position is built on this.
 *
 * <p>{@code source} is deliberately free-form: a file path, a URL, {@code "<stdin>"} or
 * {@code "<literal>"} depending on where the text entered the program. Line and column are
 * 1-based, matching every compiler and editor. A {@code column} of {@link #NO_COLUMN}
 * means the position is known only to the line — a parser that reports lines but not
 * columns must not invent one.
 *
 * @param source where the text came from
 * @param line   1-based line
 * @param column 1-based column, or {@link #NO_COLUMN}
 * @param span   how many characters the value occupies, at least 1
 */
public record Origin(String source, int line, int column, int span) {

    /** Column value meaning "known to the line only". */
    public static final int NO_COLUMN = 0;

    /** An origin at a line and column, spanning a single character. */
    public static Origin at(String source, int line, int column) {
        return new Origin(source, line, column, 1);
    }

    /** An origin known only to the line — what a line-oriented parser can honestly report. */
    public static Origin atLine(String source, int line) {
        return new Origin(source, line, NO_COLUMN, 1);
    }

    /** An origin covering {@code span} characters from {@code column}. */
    public static Origin spanning(String source, int line, int column, int span) {
        return new Origin(source, line, column, Math.max(1, span));
    }

    /** Whether a column is known, as opposed to only a line. */
    public boolean hasColumn() {
        return column > NO_COLUMN;
    }

    /**
     * The same position moved to {@code line} of an enclosing text.
     *
     * <p>Parsing a document line by line means each sub-parse reports positions relative to
     * its own line; this lifts one back into the whole document.
     */
    public Origin onLine(int line) {
        return new Origin(source, line, column, span);
    }

    /** The same position in a different source, keeping line, column and span. */
    public Origin inSource(String source) {
        return new Origin(source, line, column, span);
    }

    /**
     * {@code file:line:column}, or {@code file:line} when only the line is known — the
     * form every compiler and editor already knows how to parse.
     */
    public String describe() {
        return hasColumn() ? source + ":" + line + ":" + column : source + ":" + line;
    }

    @Override
    public String toString() {
        return describe();
    }
}
