package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line argument parsing for scripts.
 *
 * Usage:
 *   val opts = Args::parse(args)
 *   if opts.flag("verbose") { ... }            // --verbose / -v style
 *   val out = opts.option("output", "a.txt")   // --output=x / --output x
 *   val files = opts.positional()              // everything else
 *
 * Recognized forms:
 *   --name           boolean flag
 *   --name=value     option with value
 *   --name value     option with value (when the next token is not an option)
 *   -abc             short flags a, b, c
 *   --               everything after is positional
 */
public final class Args {
    private Args() {}

    public static Parsed parse(String[] argv) {
        Map<String, String> options = new LinkedHashMap<>();
        List<String> flags = new ArrayList<>();
        List<String> positional = new ArrayList<>();
        boolean afterSeparator = false;

        int i = 0;
        while (argv != null && i < argv.length) {
            String arg = argv[i];
            if (afterSeparator) {
                positional.add(arg);
            } else if (arg.equals("--")) {
                afterSeparator = true;
            } else if (arg.startsWith("--")) {
                String body = arg.substring(2);
                int eq = body.indexOf('=');
                if (eq >= 0) {
                    options.put(body.substring(0, eq), body.substring(eq + 1));
                } else if (i + 1 < argv.length && !argv[i + 1].startsWith("-")) {
                    options.put(body, argv[i + 1]);
                    i++;
                } else {
                    flags.add(body);
                }
            } else if (arg.startsWith("-") && arg.length() > 1) {
                for (int c = 1; c < arg.length(); c++) {
                    flags.add(String.valueOf(arg.charAt(c)));
                }
            } else {
                positional.add(arg);
            }
            i++;
        }
        return new Parsed(options, flags, positional);
    }

    public static final class Parsed {
        private final Map<String, String> options;
        private final List<String> flags;
        private final List<String> positional;

        Parsed(Map<String, String> options, List<String> flags, List<String> positional) {
            this.options = options;
            this.flags = flags;
            this.positional = positional;
        }

        /** True when --name or the short flag was given (also true when --name=value was given). */
        public boolean flag(String name) {
            return flags.contains(name) || options.containsKey(name);
        }

        /** The value of --name=v / --name v, or null. */
        public String option(String name) {
            return options.get(name);
        }

        /** The value of --name, or the default when absent. */
        public String option(String name, String defaultValue) {
            String v = options.get(name);
            return v != null ? v : defaultValue;
        }

        /** Integer-valued option with a default. */
        public int intOption(String name, int defaultValue) {
            String v = options.get(name);
            if (v == null) return defaultValue;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        /** Arguments that are not flags or option values, in order. */
        public List<String> positional() {
            return positional;
        }

        @Override
        public String toString() {
            return "Args(options=" + options + ", flags=" + flags + ", positional=" + positional + ")";
        }
    }
}
