package onion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Zip and gzip.
 *
 * Onion could read and write files but not archives, which is most of what a
 * release, a backup or a log rotation actually touches.
 *
 * Usage:
 *   Archive::zip("out.zip", ["a.txt", "b.txt"])
 *   Archive::zipDir("site.zip", "site")
 *   val names = Archive::entries("out.zip")
 *   val written = Archive::unzip("out.zip", "extracted")
 *
 *   Archive::gzipFile("big.log", "big.log.gz")
 *   val text = new String(Archive::gunzip(Files::readBytes("big.log.gz")))
 *
 * Tar is not here: it needs a dependency, and zip plus gzip covers what the JDK can do
 * on its own.
 */
public final class Archive {
    private Archive() {} // Prevent instantiation

    private static final int BUFFER = 8192;

    // ========== Zip ==========

    /**
     * Writes each path into a new zip, named by its file name alone.
     *
     * @param files a List of path strings — Onion's stdlib takes Lists, not arrays.
     */
    public static void zip(String zipPath, List files) {
        if (files == null) throw new IllegalArgumentException("Archive: files must not be null");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Paths.get(zipPath)))) {
            for (Object entry : files) {
                Path source = Paths.get(String.valueOf(entry));
                if (!Files.isRegularFile(source)) {
                    throw new IllegalArgumentException(
                        "Archive: not a file: " + source);
                }
                writeEntry(out, source.getFileName().toString(), source);
            }
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not write " + zipPath
                + " (" + e.getMessage() + ")", e);
        }
    }

    /** Writes a directory tree into a new zip, keeping paths relative to that directory. */
    public static void zipDir(String zipPath, String directory) {
        Path root = Paths.get(directory);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Archive: not a directory: " + directory);
        }
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Paths.get(zipPath)))) {
            List<Path> sources = new ArrayList<>();
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).sorted().forEach(sources::add);
            }
            for (Path source : sources) {
                // Always '/' — a zip written on Windows with backslashes is unreadable
                // as a directory tree everywhere else.
                String name = root.relativize(source).toString().replace('\\', '/');
                writeEntry(out, name, source);
            }
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not write " + zipPath
                + " (" + e.getMessage() + ")", e);
        }
    }

    /** The entry names in a zip, sorted, without extracting anything. */
    public static List entries(String zipPath) {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(Paths.get(zipPath).toFile())) {
            zip.stream().filter(entry -> !entry.isDirectory())
                .map(ZipEntry::getName).sorted().forEach(names::add);
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not read " + zipPath
                + " (" + e.getMessage() + ")", e);
        }
        return names;
    }

    /**
     * Extracts a zip into a directory and returns the paths written, sorted.
     *
     * <p>An entry whose name would land outside {@code targetDir} is refused. That is not
     * hypothetical caution: a zip entry named {@code ../../.ssh/authorized_keys} is the
     * standard "zip slip" attack, and an extractor that resolves entry names naively
     * writes exactly where it is told to.
     */
    public static List unzip(String zipPath, String targetDir) {
        Path target = Paths.get(targetDir).toAbsolutePath().normalize();
        List<String> written = new ArrayList<>();
        try {
            Files.createDirectories(target);
            try (ZipFile zip = new ZipFile(Paths.get(zipPath).toFile())) {
                List<? extends ZipEntry> ordered = zip.stream().sorted(
                    java.util.Comparator.comparing(ZipEntry::getName)).toList();
                for (ZipEntry entry : ordered) {
                    Path destination = target.resolve(entry.getName()).normalize();
                    if (!destination.startsWith(target)) {
                        throw new SecurityException(
                            "Archive: refusing to extract outside " + target
                                + ": entry " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                    written.add(destination.toString());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not extract " + zipPath
                + " (" + e.getMessage() + ")", e);
        }
        java.util.Collections.sort(written);
        return written;
    }

    // ========== Gzip ==========

    /** Compresses bytes. A byte array on both sides: this is the Java boundary. */
    public static byte[] gzip(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Archive: data must not be null");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not gzip (" + e.getMessage() + ")", e);
        }
        return bytes.toByteArray();
    }

    /** Decompresses bytes produced by {@link #gzip}. */
    public static byte[] gunzip(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Archive: data must not be null");
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not gunzip (" + e.getMessage() + ")", e);
        }
    }

    /** Compresses a file, streaming rather than reading it all into memory. */
    public static void gzipFile(String source, String target) {
        try (InputStream in = Files.newInputStream(Paths.get(source));
             GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(Paths.get(target)))) {
            transfer(in, out);
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not gzip " + source
                + " (" + e.getMessage() + ")", e);
        }
    }

    /** Decompresses a file, streaming. */
    public static void gunzipFile(String source, String target) {
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(Paths.get(source)));
             OutputStream out = Files.newOutputStream(Paths.get(target))) {
            transfer(in, out);
        } catch (IOException e) {
            throw new RuntimeException("Archive: could not gunzip " + source
                + " (" + e.getMessage() + ")", e);
        }
    }

    // ========== Internals ==========

    private static void writeEntry(ZipOutputStream out, String name, Path source)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // A fixed timestamp, so zipping the same inputs twice produces the same bytes.
        // A build artefact that differs run to run cannot be checksummed or cached.
        entry.setTime(0L);
        out.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(source)) {
            transfer(in, out);
        }
        out.closeEntry();
    }

    private static void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
