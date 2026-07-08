package onion;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime support for Onion's auto-CLI: when a script defines a top-level
 * {@code def main(path: String, top: Int = 10, verbose: Boolean = false)},
 * the compiler synthesizes a call to {@link #parse} and then invokes main
 * with the converted values — argument parsing, type conversion and usage
 * output are all derived from the signature.
 *
 * Spec string format (comma-separated, one entry per parameter):
 *   "name"   required positional argument
 *   "name="  optional value flag (--name VALUE)
 *   "name?"  optional boolean switch (--name)
 *
 * Returns raw string values aligned with the specs: null where an optional
 * entry is absent, "true" for a present switch. On any error prints a usage
 * line to stderr and exits with status 1.
 */
public final class Cli {
    private Cli() {
    }

    public static String[] parse(String[] args, String specString) {
        String[] specs = specString.isEmpty() ? new String[0] : specString.split(",");
        String[] names = new String[specs.length];
        char[] kinds = new char[specs.length]; // 'p' positional, 'v' value flag, 's' switch
        for (int i = 0; i < specs.length; i++) {
            String s = specs[i];
            if (s.endsWith("=")) {
                names[i] = s.substring(0, s.length() - 1);
                kinds[i] = 'v';
            } else if (s.endsWith("?")) {
                names[i] = s.substring(0, s.length() - 1);
                kinds[i] = 's';
            } else {
                names[i] = s;
                kinds[i] = 'p';
            }
        }
        String[] result = new String[specs.length];
        List<String> positionals = new ArrayList<>();
        int i = 0;
        int argCount = args == null ? 0 : args.length;
        while (i < argCount) {
            String a = args[i];
            if (a.equals("--help") || a.equals("-h")) {
                System.out.println(usageString(names, kinds));
                System.exit(0);
            }
            if (a.startsWith("--")) {
                // Accept the GNU `--name=value` form in addition to `--name value`.
                String name = a.substring(2);
                String inlineValue = null;
                int eq = name.indexOf('=');
                if (eq >= 0) {
                    inlineValue = name.substring(eq + 1);
                    name = name.substring(0, eq);
                }
                int idx = -1;
                for (int j = 0; j < names.length; j++) {
                    if (names[j].equals(name) && kinds[j] != 'p') {
                        idx = j;
                        break;
                    }
                }
                if (idx < 0) die(names, kinds, "unknown option: " + a);
                if (kinds[idx] == 's') {
                    // A switch is true when present; `--flag=false` may set it explicitly.
                    result[idx] = inlineValue != null ? inlineValue : "true";
                    i++;
                } else if (inlineValue != null) {
                    result[idx] = inlineValue;
                    i++;
                } else {
                    if (i + 1 >= argCount) die(names, kinds, "missing value for " + a);
                    result[idx] = args[i + 1];
                    i += 2;
                }
            } else {
                positionals.add(a);
                i++;
            }
        }
        int p = 0;
        for (int j = 0; j < names.length; j++) {
            if (kinds[j] == 'p') {
                if (p >= positionals.size()) die(names, kinds, "missing argument: " + names[j]);
                result[j] = positionals.get(p);
                p++;
            }
        }
        if (p < positionals.size()) die(names, kinds, "unexpected argument: " + positionals.get(p));
        return result;
    }

    // ---- Type-safe parse helpers used by auto-CLI generated code ----

    public static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            die("invalid value for --" + name + ": '" + value + "' (expected Int)");
            return 0; // unreachable
        }
    }

    public static long parseLong(String name, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            die("invalid value for --" + name + ": '" + value + "' (expected Long)");
            return 0L; // unreachable
        }
    }

    public static double parseDouble(String name, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            die("invalid value for --" + name + ": '" + value + "' (expected Double)");
            return 0.0; // unreachable
        }
    }

    public static float parseFloat(String name, String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            die("invalid value for --" + name + ": '" + value + "' (expected Float)");
            return 0.0f; // unreachable
        }
    }

    public static short parseShort(String name, String value) {
        try {
            return Short.parseShort(value);
        } catch (NumberFormatException e) {
            die("invalid value for --" + name + ": '" + value + "' (expected Short)");
            return 0; // unreachable
        }
    }

    public static byte parseByte(String name, String value) {
        try {
            return Byte.parseByte(value);
        } catch (NumberFormatException e) {
            die("invalid value for --" + name + ": '" + value + "' (expected Byte)");
            return 0; // unreachable
        }
    }

    public static boolean parseBoolean(String name, String value) {
        return Boolean.parseBoolean(value);
    }

    /**
     * The trailing positional arguments from index {@code from} onward, as the
     * rest for a `main(..., xs: String[])` parameter. Empty when there are none.
     */
    public static String[] rest(String[] args, int from) {
        if (args == null || from >= args.length) return new String[0];
        return java.util.Arrays.copyOfRange(args, from, args.length);
    }

    /**
     * Fail with a usage message when fewer than {@code required} arguments were
     * given, for a main with required leading parameters plus a String[] rest.
     */
    /** The running script's file name for usage messages, or a generic placeholder. */
    private static String scriptName() {
        return System.getProperty("onion.cli.script", "<script>");
    }

    public static void requireArgs(String[] args, int required, String usage) {
        int n = args == null ? 0 : args.length;
        if (n < required) {
            System.err.println("error: expected at least " + required + " argument(s)");
            System.err.println("usage: " + scriptName() + " " + usage);
            System.exit(1);
        }
    }

    private static void die(String message) {
        System.err.println("error: " + message);
        System.exit(1);
    }

    private static String usageString(String[] names, char[] kinds) {
        StringBuilder usage = new StringBuilder("usage: " + scriptName());
        for (int j = 0; j < names.length; j++) {
            switch (kinds[j]) {
                case 'p': usage.append(" <").append(names[j]).append('>'); break;
                case 'v': usage.append(" [--").append(names[j]).append(" VALUE]"); break;
                default:  usage.append(" [--").append(names[j]).append(']'); break;
            }
        }
        usage.append(" [--help]");
        return usage.toString();
    }

    private static void die(String[] names, char[] kinds, String message) {
        System.err.println("error: " + message);
        System.err.println(usageString(names, kinds));
        System.exit(1);
    }
}
