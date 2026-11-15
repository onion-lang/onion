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
 *   if opts.flag("verbose") { ... }            // --verbose
 *   val out = opts.option("output", "a.txt")   // --output=x / --output x
 *   val files = opts.positional()              // everything else
 *
 * Recognized forms:
 *   --name           boolean flag
 *   --name=value     option with value
 *   --name value     option with value (when the next token is not an option;
 *                    a negative number like -5 or -3.14 counts as a value,
 *                    not an option, so --count -5 works). The parser cannot tell
 *                    this from a flag followed by a positional, so it decides when
 *                    the script asks: if the script calls flag("name") and never
 *                    option("name"), the value goes back to positional(), in
 *                    its original place. Ask flag/option before positional().
 *   -abc             short flags a, b, c (each registered under its own
 *                    single-character name, e.g. flag("a"); NOT an alias for
 *                    any same-named long flag, e.g. -v does not satisfy
 *                    flag("verbose"))
 *   --               everything after is positional
 */
public final class Args {
    private Args() {}

    public static Parsed parse(String[] argv) {
        Map<String, String> options = new LinkedHashMap<>();
        List<String> flags = new ArrayList<>();
        List<String> positional = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();
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
                } else if (i + 1 < argv.length && isValue(argv[i + 1])) {
                    options.put(body, argv[i + 1]);
                    pending.add(new Pending(body, argv[i + 1], positional.size()));
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
        return new Parsed(options, flags, positional, pending);
    }

    /**
     * Whether {@code token} can fill a {@code --name} value slot: anything that is not
     * itself an option, plus a negative number ({@code -5}, {@code -3.14}), which would
     * otherwise be mistaken for a new flag/short-flag cluster.
     */
    private static boolean isValue(String token) {
        return !token.startsWith("-")
            || (token.length() > 1 && Character.isDigit(token.charAt(1)));
    }

    /** A {@code --name value} pair that may really be a flag followed by a positional. */
    private record Pending(String name, String value, int at) {}

    public static final class Parsed {
        private final Map<String, String> options;
        private final List<String> flags;
        private final List<String> positional;
        private final List<Pending> pending;
        private final java.util.Set<String> askedFlag = new java.util.HashSet<>();
        private final java.util.Set<String> askedOption = new java.util.HashSet<>();

        Parsed(Map<String, String> options, List<String> flags, List<String> positional,
               List<Pending> pending) {
            this.options = options;
            this.flags = flags;
            this.positional = positional;
            this.pending = pending;
        }

        /** True when --name or the short flag was given (also true when --name=value was given). */
        public boolean flag(String name) {
            askedFlag.add(name);
            return flags.contains(name) || options.containsKey(name);
        }

        /** The value of --name=v / --name v, or null. */
        public String option(String name) {
            askedOption.add(name);
            return options.get(name);
        }

        /** The value of --name, or the default when absent. */
        public String option(String name, String defaultValue) {
            askedOption.add(name);
            String v = options.get(name);
            return v != null ? v : defaultValue;
        }

        /** Integer-valued option with a default. */
        public int intOption(String name, int defaultValue) {
            askedOption.add(name);
            String v = options.get(name);
            if (v == null) return defaultValue;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        /**
         * Arguments that are not flags or option values, in order. A {@code --name value}
         * pair read only through {@code flag("name")} gives its value back here.
         */
        public List<String> positional() {
            List<String> out = new ArrayList<>();
            for (int i = 0; i <= positional.size(); i++) {
                for (Pending p : pending) {
                    if (p.at() == i && askedFlag.contains(p.name()) && !askedOption.contains(p.name())) {
                        out.add(p.value());
                    }
                }
                if (i < positional.size()) out.add(positional.get(i));
            }
            return out;
        }

        @Override
        public String toString() {
            return "Args(options=" + options + ", flags=" + flags + ", positional=" + positional + ")";
        }
    }
}
