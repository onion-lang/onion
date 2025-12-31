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
}
