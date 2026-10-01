package onion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A shape over a structured document whose components are looked up by name.
 *
 * <p>This is the JSON/YAML side of {@link Shapes}. It differs from the regex form in what
 * printing means: a regex renders by dropping values into the gaps between literals, while
 * a document renders by pairing each component's name with its value. So the value side is
 * supplied as an <em>explode</em> function rather than a finished string, and the format
 * turns the resulting map into text.
 *
 * <h2>Component tags</h2>
 *
 * <p>Each component carries a tag naming what it reads:
 *
 * <ul>
 *   <li>a scalar kind &mdash; {@code String}, {@code Int}, {@code Long}, {@code Double},
 *       {@code Float}, {@code Boolean}, {@code Short}, {@code Byte};</li>
 *   <li>{@code Nested} &mdash; an object read by another document shape, supplied through
 *       the {@code nested} list;</li>
 *   <li>{@code List[K]} &mdash; an array whose elements are all of kind {@code K} (a scalar
 *       kind or {@code Nested});</li>
 *   <li>any of the above followed by {@code ?} &mdash; the key may be absent or
 *       {@code null}, and the component is then {@code null}.</li>
 * </ul>
 *
 * <p>A defect inside a structure carries the whole path to it, e.g. {@code actions[2]} or
 * {@code owner.name}, and a bad element never hides its siblings: every element of every
 * array is read, and every defect is reported.
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

    /** One component: what it reads, parsed once out of its tag. */
    private static final class Kind {
        final String scalar;       // scalar tag, or null for Nested
        final boolean list;
        final boolean nullable;
        final Function0<Object> nested; // for Nested, the (lazy) shape of the element

        Kind(String tag, Function0<Object> nested) {
            String t = tag;
            this.nullable = t.endsWith("?");
            if (nullable) t = t.substring(0, t.length() - 1);
            this.list = t.startsWith("List[") && t.endsWith("]");
            if (list) t = t.substring(5, t.length() - 1);
            if (t.equals("Nested")) {
                if (nested == null) {
                    throw new IllegalArgumentException("component tag " + tag + " needs a nested shape");
                }
                this.scalar = null;
            } else {
                this.scalar = t;
            }
            this.nested = nested;
        }

        boolean isNested() { return scalar == null; }

        /** What one element must be, for a defect's {@code expected}. */
        String elementExpected() { return isNested() ? "object" : scalar; }

        /** What the whole component must be, for a defect's {@code expected}. */
        String expected() { return list ? "List[" + elementExpected() + "]" : elementExpected(); }
    }

    private final Codec codec;
    private final List<String> names;
    private final List<Kind> kinds;
    private final Function1<List<Object>, T> build;
    private final Function1<T, List<Object>> explode;
    /** Resolved nested shapes, by component index; each resolved once, on first use. */
    private final Object[] resolved;

    MappedShape(Codec codec, List<String> names, List<String> tags,
                Function1<List<Object>, T> build, Function1<T, List<Object>> explode) {
        this(codec, names, tags, null, build, explode);
    }

    @SuppressWarnings("unchecked")
    MappedShape(Codec codec, List<String> names, List<String> tags, List<Object> nested,
                Function1<List<Object>, T> build, Function1<T, List<Object>> explode) {
        this.codec = codec;
        this.names = List.copyOf(names);
        List<String> ts = List.copyOf(tags);
        List<Kind> ks = new ArrayList<>(ts.size());
        for (int i = 0; i < ts.size(); i++) {
            Object n = (nested != null && i < nested.size()) ? nested.get(i) : null;
            ks.add(new Kind(ts.get(i), (Function0<Object>) n));
        }
        this.kinds = Collections.unmodifiableList(ks);
        this.build = build;
        this.explode = explode;
        this.resolved = new Object[ts.size()];
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
        return readFields(doc, origin, "");
    }

    /**
     * Reads every component out of an already-parsed document, prefixing each defect's
     * path with {@code path} (empty at the top level).
     */
    Outcome<T> readFields(Object doc, Origin origin, String path) {
        List<Object> parts = new ArrayList<>(kinds.size());
        List<Defect> problems = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            String name = names.get(i);
            Object raw = Json.get(doc, name);
            String at = path.isEmpty() ? name : path + "." + name;
            Outcome<Object> read = readComponent(i, kinds.get(i), raw, origin, at);
            if (read.isOk()) parts.add(read.get()); else problems.addAll(read.defects());
        }
        if (!problems.isEmpty()) return Outcome.bad(problems);
        return Outcome.ok(build.call(parts));
    }

    private Outcome<Object> readComponent(int index, Kind kind, Object raw, Origin origin, String path) {
        if (raw == null) {
            // A missing key used to produce a *successfully constructed* record with a
            // null non-nullable field, which is worse than an error (issue #352). A
            // nullable component is the one place absence is a value.
            if (kind.nullable) return Outcome.ok(null);
            return Outcome.bad(Defect.at(origin, path, kind.expected(), "absent"));
        }
        if (!kind.list) return readElement(index, kind, raw, origin, path);
        if (!(raw instanceof List)) {
            return Outcome.bad(Defect.at(origin, path, kind.expected(), describe(raw)));
        }
        List<?> items = (List<?>) raw;
        List<Object> values = new ArrayList<>(items.size());
        List<Defect> problems = new ArrayList<>();
        for (int j = 0; j < items.size(); j++) {
            String at = path + "[" + j + "]";
            Object item = items.get(j);
            if (item == null) {
                problems.add(Defect.at(origin, at, kind.elementExpected(), "null"));
                continue;
            }
            // Keep reading after a bad element: one bad element must not hide the others.
            Outcome<Object> read = readElement(index, kind, item, origin, at);
            if (read.isOk()) values.add(read.get()); else problems.addAll(read.defects());
        }
        if (!problems.isEmpty()) return Outcome.bad(problems);
        return Outcome.ok((Object) values);
    }

    @SuppressWarnings("unchecked")
    private Outcome<Object> readElement(int index, Kind kind, Object raw, Origin origin, String path) {
        if (!kind.isNested()) return Scalars.coerce(kind.scalar, raw, origin, path);
        if (!(raw instanceof Map)) {
            return Outcome.bad(Defect.at(origin, path, "object", describe(raw)));
        }
        Shape<Object> inner = nestedShape(index);
        if (inner instanceof MappedShape) {
            return ((MappedShape<Object>) inner).readFields(raw, origin, path);
        }
        // Not a document shape we can walk into: hand it the subtree as text, and move its
        // defects under this component's path.
        Outcome<Object> read = inner.parse(Json.stringify(raw), origin);
        if (read.isOk()) return read;
        List<Defect> moved = new ArrayList<>();
        for (Defect d : read.defects()) {
            String p = d.path() == null || d.path().isEmpty() ? path : path + "." + d.path();
            moved.add(new Defect(d.origin(), p, d.expected(), d.actual()));
        }
        return Outcome.bad(moved);
    }

    @SuppressWarnings("unchecked")
    private Shape<Object> nestedShape(int index) {
        Object s = resolved[index];
        if (s == null) {
            s = kinds.get(index).nested.call();
            if (!(s instanceof Shape)) {
                throw new IllegalStateException(
                    "component " + names.get(index) + " has no nested shape (got " + s + ")");
            }
            resolved[index] = s;
        }
        return (Shape<Object>) s;
    }

    @Override
    public boolean canPrint() {
        return explode != null;
    }

    @Override
    public String print(T value) {
        return codec.write(toTree(value));
    }

    /** The document a value prints as, before the format renders it to text. */
    Map<String, Object> toTree(T value) {
        if (explode == null) {
            throw new UnsupportedOperationException("shape " + describe() + " cannot print");
        }
        List<Object> parts = explode.call(value);
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < names.size() && i < parts.size(); i++) {
            fields.put(names.get(i), writeComponent(i, kinds.get(i), parts.get(i)));
        }
        return fields;
    }

    private Object writeComponent(int index, Kind kind, Object v) {
        if (v == null) return null;
        if (!kind.list) return writeElement(index, kind, v);
        List<Object> out = new ArrayList<>();
        for (Object item : (List<?>) v) out.add(writeElement(index, kind, item));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object writeElement(int index, Kind kind, Object v) {
        if (!kind.isNested() || v == null) return v;
        Shape<Object> inner = nestedShape(index);
        if (inner instanceof MappedShape) return ((MappedShape<Object>) inner).toTree(v);
        return Json.parseOrNull(inner.print(v));
    }

    @Override
    public String describe() {
        return codec.name();
    }

    // ------------------------------------------------------------- JSON Schema

    @Override
    public boolean hasJsonSchema() {
        return isJson();
    }

    @Override
    public String jsonSchema() {
        if (!isJson()) {
            throw new UnsupportedOperationException(
                "shape " + describe() + " is not a JSON shape, so it has no JSON Schema");
        }
        return Json.stringify(schemaTree(Collections.newSetFromMap(new IdentityHashMap<>())));
    }

    private boolean isJson() {
        return "JSON".equals(codec.name());
    }

    /**
     * {@code {"type": "object", "properties": ..., "required": [...], "additionalProperties": false}}.
     *
     * <p>{@code visiting} holds the build function's class of every record on the current
     * path: a record that contains itself, directly or through another, has no finite
     * inline schema, and saying so beats overflowing the stack.
     */
    private Map<String, Object> schemaTree(Set<Object> visiting) {
        Object key = build.getClass();
        if (!visiting.add(key)) {
            throw new UnsupportedOperationException(
                "a recursive JSON shape has no finite inline JSON Schema");
        }
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            List<Object> required = new ArrayList<>();
            for (int i = 0; i < kinds.size(); i++) {
                Kind kind = kinds.get(i);
                props.put(names.get(i), componentSchema(i, kind, visiting));
                if (!kind.nullable) required.add(names.get(i));
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", required);
            schema.put("additionalProperties", Boolean.FALSE);
            return schema;
        } finally {
            visiting.remove(key);
        }
    }

    private Object componentSchema(int index, Kind kind, Set<Object> visiting) {
        Map<String, Object> element = elementSchema(index, kind, visiting);
        Map<String, Object> schema;
        if (kind.list) {
            schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", element);
        } else {
            schema = element;
        }
        if (!kind.nullable) return schema;
        if (!kind.list && kind.isNested()) {
            // An object schema carries properties/required alongside its type; widening the
            // type to ["object", "null"] would leave them describing a null. anyOf keeps the
            // object schema whole.
            Map<String, Object> nullSchema = new LinkedHashMap<>();
            nullSchema.put("type", "null");
            Map<String, Object> any = new LinkedHashMap<>();
            any.put("anyOf", List.of(schema, nullSchema));
            return any;
        }
        schema.put("type", List.of(schema.get("type"), "null"));
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> elementSchema(int index, Kind kind, Set<Object> visiting) {
        if (kind.isNested()) {
            Shape<Object> inner = nestedShape(index);
            if (inner instanceof MappedShape) return ((MappedShape<Object>) inner).schemaTree(visiting);
            if (inner.hasJsonSchema()) {
                Object parsed = Json.parseOrNull(inner.jsonSchema());
                if (parsed instanceof Map) return new LinkedHashMap<>((Map<String, Object>) parsed);
            }
            throw new UnsupportedOperationException(
                "component " + names.get(index) + " is read by " + inner.describe() + ", which has no JSON Schema");
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", schemaTypeOf(kind.scalar));
        return schema;
    }

    private static String schemaTypeOf(String scalar) {
        switch (scalar) {
            case "Int": case "Long": case "Short": case "Byte": return "integer";
            case "Double": case "Float": return "number";
            case "Boolean": return "boolean";
            default: return "string";
        }
    }

    // ------------------------------------------------------------- helpers

    /** What was found, for a defect's `actual` -- the value's shape, not a dump of it. */
    private static String describe(Object value) {
        if (value instanceof String) return "\"" + value + "\"";
        if (value instanceof Map) return "an object";
        if (value instanceof List) return "an array";
        return String.valueOf(value);
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
