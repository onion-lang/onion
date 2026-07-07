package onion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

public class IO {
    private static final BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));

    public static void print(Object o) {
        System.out.print(o);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    public static String readLine() {
        try {
            return STDIN.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readln() {
        return readLine();
    }

    public static String readln(String prompt) {
        return input(prompt);
    }

    public static String input(String prompt) {
        System.out.print(prompt);
        try {
            return STDIN.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readAll() throws IOException{
        return new String(
            System.in.readAllBytes(),
            System.getProperty("file.encoding")
        );
    }

    // Formatted output
    public static void printf(String format, Object... args) {
        System.out.printf(format, args);
    }

    public static String format(String format, Object... args) {
        return String.format(format, args);
    }

    // Error output (stderr)
    public static void eprint(Object o) {
        System.err.print(o);
    }

    public static void eprintln(Object o) {
        System.err.println(o);
    }

    public static void eprintf(String format, Object... args) {
        System.err.printf(format, args);
    }

    // Type-safe input methods
    public static int readInt() {
        try {
            String line = STDIN.readLine();
            if (line == null) throw new UncheckedIOException(new IOException("End of input"));
            return Integer.parseInt(line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            throw e;
        }
    }

    public static int readInt(String prompt) {
        System.out.print(prompt);
        return readInt();
    }

    public static long readLong() {
        try {
            String line = STDIN.readLine();
            if (line == null) throw new UncheckedIOException(new IOException("End of input"));
            return Long.parseLong(line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            throw e;
        }
    }

    public static long readLong(String prompt) {
        System.out.print(prompt);
        return readLong();
    }

    public static double readDouble() {
        try {
            String line = STDIN.readLine();
            if (line == null) throw new UncheckedIOException(new IOException("End of input"));
            return Double.parseDouble(line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            throw e;
        }
    }

    public static double readDouble(String prompt) {
        System.out.print(prompt);
        return readDouble();
    }

    public static boolean readBoolean() {
        try {
            String line = STDIN.readLine();
            if (line == null) throw new UncheckedIOException(new IOException("End of input"));
            String trimmed = line.trim().toLowerCase();
            if (trimmed.equals("true") || trimmed.equals("yes") || trimmed.equals("1")) {
                return true;
            } else if (trimmed.equals("false") || trimmed.equals("no") || trimmed.equals("0")) {
                return false;
            } else {
                throw new IllegalArgumentException("Invalid boolean value: " + line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean readBoolean(String prompt) {
        System.out.print(prompt);
        return readBoolean();
    }

    // Safe input methods (return null on error, no exceptions)
    public static Integer tryReadInt(String prompt) {
        System.out.print(prompt);
        try {
            String line = STDIN.readLine();
            if (line == null) return null;
            return Integer.parseInt(line.trim());
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    public static Double tryReadDouble(String prompt) {
        System.out.print(prompt);
        try {
            String line = STDIN.readLine();
            if (line == null) return null;
            return Double.parseDouble(line.trim());
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    public static Long tryReadLong(String prompt) {
        System.out.print(prompt);
        try {
            String line = STDIN.readLine();
            if (line == null) return null;
            return Long.parseLong(line.trim());
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    // Line-oriented input
    /** Reads all remaining lines from standard input into a list. */
    public static java.util.List<String> readLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            String line;
            while ((line = STDIN.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lines;
    }

    /** Applies {@code action} to each remaining line of standard input, in order. */
    public static void eachLine(Function1<String, ?> action) {
        try {
            String line;
            while ((line = STDIN.readLine()) != null) {
                action.call(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Line-oriented output
    /** Prints each element of {@code items} on its own line. */
    public static void printLines(Iterable<?> items) {
        if (items == null) return;
        for (Object item : items) {
            System.out.println(item);
        }
    }

    /** Prints each argument on its own line. */
    public static void printAll(Object... items) {
        if (items == null) return;
        for (Object item : items) {
            System.out.println(item);
        }
    }

    /** Flushes standard output. */
    public static void flush() {
        System.out.flush();
    }

    // Utility methods
    public static void newline() {
        System.out.println();
    }

    public static void clear() {
        // ANSI escape code to clear screen and move cursor to top-left
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
