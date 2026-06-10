package onion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * External process execution for scripting.
 *
 * Usage:
 *   val out = Proc::run("git", "status", "--short")   // capture stdout
 *   val code = Proc::exec("make", "build")            // inherit IO, exit code
 *   val r = Proc::capture("ls", "missing")            // status + stdout + stderr
 *   if r.failed() { IO::println(r.stderr()) }
 */
public final class Proc {
    private Proc() {} // Prevent instantiation

    /** Full result of a finished process. */
    public static final class Result {
        private final int status;
        private final String stdout;
        private final String stderr;

        Result(int status, String stdout, String stderr) {
            this.status = status;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int status() { return status; }
        public String stdout() { return stdout; }
        public String stderr() { return stderr; }
        public boolean succeeded() { return status == 0; }
        public boolean failed() { return status != 0; }

        @Override
        public String toString() {
            return "Proc.Result(status=" + status + ")";
        }
    }

    // ========== Capture ==========

    /**
     * Runs a command and captures exit status, stdout and stderr.
     * Never throws on non-zero exit; inspect the Result instead.
     */
    public static Result capture(String... command) {
        return captureIn(null, command);
    }

    /** Like capture, but runs in the given working directory. */
    public static Result captureIn(String dir, String... command) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Proc: command must not be empty");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (dir != null) builder.directory(new File(dir));
            Process process = builder.start();
            // Drain stderr on a separate thread so a full pipe buffer on either
            // stream cannot deadlock the child process
            final InputStream errStream = process.getErrorStream();
            final String[] errHolder = {""};
            Thread errReader = new Thread(() -> {
                try {
                    errHolder[0] = readAll(errStream);
                } catch (IOException ignored) {
                }
            });
            errReader.start();
            String out = readAll(process.getInputStream());
            errReader.join();
            int status = process.waitFor();
            return new Result(status, out, errHolder[0]);
        } catch (IOException e) {
            throw new RuntimeException("Proc: failed to run " + Arrays.toString(command) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Proc: interrupted while running " + Arrays.toString(command), e);
        }
    }

    // ========== Convenience ==========

    /**
     * Runs a command and returns its stdout (trailing newline stripped).
     * Throws RuntimeException including stderr when the exit status is non-zero.
     */
    public static String run(String... command) {
        return runIn(null, command);
    }

    /** Like run, but executes in the given working directory. */
    public static String runIn(String dir, String... command) {
        Result result = captureIn(dir, command);
        if (result.failed()) {
            throw new RuntimeException(
                "Proc: " + Arrays.toString(command) + " exited with " + result.status()
                + (result.stderr().isEmpty() ? "" : ": " + result.stderr().strip()));
        }
        return stripTrailingNewline(result.stdout());
    }

    /**
     * Runs a command with stdin/stdout/stderr inherited from this process
     * (output goes straight to the console) and returns the exit status.
     */
    public static int exec(String... command) {
        return execIn(null, command);
    }

    /** Like exec, but executes in the given working directory. */
    public static int execIn(String dir, String... command) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Proc: command must not be empty");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
            if (dir != null) builder.directory(new File(dir));
            return builder.start().waitFor();
        } catch (IOException e) {
            throw new RuntimeException("Proc: failed to run " + Arrays.toString(command) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Proc: interrupted while running " + Arrays.toString(command), e);
        }
    }

    // ========== Helpers ==========

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String stripTrailingNewline(String s) {
        if (s.endsWith("\r\n")) return s.substring(0, s.length() - 2);
        if (s.endsWith("\n")) return s.substring(0, s.length() - 1);
        return s;
    }
}
