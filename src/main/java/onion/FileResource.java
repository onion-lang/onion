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
        String[] arr = Files.readLines(path);
        List<String> list = new ArrayList<>(arr.length);
        for (String s : arr) list.add(s);
        return list;
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
