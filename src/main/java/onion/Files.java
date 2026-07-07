package onion;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * File I/O utilities for Onion programs.
 * All methods are static and can be imported via: import static onion.Files::*
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append(System.lineSeparator());
                sb.append(line);
            }
            return sb.toString();
        }
    }

    public static String[] readLines(String path) throws IOException {
        return readLines(new File(path));
    }

    public static String[] readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines.toArray(new String[0]);
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

    public static void writeLines(String path, String[] lines) throws IOException {
        writeLines(new File(path), lines);
    }

    public static void writeLines(File file, String[] lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public static void writeLines(String path, java.util.List<String> lines) throws IOException {
        writeLines(new File(path), lines);
    }

    public static void writeLines(File file, java.util.List<String> lines) throws IOException {
        writeLines(file, lines.toArray(new String[0]));
    }

    public static void appendText(String path, String content) throws IOException {
        appendText(new File(path), content);
    }

    public static void appendText(File file, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(file, true))) {
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

    public static File[] listFiles(String path) {
        return new File(path).listFiles();
    }

    public static long size(String path) {
        return new File(path).length();
    }

    public static long size(File file) {
        return file == null ? 0 : file.length();
    }

    // Path operations
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
     */
    public static void copyDir(String src, String dst) throws IOException {
        final java.nio.file.Path srcPath = java.nio.file.Paths.get(src);
        final java.nio.file.Path dstPath = java.nio.file.Paths.get(dst);
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
     * Glob-matches files under a directory. The pattern uses standard glob
     * syntax: * (within a segment), ** (across directories), ? and ranges.
     *
     * Usage:
     *   foreach f: String in Files::glob(".", "*.on") { ... }
     *   Files::glob("src", "**&#47;*.java")
     *
     * Returns relative paths (using /), sorted.
     */
    public static java.util.List<String> glob(String dir, String pattern) throws IOException {
        java.nio.file.Path base = java.nio.file.Paths.get(dir);
        java.nio.file.PathMatcher matcher =
            base.getFileSystem().getPathMatcher("glob:" + pattern);
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (!java.nio.file.Files.isDirectory(base)) return result;
        // walkFileTree with a tolerant visitor: unreadable entries (other
        // users' files under /tmp etc.) are skipped instead of aborting
        java.nio.file.Files.walkFileTree(base, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            private void check(java.nio.file.Path p) {
                java.nio.file.Path rel = base.relativize(p);
                if (!rel.toString().isEmpty() && matcher.matches(rel)) {
                    result.add(rel.toString().replace(File.separatorChar, '/'));
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
