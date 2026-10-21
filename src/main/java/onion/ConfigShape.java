package onion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A lossless shape over a commented {@code key = value} config document (issue #362).
 *
 * <p>The format, deliberately small: one entry per line as {@code key = value} (the
 * first {@code =} splits; spacing around it is free), {@code #}-lines are comments,
 * blank lines are blank lines. A value runs to the end of the line.
 *
 * <p>As a plain {@link Shape} it behaves like the JSON/YAML document shapes: components
 * are matched to keys by name, a missing key or an unconvertible value is a positioned
 * {@link Defect}, and {@link #print} renders a canonical document (L1).
 *
 * <p>A {@code String} component's value is read trimmed of surrounding whitespace, same
 * as every other scalar, unless it is in double quotes (#2229): {@code print} quotes a
 * value that is padded or starts with {@code "}, so L1 holds for every {@code String}.
 * Inside the quotes only {@code \"} and {@code \\} are escapes; a quoted value with no
 * closing quote, or a bare quote inside, is a {@link Defect}.
 *
 * <p>What earns the name lossless is the other pair. {@link #parseLossless} keeps a
 * {@link Residue} recording every line verbatim — comments, blank lines, unknown keys,
 * key order, the spacing around each {@code =}, even the original spelling of each
 * value — and {@link #printLossless} reassembles it. Three guarantees, all pinned by
 * spec:
 *
 * <ul>
 *   <li>unedited documents reproduce byte for byte (L2): {@code printLossless(v, r) == t}
 *       whenever {@code parseLossless(t)} yielded {@code (v, r)};</li>
 *   <li>an edited component re-renders only its own value slot — everything else,
 *       including {@code "007"}-style spellings of <em>other</em> values, survives;</li>
 *   <li>an <em>unchanged</em> component keeps its original spelling even though the
 *       typed value round-tripped through {@code Int}: {@code 007} stays {@code 007}
 *       unless the program actually changed the value.</li>
 * </ul>
 */
public final class ConfigShape<T> implements Shape<T> {

    private final List<String> names;
    private final List<String> tags;
    private final Function1<List<Object>, T> build;
    private final Function1<T, List<Object>> explode;

    public ConfigShape(List<String> names, List<String> tags,
                       Function1<List<Object>, T> build, Function1<T, List<Object>> explode) {
        this.names = names;
        this.tags = tags;
        this.build = build;
        this.explode = explode;
    }

    // ------------------------------------------------------------- document model

    /** One source line, kept verbatim. An entry line additionally knows its pieces. */
    private static final class Line {
        final String raw;        // the exact source line, no terminator
        final String key;        // trimmed key, or null for comment/blank/malformed
        final String prefix;     // "key   = " — everything before the value, verbatim
        final String valueRaw;   // the value text, verbatim

        Line(String raw, String key, String prefix, String valueRaw) {
            this.raw = raw; this.key = key; this.prefix = prefix; this.valueRaw = valueRaw;
        }
        static Line verbatim(String raw) { return new Line(raw, null, null, null); }
    }

    private final class ConfigResidue implements Residue {
        final ConfigShape<?> owner = ConfigShape.this;
        final List<Line> lines;
        final Map<String, Integer> entryLines;   // component name -> index into lines
        final List<Object> parsedValues;         // typed values as parsed, for unchanged-detection
        final boolean trailingNewline;

        ConfigResidue(List<Line> lines, Map<String, Integer> entryLines,
                      List<Object> parsedValues, boolean trailingNewline) {
            this.lines = lines; this.entryLines = entryLines;
            this.parsedValues = parsedValues; this.trailingNewline = trailingNewline;
        }
    }

    private List<Line> scan(String text) {
        List<Line> out = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.add(Line.verbatim(raw));
                continue;
            }
            int eq = raw.indexOf('=');
            if (eq < 0) {
                out.add(Line.verbatim(raw)); // malformed; parse reports it, residue keeps it
                continue;
            }
            String key = raw.substring(0, eq).trim();
            String prefix = raw.substring(0, valueStart(raw, eq));
            String valueRaw = raw.substring(valueStart(raw, eq));
            out.add(new Line(raw, key, prefix, valueRaw));
        }
        return out;
    }

    private static int valueStart(String raw, int eq) {
        int i = eq + 1;
        while (i < raw.length() && raw.charAt(i) == ' ') i++;
        return i;
    }

    // ------------------------------------------------------------- Shape

    @Override
    public Outcome<T> parse(String text, Origin origin) {
        Outcome<Lossless<T>> full = parseLossless(text, origin);
        if (full.isBad()) return Outcome.bad(full.defects());
        return Outcome.ok(full.get().value());
    }

    @Override
    public boolean canPrint() { return true; }

    /**
     * Canonical rendering: declaration order, `key = value`, newline-terminated. A
     * nullable (`T?`) component holding `null` has no value to write — the format has no
     * null literal distinct from the text `"null"` — so its line is omitted entirely,
     * matching how an absent key parses back to `null` (#1969).
     */
    @Override
    public String print(T value) {
        List<Object> parts = explode.call(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            Object v = parts.get(i);
            if (v == null) continue;
            String rendered = render(v, tags.get(i));
            requireRenderable(names.get(i), rendered);
            sb.append(names.get(i)).append(" = ").append(rendered).append('\n');
        }
        return sb.toString();
    }

    /**
     * A `key = value` line runs to the end of the line, so a value whose rendered form
     * contains a line break cannot be represented at all: printing it would silently
     * split into an extra line indistinguishable from a real entry, corrupting the
     * document instead of merely failing L2. Refuse rather than misrender.
     */
    private static void requireRenderable(String name, String rendered) {
        if (rendered.indexOf('\n') >= 0 || rendered.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                "config value for '" + name + "' contains a line break, which the line-based "
                    + "`key = value` format cannot represent: " + rendered);
        }
    }

    @Override
    public String describe() {
        return "config(" + String.join(", ", names) + ")";
    }

    // ------------------------------------------------------------- lossless

    @Override
    public boolean isLossless() { return true; }

    @Override
    public Outcome<Lossless<T>> parseLossless(String text, Origin origin) {
        String source = origin == null ? "<input>" : origin.source();
        boolean trailingNewline = text.endsWith("\n");
        String body = trailingNewline ? text.substring(0, text.length() - 1) : text;
        List<Line> lines = scan(body);

        Map<String, Integer> keyToLine = new LinkedHashMap<>();
        List<Defect> defects = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.key == null) {
                String trimmed = line.raw.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    defects.add(Defect.at(Origin.atLine(source, i + 1), "",
                        "`key = value`, a `#` comment, or a blank line", line.raw.trim()));
                }
            } else if (keyToLine.containsKey(line.key)) {
                defects.add(Defect.at(Origin.atLine(source, i + 1), line.key,
                    "a single occurrence of `" + line.key + "`", "a duplicate"));
            } else {
                keyToLine.put(line.key, i);
            }
        }

        Map<String, Integer> entryLines = new LinkedHashMap<>();
        List<Object> values = new ArrayList<>();
        for (int c = 0; c < names.size(); c++) {
            String name = names.get(c);
            String tag = tags.get(c);
            // A trailing "?" marks a nullable (`T?`) component: an absent key reads as a
            // real `null` instead of a defect, the same semantics json/yaml already have
            // (#1969). `Scalars.read` only knows the bare tag, so it is stripped before use.
            boolean nullable = tag.endsWith("?");
            String baseTag = baseTag(tag);
            Integer at = keyToLine.get(name);
            if (at == null) {
                if (!nullable) defects.add(Defect.at(origin, name, tag, "absent"));
                values.add(null);
                continue;
            }
            entryLines.put(name, at);
            Origin lineOrigin = Origin.atLine(source, at + 1);
            Outcome<String> spelled = valueForRead(baseTag, lines.get(at).valueRaw, lineOrigin, name);
            if (spelled.isBad()) {
                defects.addAll(spelled.defects());
                values.add(null);
                continue;
            }
            Outcome<Object> read = Scalars.read(baseTag, spelled.get(), lineOrigin, name);
            if (read.isBad()) {
                defects.addAll(read.defects());
                values.add(null);
            } else {
                values.add(read.get());
            }
        }
        if (!defects.isEmpty()) return Outcome.bad(defects);

        T value = build.call(values);
        return Outcome.ok(new Lossless<>(value,
            new ConfigResidue(lines, entryLines, values, trailingNewline), this));
    }

    @Override
    public String printLossless(T value, Residue residue) {
        if (!(residue instanceof ConfigShape.ConfigResidue)) {
            throw new IllegalArgumentException(
                "residue was not produced by " + describe() + "; refusing to misrender through it");
        }
        @SuppressWarnings("unchecked")
        ConfigResidue r = (ConfigResidue) residue;
        if (r.owner != this && !sameShape(r.owner)) {
            throw new IllegalArgumentException(
                "residue was produced by " + r.owner.describe() + ", not " + describe());
        }

        List<Object> parts = explode.call(value);
        // component name -> replacement text, only where the typed value actually changed;
        // an unchanged component keeps its original spelling ("007" stays "007"). A
        // nullable (`T?`) component edited to `null` has no line to replace -- it is
        // deleted -- and one edited from absent to non-null has none to find -- it is
        // appended -- since the format has no null literal distinct from the text
        // "null" (#1969).
        Map<Integer, String> replacements = new LinkedHashMap<>();
        Set<Integer> deletions = new HashSet<>();
        List<String> insertions = new ArrayList<>();
        for (int c = 0; c < names.size(); c++) {
            Integer at = r.entryLines.get(names.get(c));
            Object now = parts.get(c);
            if (at == null) {
                if (now != null) {
                    String rendered = render(now, tags.get(c));
                    requireRenderable(names.get(c), rendered);
                    insertions.add(names.get(c) + " = " + rendered);
                }
                continue;
            }
            Object was = r.parsedValues.get(c);
            if (java.util.Objects.equals(now, was)) continue;
            if (now == null) {
                deletions.add(at);
            } else {
                String rendered = render(now, tags.get(c));
                requireRenderable(names.get(c), rendered);
                replacements.put(at, rendered);
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean firstOut = true;
        for (int i = 0; i < r.lines.size(); i++) {
            if (deletions.contains(i)) continue;
            Line line = r.lines.get(i);
            if (!firstOut) sb.append('\n');
            firstOut = false;
            String replacement = replacements.get(i);
            if (replacement == null) sb.append(line.raw);
            else {
                sb.append(line.prefix).append(replacement);
                // A CRLF document's lines keep their \r inside valueRaw; an edited
                // line must keep its terminator style too.
                if (line.valueRaw.endsWith("\r")) sb.append('\r');
            }
        }
        // A CRLF document keeps its terminator style on appended lines too (#2130).
        boolean crlf = false;
        for (Line line : r.lines) if (line.raw.endsWith("\r")) { crlf = true; break; }
        for (String inserted : insertions) {
            if (!firstOut) {
                if (crlf && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\r')) sb.append('\r');
                sb.append('\n');
            }
            firstOut = false;
            sb.append(inserted);
        }
        if (r.trailingNewline) {
            if (crlf && !insertions.isEmpty()) sb.append('\r');
            sb.append('\n');
        }
        return sb.toString();
    }

    private boolean sameShape(ConfigShape<?> other) {
        return names.equals(other.names) && tags.equals(other.tags);
    }

    /** A component's tag, stripped of the trailing {@code "?"} that marks it nullable. */
    private static String baseTag(String tag) {
        return tag.endsWith("?") ? tag.substring(0, tag.length() - 1) : tag;
    }

    private static String render(Object v, String tag) {
        if (v instanceof String s && baseTag(tag).equals("String") && needsQuoting(s)) {
            return "\"" + escapeQuoted(s) + "\"";
        }
        return String.valueOf(v);
    }

    /**
     * Whether a {@code String} value must be quoted to survive {@link #print}/{@code parse}
     * (#2229): one with leading/trailing whitespace would otherwise come back trimmed, and
     * one already spelled with a leading {@code "} would otherwise be misread as this very
     * quoting convention on the next parse.
     */
    private static boolean needsQuoting(String s) {
        return !s.equals(s.trim()) || s.startsWith("\"");
    }

    private static String escapeQuoted(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * The text to hand {@link Scalars#read} for one component: trimmed exactly as before
     * for every tag, except a {@code String} value that starts with {@code "}, which is
     * read as the quoted form {@link #render} writes (#2229). Inside the quotes only
     * {@code \"} and {@code \\} are escapes; any other backslash is kept literally, so
     * {@code \n} stays two characters. A quoted value with no closing {@code "}, or with
     * an unescaped {@code "} before its end, is a positioned {@link Defect} rather than a
     * guess at what was meant.
     */
    private static Outcome<String> valueForRead(String baseTag, String rawValue, Origin at, String name) {
        String trimmed = rawValue.trim();
        if (!baseTag.equals("String") || !trimmed.startsWith("\"")) return Outcome.ok(trimmed);
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 1; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length()
                    && (trimmed.charAt(i + 1) == '"' || trimmed.charAt(i + 1) == '\\')) {
                sb.append(trimmed.charAt(++i));
            } else if (c == '"') {
                if (i == trimmed.length() - 1) return Outcome.ok(sb.toString());
                return Outcome.bad(Defect.at(at, name,
                    "a quoted String with `\\\"` for each `\"` inside it", trimmed));
            } else {
                sb.append(c);
            }
        }
        return Outcome.bad(Defect.at(at, name,
            "a closing `\"` (write `\\\\` for a backslash, `\\\"` for a quote)", trimmed));
    }
}
