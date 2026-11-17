package onion;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * File I/O utilities for Onion programs.
 * All methods are static and can be imported via: import static onion.Files::*
 *
 * <p>Text is read and written as UTF-8 on every JDK, whatever the platform charset: JDK 18+
 * made UTF-8 the default (JEP 400), and on JDK 17 a Windows machine would otherwise use its
 * ANSI code page (MS932 under a Japanese locale). The {@code Charset} overloads of
 * {@code readText}/{@code writeText} choose another encoding explicitly.
 */
public final class Files {
    private Files() {} // Prevent instantiation

    // Reading
    public static String readText(String path) throws IOException {
        return readText(path, StandardCharsets.UTF_8);
    }

    public static String readText(String path, Charset charset) throws IOException {
        return readText(new File(path), charset);
    }

    public static String readText(File file) throws IOException {
        return readText(file, StandardCharsets.UTF_8);
    }

    public static String readText(File file, Charset charset) throws IOException {
        return stripBom(new String(java.nio.file.Files.readAllBytes(file.toPath()), charset));
    }

    /**
     * Drops a leading U+FEFF: a byte order mark (Windows Notepad's "UTF-8 with BOM") is an
     * encoding marker, not text, so the first key or line must not carry it. Only a leading
     * one is dropped; {@link Csv} does the same.
     */
    static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }

    public static List<String> readLines(String path) throws IOException {
        return readLines(new File(path));
    }

    public static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(lines.isEmpty() ? stripBom(line) : line);
            }
        }
        return lines;
    }

    public static byte[] readBytes(String path) throws IOException {
        return readBytes(new File(path));
    }

    public static byte[] readBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    // Writing
    public static void writeText(String path, String content) throws IOException {
        writeText(path, content, StandardCharsets.UTF_8);
    }

    public static void writeText(String path, String content, Charset charset) throws IOException {
        writeText(new File(path), content, charset);
    }

    public static void writeText(File file, String content) throws IOException {
        writeText(file, content, StandardCharsets.UTF_8);
    }

    public static void writeText(File file, String content, Charset charset) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), charset))) {
            writer.write(content);
        }
    }

    public static void writeLines(String path, java.util.List<String> lines) throws IOException {
        writeLines(new File(path), lines);
    }

    public static void writeLines(File file, java.util.List<String> lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public static void appendText(String path, String content) throws IOException {
        appendText(new File(path), content);
    }

    public static void appendText(File file, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    public static void writeBytes(String path, byte[] data) throws IOException {
        writeBytes(new File(path), data);
    }

    public static void writeBytes(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    // File operations
    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    public static boolean isFile(String path) {
        return new File(path).isFile();
    }

    public static boolean isDirectory(String path) {
        return new File(path).isDirectory();
    }

    public static boolean delete(String path) {
        return new File(path).delete();
    }

    public static boolean delete(File file) {
        return file != null && file.delete();
    }

    public static boolean mkdirs(String path) {
        return new File(path).mkdirs();
    }

    public static List<File> listFiles(String path) {
        File[] entries = new File(path).listFiles();
        if (entries == null) return new ArrayList<File>();
        return new ArrayList<File>(Arrays.asList(entries));
    }

    public static long size(String path) {
        return new File(path).length();
    }

    public static long size(File file) {
        return file == null ? 0 : file.length();
    }

    // Path operations

    /** Joins path segments given as a list. */
    public static String joinPath(java.util.List<String> parts) {
        if (parts == null || parts.isEmpty()) return "";
        return joinPath(parts.toArray(new String[0]));
    }

    public static String joinPath(String... parts) {
        if (parts.length == 0) return "";
        Path path = Paths.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            path = path.resolve(parts[i]);
        }
        return path.toString();
    }

    public static String getFileName(String path) {
        return new File(path).getName();
    }

    public static String getParent(String path) {
        String parent = new File(path).getParent();
        return parent == null ? "" : parent;
    }

    public static String getAbsolutePath(String path) {
        return new File(path).getAbsolutePath();
    }

    // ========== Copy / Move ==========

    /**
     * Copies {@code src} to {@code dst}, replacing {@code dst} if it already exists.
     */
    public static void copy(String src, String dst) throws IOException {
        java.nio.file.Files.copy(
            java.nio.file.Paths.get(src),
            java.nio.file.Paths.get(dst),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
    }

    /**
     * Moves (renames) {@code src} to {@code dst}, replacing {@code dst} if it already exists.
     */
    public static void move(String src, String dst) throws IOException {
        java.nio.file.Files.move(
            java.nio.file.Paths.get(src),
            java.nio.file.Paths.get(dst),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
    }

    /**
     * Recursively copies the directory tree rooted at {@code src} into {@code dst}.
     * {@code dst} is created if it does not exist; existing files are replaced.
     *
     * @throws IllegalArgumentException if {@code dst} is {@code src} or lies inside it;
     *         nothing is written in that case
     */
    public static void copyDir(String src, String dst) throws IOException {
        final java.nio.file.Path srcPath = java.nio.file.Paths.get(src);
        final java.nio.file.Path dstPath = java.nio.file.Paths.get(dst);
        final java.nio.file.Path srcAbs = realPathOfExistingPrefix(srcPath);
        final java.nio.file.Path dstAbs = realPathOfExistingPrefix(dstPath);
        if (dstAbs.equals(srcAbs) || dstAbs.startsWith(srcAbs)) {
            throw new IllegalArgumentException("cannot copy a directory into itself: " + src + " -> " + dst);
        }
        java.nio.file.Files.walkFileTree(srcPath, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.createDirectories(dstPath.resolve(srcPath.relativize(dir)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(
                    java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.copy(
                    file,
                    dstPath.resolve(srcPath.relativize(file)),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The absolute path with symlinks resolved as far as the path exists; the part that does
     * not exist yet (a destination about to be created) is appended unchanged.
     * A {@code ..} is not folded away first: the OS applies it after following a symlink, so
     * {@code link/..} is the link target's parent, not the directory holding {@code link}.
     */
    private static java.nio.file.Path realPathOfExistingPrefix(java.nio.file.Path path) throws IOException {
        java.nio.file.Path abs = path.toAbsolutePath();
        java.nio.file.Path existing = abs;
        while (existing != null && !java.nio.file.Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) return abs;
        return existing.toRealPath().resolve(existing.relativize(abs)).normalize();
    }

    // ========== Directory listing and glob ==========

    /**
     * Lists the entries of a directory (names only, sorted).
     */
    public static java.util.List<String> list(String dir) {
        String[] names = new File(dir).list();
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (names != null) {
            java.util.Arrays.sort(names);
            for (String n : names) result.add(n);
        }
        return result;
    }

    /**
     * Every regular file under a directory, recursively, as paths relative to it
     * (using /), sorted -- like {@code find dir -type f}. Directories themselves are
     * not listed (unlike {@code glob(dir, "**")}), unreadable entries are skipped,
     * and a path that is not a directory gives an empty list.
     *
     * Usage:
     *   foreach f in Files::walk("src") { ... }
     */
    public static java.util.List<String> walk(String dir) throws IOException {
        java.nio.file.Path base = java.nio.file.Paths.get(dir);
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (!java.nio.file.Files.isDirectory(base)) return result;
        java.nio.file.Files.walkFileTree(base, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path p, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    result.add(base.relativize(p).toString().replace(File.separatorChar, '/'));
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path p, IOException e) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        java.util.Collections.sort(result);
        return result;
    }

    /** Expands {a,b} alternatives (nested too) into separate patterns; capped at 64. */
    private static java.util.List<String> expandBraces(String p, int depth) {
        java.util.List<String> one = new java.util.ArrayList<>();
        one.add(p);
        if (depth > 8) return one;
        int open = -1;
        int level = 0;
        java.util.List<Integer> commas = new java.util.ArrayList<>();
        boolean inClass = false; // inside [...]: braces and commas are literal
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '\\') { i++; continue; }
            if (inClass) {
                if (c == ']') inClass = false;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == '{') {
                if (level == 0) { open = i; commas.clear(); }
                level++;
            } else if (c == ',' && level == 1) {
                commas.add(i);
            } else if (c == '}' && level > 0) {
                level--;
                if (level == 0) {
                    String pre = p.substring(0, open);
                    String post = p.substring(i + 1);
                    java.util.List<String> out = new java.util.ArrayList<>();
                    int from = open + 1;
                    commas.add(i);
                    for (int cut : commas) {
                        String alt = p.substring(from, cut);
                        from = cut + 1;
                        for (String e : expandBraces(pre + alt + post, depth + 1)) {
                            out.add(e);
                            if (out.size() > 64) return one;
                        }
                    }
                    return out;
                }
            }
        }
        return one;
    }

    /**
     * Glob-matches files under a directory. The pattern uses standard glob
     * syntax: * (within a segment), ** (across directories), ? and ranges.
     *
     * Usage:
     *   foreach f: String in Files::glob(".", "*.on") { ... }
     *   Files::glob("src", "**&#47;*.java")
     *
     * A {@code **&#47;} segment also matches zero directories, so
     * {@code **&#47;*.java} finds files directly in {@code dir} too, also inside
     * a brace alternative ({@code {**&#47;*.on,*.md}}). A leading {@code ./} is ignored.
     *
     * Returns relative paths (using /), sorted.
     */
    public static java.util.List<String> glob(String dir, String pattern) throws IOException {
        java.nio.file.Path base = java.nio.file.Paths.get(dir);
        if (pattern.startsWith("./")) pattern = pattern.substring(2);
        java.util.ArrayList<java.nio.file.PathMatcher> matchers = new java.util.ArrayList<>();
        // The JDK rejects nested brace groups; the expansion below handles
        // them, so a failed compile only matters if nothing else compiles.
        java.util.regex.PatternSyntaxException original = null;
        try {
            matchers.add(base.getFileSystem().getPathMatcher("glob:" + pattern));
        } catch (java.util.regex.PatternSyntaxException e) {
            original = e;
        }
        // A "**/" segment also matches zero directories (as bash globstar and
        // Python glob do); Java's matcher needs at least one. Brace
        // alternatives are expanded first, so each one gets the same rule.
        java.util.LinkedHashSet<String> patterns = new java.util.LinkedHashSet<>();
        for (String alt : expandBraces(pattern, 0)) {
            java.util.List<java.util.List<String>> variants = new java.util.ArrayList<>();
            variants.add(new java.util.ArrayList<>());
            String[] segs = alt.split("/", -1);
            for (int i = 0; i < segs.length; i++) {
                java.util.List<java.util.List<String>> next = new java.util.ArrayList<>();
                boolean skippable = segs[i].equals("**") && i < segs.length - 1;
                for (java.util.List<String> v : variants) {
                    java.util.List<String> kept = new java.util.ArrayList<>(v);
                    kept.add(segs[i]);
                    next.add(kept);
                    if (skippable) next.add(v);
                }
                if (next.size() <= 64) variants = next;
            }
            for (java.util.List<String> v : variants) patterns.add(String.join("/", v));
        }
        for (String joined : patterns) {
            if (!joined.isEmpty() && !joined.equals(pattern)) {
                try {
                    matchers.add(base.getFileSystem().getPathMatcher("glob:" + joined));
                } catch (java.util.regex.PatternSyntaxException e) {
                    // an expanded alternative that does not compile adds nothing;
                    // the original pattern above is always kept
                }
            }
        }
        if (matchers.isEmpty() && original != null) throw original;
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (!java.nio.file.Files.isDirectory(base)) return result;
        // walkFileTree with a tolerant visitor: unreadable entries (other
        // users' files under /tmp etc.) are skipped instead of aborting
        java.nio.file.Files.walkFileTree(base, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            private void check(java.nio.file.Path p) {
                java.nio.file.Path rel = base.relativize(p);
                if (rel.toString().isEmpty()) return;
                for (java.nio.file.PathMatcher m : matchers) {
                    if (m.matches(rel)) {
                        result.add(rel.toString().replace(File.separatorChar, '/'));
                        return;
                    }
                }
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path p, java.nio.file.attribute.BasicFileAttributes attrs) {
                check(p);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path p, java.nio.file.attribute.BasicFileAttributes attrs) {
                check(p);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path p, IOException exc) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        java.util.Collections.sort(result);
        return result;
    }

    /**
     * The file extension (without the dot), e.g. "txt" for "dir/file.txt", or ""
     * when the name has no extension or starts with a dot (like ".gitignore").
     * Named {@code ext} because {@code extension} is an Onion keyword.
     */
    public static String ext(String path) {
        if (path == null) return "";
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1);
    }

    /**
     * The file name without its directory or extension, e.g. "file" for
     * "dir/file.txt". A leading-dot name like ".gitignore" is returned as-is.
     */
    public static String stem(String path) {
        if (path == null) return "";
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /**
     * Replaces (or adds) the file extension: ("dir/file.txt", "md") ->
     * "dir/file.md". A leading dot in {@code newExtension} is ignored.
     */
    public static String withExtension(String path, String newExtension) {
        if (path == null) return "";
        String ext = newExtension == null ? "" : newExtension;
        if (ext.startsWith(".")) ext = ext.substring(1);
        File file = new File(path);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String newName = ext.isEmpty() ? base : base + "." + ext;
        String parent = file.getParent();
        return parent == null ? newName : parent + File.separator + newName;
    }
}
