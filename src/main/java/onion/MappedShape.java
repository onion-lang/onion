package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A shape over a structured document whose components are looked up by name.
 *
 * <p>This is the JSON/YAML side of {@link Shapes}. It differs from the regex form in what
 * printing means: a regex renders by dropping values into the gaps between literals, while
 * a document renders by pairing each component's name with its value. So the value side is
 * supplied as an <em>explode</em> function rather than a finished string, and the format
 * turns the resulting map into text.
 */
final class MappedShape<T> implements Shape<T> {

    /** How a format reads text into a map and writes a map back out. */
    interface Codec {
        /** Parses to a map, or throws with a character offset in the text. */
        Object read(String text) throws Exception;
        String write(Map<String, Object> fields);
        /** A character offset from a parse failure, or -1 when the format does not report one. */
        int offsetOf(Exception e);
        String name();
    }

    private final Codec codec;
    private final List<String> names;
    private final List<String> tags;
    private final Function1<List<Object>, T> build;
    private final Function1<T, List<Object>> explode;

    MappedShape(Codec codec, List<String> names, List<String> tags,
                Function1<List<Object>, T> build, Function1<T, List<Object>> explode) {
        this.codec = codec;
        this.names = List.copyOf(names);
        this.tags = List.copyOf(tags);
        this.build = build;
        this.explode = explode;
    }

    @Override
    public Outcome<T> parse(String text, Origin origin) {
        Object doc;
        try {
            doc = codec.read(text);
        } catch (Exception e) {
            // The parser knows where it gave up; the derive! path threw that away.
            Origin at = positionOf(text, codec.offsetOf(e), origin);
            return Outcome.bad(Defect.at(at, "", "valid " + codec.name(), messageOf(e)));
        }
        if (doc == null) {
            return Outcome.bad(Defect.at(origin, "", "valid " + codec.name(), "nothing"));
        }
        List<Object> parts = new ArrayList<>(tags.size());
        List<Defect> problems = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            String name = names.get(i);
            Object raw = Json.get(doc, name);
            if (raw == null) {
                // A missing key used to produce a *successfully constructed* record with a
                // null non-nullable field, which is worse than an error (issue #352).
                problems.add(Defect.at(origin, name, tags.get(i), "absent"));
                continue;
            }
            Outcome<Object> read = Scalars.coerce(tags.get(i), raw, origin, name);
            if (read.isOk()) parts.add(read.get()); else problems.addAll(read.defects());
        }
        if (!problems.isEmpty()) return Outcome.bad(problems);
        return Outcome.ok(build.call(parts));
    }

    @Override
    public boolean canPrint() {
        return explode != null;
    }

    @Override
    public String print(T value) {
        if (explode == null) {
            throw new UnsupportedOperationException("shape " + describe() + " cannot print");
        }
        List<Object> parts = explode.call(value);
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < names.size() && i < parts.size(); i++) {
            fields.put(names.get(i), parts.get(i));
        }
        return codec.write(fields);
    }

    @Override
    public String describe() {
        return codec.name();
    }

    /** Turns a character offset into a line and column, so a failure points somewhere. */
    private static Origin positionOf(String text, int offset, Origin origin) {
        String source = origin == null ? "<input>" : origin.source();
        if (offset < 0 || text == null) return origin;
        int line = 1, column = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') { line++; column = 1; } else { column++; }
        }
        return Origin.at(source, line, column);
    }

    private static String messageOf(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
