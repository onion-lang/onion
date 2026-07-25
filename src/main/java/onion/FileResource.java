package onion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A file as a typed resource. Created by the {@code file"path"} literal (or
 * {@code file(path)}); reading methods derive the parse step from what you ask
 * for, so the read-parse layer collapses to one call:
 *
 * <pre>
 * val text = file"notes.txt".text
 * val rows = file"data.csv".csv          // List of List of String
 * val recs = file"data.csv".csvRows      // List of Map (header -> value)
 * val conf = file"config.json".json      // parsed JSON value
 * foreach line: String in file"app.log".lines { ... }
 * </pre>
 */
public final class FileResource {
    private final String path;

    public FileResource(String path) {
        this.path = path;
    }

    /** The underlying path string. */
    public String path() {
        return path;
    }

    /** Whole file as text (UTF-8). */
    public String text() throws IOException {
        return Files.readText(path);
    }

    /** File contents split into lines. */
    public List<String> lines() throws IOException {
        return Files.readLines(path);
    }

    /** File parsed as JSON (see {@link Json#parse}). */
    public Object json() throws Exception {
        return Json.parse(text());
    }

    /** File parsed as CSV rows (see {@link Csv#parse}). */
    public List<List<String>> csv() throws IOException {
        return Csv.parse(text());
    }

    /** File parsed as CSV with a header row (see {@link Csv#parseWithHeader}). */
    public List<Map<String, String>> csvRows() throws IOException {
        return Csv.parseWithHeader(text());
    }

    /**
     * The file read through {@code shape}.
     *
     * <p>The fixed getters above close the set of things a file can be read as -- the
     * parse step is chosen by which one you call, and a user cannot add to it. A shape
     * opens that set, and carries this file's path into every defect, so a failure says
     * *which file* rather than only what was wrong (issue #353). Named `read` rather than `as`, which is Onion's cast keyword.
     *
     * <p>An unreadable file is a defect too, not an exception: reading a file that might
     * not be there is the ordinary case at a boundary.
     */
    public <T> Outcome<T> read(Shape<T> shape) {
        String content;
        try {
            content = text();
        } catch (IOException e) {
            return Outcome.bad(Defect.at(Origin.atLine(path, 1), "", "a readable file", describe(e)));
        }
        return shape.parse(content, Origin.atLine(path, 1));
    }

    /**
     * One value per line, keeping both the lines that read and the reasons the rest did
     * not -- see {@link Shape#eachLine}. Each defect is positioned on its line of this file.
     */
    public <T> List<Outcome<T>> eachLine(Shape<T> shape) {
        String content;
        try {
            content = text();
        } catch (IOException e) {
            List<Outcome<T>> failed = new java.util.ArrayList<>();
            failed.add(Outcome.bad(Defect.at(Origin.atLine(path, 1), "", "a readable file", describe(e))));
            return failed;
        }
        return shape.eachLine(content, Origin.atLine(path, 1));
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    /** Whether the file exists. */
    public boolean exists() {
        return Files.exists(path);
    }

    /** Replaces the file's contents. */
    public void write(String content) throws IOException {
        Files.writeText(path, content);
    }

    /** Appends to the file. */
    public void append(String content) throws IOException {
        Files.appendText(path, content);
    }

    @Override
    public String toString() {
        return "file\"" + path + "\"";
    }
}
