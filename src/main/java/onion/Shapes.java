package onion;

import java.util.ArrayList;
import java.util.List;

/**
 * Ways of building a {@link Shape}.
 *
 * <p>These are what a {@code shape} declaration lowers to. They are ordinary API, so a
 * shape the compiler cannot derive can still be written by hand.
 */
public final class Shapes {

    private Shapes() {}

    /**
     * A shape backed by an anchored regular expression: one capture group per component.
     *
     * <p>Component failures <strong>accumulate</strong>. Reading {@code "abc,def"} as two
     * {@code Int}s reports two defects, not the first one — which is the whole reason
     * {@link Outcome} exists rather than {@link Result}.
     *
     * @param pattern    the regular expression; matched against the whole text
     * @param names      component names, used as the {@code path} of any defect
     * @param tags       component scalar kinds, in the vocabulary of the conversion table
     * @param build      assembles the converted components into a value
     * @param printer    renders a value back, or {@code null} when the pattern has no
     *                   unique rendering and the shape is therefore read-only
     */
    public static <T> Shape<T> regex(
        String pattern,
        List<String> names,
        List<String> tags,
        Function1<List<Object>, T> build,
        Function1<T, String> printer
    ) {
        return new RegexShape<>(pattern, names, tags, build, printer);
    }

    /**
     * A shape over a JSON document: one key per component, looked up by name.
     *
     * @param names   component names, used both as document keys and as defect paths
     * @param tags    component scalar kinds
     * @param build   assembles the read components into a value
     * @param explode the component values of a value, in declaration order, or
     *                {@code null} for a read-only shape
     */
    public static <T> Shape<T> json(
        List<String> names, List<String> tags,
        Function1<List<Object>, T> build, Function1<T, List<Object>> explode
    ) {
        return new MappedShape<>(JSON, names, tags, build, explode);
    }

    /**
     * A lossless shape over a commented {@code key = value} config document. Unlike
     * {@link #json} and {@link #yaml} this one also supports
     * {@link Shape#parseLossless}/{@link Shape#printLossless}: comments, blank lines,
     * spacing, key order, unknown keys and unchanged values' spellings all survive a
     * round trip (L2). See {@link ConfigShape}.
     */
    public static <T> Shape<T> config(
        List<String> names, List<String> tags,
        Function1<List<Object>, T> build, Function1<T, List<Object>> explode
    ) {
        return new ConfigShape<>(names, tags, build, explode);
    }

    /** A shape over a flat YAML document. */
    public static <T> Shape<T> yaml(
        List<String> names, List<String> tags,
        Function1<List<Object>, T> build, Function1<T, List<Object>> explode
    ) {
        return new MappedShape<>(YAML, names, tags, build, explode);
    }

    private static final MappedShape.Codec JSON = new MappedShape.Codec() {
        public Object read(String text) throws Exception { return Json.parse(text); }
        public String write(java.util.Map<String, Object> fields) { return Json.stringify(fields); }
        public int offsetOf(Exception e) {
            return (e instanceof Json.JsonParseException) ? ((Json.JsonParseException) e).getPosition() : -1;
        }
        public String name() { return "JSON"; }
    };

    private static final MappedShape.Codec YAML = new MappedShape.Codec() {
        public Object read(String text) throws Exception { return Yaml.parse(text); }
        public String write(java.util.Map<String, Object> fields) { return Yaml.stringify(fields); }
        public int offsetOf(Exception e) {
            // Yaml reports a line rather than an offset; MappedShape only understands
            // offsets, so let it fall back to the caller's origin instead of guessing.
            return -1;
        }
        public String name() { return "YAML"; }
    };

    private static final class RegexShape<T> implements Shape<T> {
        private final String pattern;
        private final List<String> names;
        private final List<String> tags;
        private final Function1<List<Object>, T> build;
        private final Function1<T, String> printer;

        RegexShape(String pattern, List<String> names, List<String> tags,
                   Function1<List<Object>, T> build, Function1<T, String> printer) {
            this.pattern = pattern;
            this.names = List.copyOf(names);
            this.tags = List.copyOf(tags);
            this.build = build;
            this.printer = printer;
        }

        @Override
        public Outcome<T> parse(String text, Origin origin) {
            List<String> groups = Regex.matchGroups(text, pattern);
            if (groups == null) {
                // A non-match belongs to the whole text, not to any one component, so its
                // path is empty -- that is what distinguishes it from a bad field, which
                // the current derivation cannot express because both return null.
                return Outcome.bad(Defect.at(origin, "", "match of /" + pattern + "/", abbreviate(text)));
            }
            List<Object> parts = new ArrayList<>(tags.size());
            List<Defect> problems = new ArrayList<>();
            for (int i = 0; i < tags.size(); i++) {
                String raw = i < groups.size() ? groups.get(i) : "";
                String name = i < names.size() ? names.get(i) : String.valueOf(i);
                Outcome<Object> read = Scalars.read(tags.get(i), raw, origin, name);
                if (read.isOk()) parts.add(read.get()); else problems.addAll(read.defects());
            }
            if (!problems.isEmpty()) return Outcome.bad(problems);
            return Outcome.ok(build.call(parts));
        }

        @Override
        public boolean canPrint() {
            return printer != null;
        }

        @Override
        public String print(T value) {
            if (printer == null) {
                throw new UnsupportedOperationException(
                    "shape " + describe() + " is not invertible, so it cannot print");
            }
            return printer.call(value);
        }

        @Override
        public String describe() {
            return "re\"" + pattern + "\"";
        }

        private static String abbreviate(String text) {
            if (text == null) return "null";
            return text.length() <= 40 ? text : text.substring(0, 37) + "...";
        }
    }
}
