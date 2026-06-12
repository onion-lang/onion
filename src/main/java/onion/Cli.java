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
            if (a.startsWith("--")) {
                String name = a.substring(2);
                int idx = -1;
                for (int j = 0; j < names.length; j++) {
                    if (names[j].equals(name) && kinds[j] != 'p') {
                        idx = j;
                        break;
                    }
                }
                if (idx < 0) die(names, kinds, "unknown option: " + a);
                if (kinds[idx] == 's') {
                    result[idx] = "true";
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

    private static void die(String[] names, char[] kinds, String message) {
        StringBuilder usage = new StringBuilder("usage: <script>");
        for (int j = 0; j < names.length; j++) {
            switch (kinds[j]) {
                case 'p': usage.append(" <").append(names[j]).append('>'); break;
                case 'v': usage.append(" [--").append(names[j]).append(" VALUE]"); break;
                default:  usage.append(" [--").append(names[j]).append(']'); break;
            }
        }
        System.err.println("error: " + message);
        System.err.println(usage);
        System.exit(1);
    }
}
