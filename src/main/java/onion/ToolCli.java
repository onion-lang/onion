package onion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The runtime half of tool CLIs (issue #358).
 *
 * A script whose top level declares tools gets a synthesized {@code main} that hands
 * {@code argv} plus the tools' machine-readable contract (a JSON string built by the
 * compiler from the declarations) to {@link #dispatch}. Everything the CLI does —
 * {@code --contract}, {@code --help} with types and defaults, subcommand selection,
 * flag/positional parsing, typed conversion, error reporting — is derived from that
 * one contract. There is no spec string, no parallel per-type switch, and no
 * {@code System.exit} anywhere on this path: failures return an exit code the caller
 * returns from {@code main}, which is what makes them testable in-process.
 *
 * Contract schema (an array; one entry per tool):
 * <pre>
 * [{"tool": "ingest",
 *   "params": [{"name":"src","type":"String","role":"positional"},
 *              {"name":"count","type":"Int","role":"flag","default":"3"}],
 *   "returns": "Int",
 *   "capabilities": ["read(src)","console"]}]
 * </pre>
 * Roles: {@code positional} (required, in order), {@code flag} ({@code --name value}
 * or {@code --name=value}), {@code switch} ({@code --name}, boolean). A defaulted
 * parameter whose flag is absent yields a {@code null} slot in {@link Result#value};
 * the synthesized call site evaluates the default expression in-language, so defaults
 * stay arbitrary expressions, not strings. A default that is not a literal (e.g.
 * {@code = 1 + 2}) has no value to quote, so the entry carries
 * {@code "defaultComputed":true} instead of a {@code "default"} key; {@code --help}
 * and {@code --plan} describe it as computed rather than fabricating a value.
 *
 * <p>An entry may also carry {@code "staticOperands"}, present only when the compiler
 * could read at least one operand off the source (a literal URL's host, a {@code Proc}
 * command name, a {@code System::getenv} variable name, a literal path):
 * <pre>
 *   "staticOperands": {"net": {"known": ["api.github.com"], "unresolved": false}}
 * </pre>
 * {@code known} is a lower bound; {@code unresolved} is true when some call site of that
 * effect has an operand the analysis could not determine. {@code --plan} prints each
 * known operand on its own line. An {@code exec} entry may also carry
 * {@code "commands": [["gh", "pr", "list", null, ...]]}: each call site's argument
 * vector, {@code null} where an argument is not a literal. {@code --plan} then prints
 * the whole command instead of the bare name: literal arguments verbatim, {@code …} for
 * each run of non-literal ones, cut at {@value #COMMAND_WIDTH} characters.
 */
public final class ToolCli {
    private ToolCli() {}

    /** What dispatch decided: either exit with a code, or run tool #tool with values. */
    public static final class Result {
        private final boolean exit;
        private final int code;
        private final int tool;
        private final Object[] values;

        private Result(boolean exit, int code, int tool, Object[] values) {
            this.exit = exit; this.code = code; this.tool = tool; this.values = values;
        }
        public boolean isExit() { return exit; }
        public int exitCode() { return code; }
        public int tool() { return tool; }
        public Object value(int i) { return values[i]; }

        static Result exit(int code) { return new Result(true, code, -1, null); }
        static Result run(int tool, Object[] values) { return new Result(false, 0, tool, values); }
    }

    @SuppressWarnings("unchecked")
    public static Result dispatch(String[] args, String contractJson) {
        final List<Map<String, Object>> tools;
        try {
            tools = (List<Map<String, Object>>) (List<?>) (List<Object>) Json.parse(contractJson);
        } catch (Exception e) {
            System.err.println("internal error: malformed tool contract: " + e.getMessage());
            return Result.exit(70); // EX_SOFTWARE
        }

        // Everything after a bare `--` is a value, however it is spelled, so a tool can
        // be handed an argument that starts with `--` (issue #437). The mode scan below
        // must stop there too, or `-- --help` would still print help.
        int endOfOptions = args.length;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--")) { endOfOptions = i; break; }
        }

        // The three modes are mutually exclusive. Picking one by the order the checks
        // happen to be written in silently discards the other flag the user passed
        // (issue #439), so collect them and say when more than one arrived.
        boolean wantContract = false, wantHelp = false, plan = false;
        for (int i = 0; i < endOfOptions; i++) {
            String a = args[i];
            if (a.equals("--contract")) wantContract = true;
            else if (a.equals("--help") || a.equals("-h")) wantHelp = true;
            else if (a.equals("--plan")) plan = true;
        }
        int modes = (wantContract ? 1 : 0) + (wantHelp ? 1 : 0) + (plan ? 1 : 0);
        if (modes > 1) {
            List<String> given = new ArrayList<>();
            if (wantHelp) given.add("--help");
            if (wantContract) given.add("--contract");
            if (plan) given.add("--plan");
            System.err.println("only one of " + String.join(", ", given)
                + " can be used at a time.");
            return Result.exit(1);
        }
        if (wantContract) { System.out.println(contractJson); return Result.exit(0); }
        if (wantHelp) { System.out.print(help(tools)); return Result.exit(0); }

        // Strip `--plan` and the `--` marker. `optionCount` is how many of the surviving
        // arguments precede the marker: those may be options, the rest are values.
        int optionCount;
        {
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < endOfOptions; i++) {
                if (!args[i].equals("--plan")) kept.add(args[i]);
            }
            optionCount = kept.size();
            for (int i = endOfOptions + 1; i < args.length; i++) kept.add(args[i]);
            args = kept.toArray(new String[0]);
        }

        int toolIndex = 0;
        String[] rest = args;
        if (tools.size() > 1) {
            if (args.length == 0) { System.err.print(help(tools)); return Result.exit(1); }
            toolIndex = -1;
            for (int i = 0; i < tools.size(); i++) {
                if (name(tools.get(i)).equals(args[0])) { toolIndex = i; break; }
            }
            if (toolIndex < 0) {
                System.err.println("unknown tool `" + args[0] + "`. " + toolNames(tools));
                return Result.exit(1);
            }
            rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            if (optionCount > 0) optionCount--;   // the subcommand was one of them
        }

        Map<String, Object> tool = tools.get(toolIndex);
        List<Map<String, Object>> params = params(tool);
        Object[] values = new Object[params.size()];

        // Index the declared flags/switches; walk argv filling positionals in order.
        Map<String, Integer> named = new LinkedHashMap<>();
        List<Integer> positionals = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            String role = str(params.get(i), "role");
            if (role.equals("positional")) positionals.add(i);
            else {
                // The parameter's own name and its kebab-case spelling both select it
                // (`makeSample` answers to `--make-sample` and `--makeSample`). The
                // compiler rejects two parameters that would share a spelling, so
                // neither put can shadow another parameter.
                String pname = str(params.get(i), "name");
                named.put("--" + pname, i);
                named.put("--" + Cli.kebab(pname), i);
            }
        }

        int nextPositional = 0;
        for (int a = 0; a < rest.length; a++) {
            String arg = rest[a];
            // Past the `--` marker every token is a value, even one spelled like an
            // option — that is the whole point of the marker (issue #437).
            if (a < optionCount && arg.startsWith("--")) {
                String key = arg;
                String inline = null;
                int eq = arg.indexOf('=');
                if (eq >= 0) { key = arg.substring(0, eq); inline = arg.substring(eq + 1); }
                Integer idx = named.get(key);
                if (idx == null) {
                    System.err.println("unknown option `" + key + "` for tool `" + name(tool) + "`. Try --help.");
                    return Result.exit(1);
                }
                Map<String, Object> p = params.get(idx);
                if (values[idx] != null) {
                    System.err.println("option `" + key + "` was given more than once.");
                    return Result.exit(1);
                }
                if (str(p, "role").equals("switch")) {
                    if (inline != null) {
                        System.err.println("switch `" + key + "` does not take a value.");
                        return Result.exit(1);
                    }
                    values[idx] = Boolean.TRUE;
                } else {
                    String raw = inline;
                    if (raw == null) {
                        if (a + 1 >= rest.length) {
                            System.err.println("option `" + key + "` needs a value (" + str(p, "type") + ").");
                            return Result.exit(1);
                        }
                        raw = rest[++a];
                    }
                    Object converted = convert(raw, str(p, "type"));
                    if (converted == CONVERSION_FAILED) {
                        System.err.println("option `" + key + "`: `" + raw + "` is not a valid " + str(p, "type") + ".");
                        return Result.exit(1);
                    }
                    values[idx] = converted;
                }
            } else {
                if (nextPositional >= positionals.size()) {
                    System.err.println("unexpected argument `" + arg + "`. Try --help.");
                    return Result.exit(1);
                }
                int idx = positionals.get(nextPositional++);
                Map<String, Object> p = params.get(idx);
                Object converted = convert(arg, str(p, "type"));
                if (converted == CONVERSION_FAILED) {
                    System.err.println("argument `" + str(p, "name") + "`: `" + arg + "` is not a valid " + str(p, "type") + ".");
                    return Result.exit(1);
                }
                values[idx] = converted;
            }
        }

        if (nextPositional < positionals.size()) {
            StringBuilder sb = new StringBuilder("missing argument");
            List<String> missing = new ArrayList<>();
            for (int i = nextPositional; i < positionals.size(); i++) {
                Map<String, Object> p = params.get(positionals.get(i));
                missing.add("<" + str(p, "name") + ": " + str(p, "type") + ">");
            }
            sb.append(missing.size() > 1 ? "s " : " ").append(String.join(" ", missing));
            System.err.println(sb);
            System.err.print(usage(tool, tools.size() > 1));
            return Result.exit(1);
        }
        if (plan) {
            System.out.print(plan(tool, params, values));
            return Result.exit(0);
        }
        return Result.run(toolIndex, values);
    }

    // ---- the plan: declared capabilities instantiated with bound arguments --------

    private static String plan(Map<String, Object> tool, List<Map<String, Object>> params,
                               Object[] values) {
        StringBuilder sb = new StringBuilder("plan: `" + name(tool) + "` would\n");
        List<Object> caps = list(tool, "capabilities");
        if (caps.isEmpty()) {
            sb.append("  perform no effects (pure)\n");
        }
        // Operands the compiler read off the source (FRICTION F11): literal hosts,
        // commands, env-var names and paths. One line per operand, the effect repeated,
        // printed once per effect. Absent from contracts built before this existed.
        Map<String, Object> staticOps = map(tool, "staticOperands");
        java.util.Set<String> staticDone = new java.util.HashSet<>();
        for (Object capObj : caps) {
            String cap = String.valueOf(capObj);
            String effect = cap;
            String paramName = null;
            int open = cap.indexOf('(');
            if (open >= 0 && cap.endsWith(")")) {
                effect = cap.substring(0, open);
                paramName = cap.substring(open + 1, cap.length() - 1);
            }
            if (effect.equals("unknown")) {
                // A plan that quietly omits what it could not characterize is worse
                // than no plan.
                sb.append("  unknown  — calls code the analysis cannot characterize; ")
                  .append("this plan is a lower bound\n");
            } else if (paramName == null) {
                List<Object> known = list(map(staticOps, effect), "known");
                if (!known.isEmpty()) {
                    // Known operands replace the bare line. A lower bound: when some
                    // call site's operand could not be read, say so on its own line.
                    if (staticDone.add(effect)) {
                        appendStaticOperands(sb, effect, map(staticOps, effect));
                        if (Boolean.TRUE.equals(map(staticOps, effect).get("unresolved")))
                            sb.append("  ").append(pad(effect, 8)).append("(operand not statically known)\n");
                    }
                    continue;
                }
                // Ambient effects have no operand; a parameterizable one declared bare
                // has an operand the analysis could not tie down — say which is which.
                if (effect.equals("console") || effect.equals("env")
                        || effect.equals("clock") || effect.equals("rand")) {
                    sb.append("  ").append(effect).append('\n');
                } else {
                    sb.append("  ").append(pad(effect, 8)).append("(operand not statically known)\n");
                }
            } else {
                int idx = -1;
                for (int i = 0; i < params.size(); i++) {
                    if (str(params.get(i), "name").equals(paramName)) { idx = i; break; }
                }
                String operand;
                if (idx < 0) operand = "(operand not statically known)";
                else {
                    String bound;
                    if (values[idx] != null) bound = String.valueOf(values[idx]);
                    else {
                        Map<String, Object> p = params.get(idx);
                        Object dflt = p.get("default");
                        if (dflt != null) bound = dflt + " (default)";
                        else if (Boolean.TRUE.equals(p.get("defaultComputed")))
                            // A non-literal default (e.g. `= 1 + 2`) has no value the
                            // contract can quote -- saying so is more honest than
                            // fabricating one (issue: this used to print the literal
                            // placeholder text "<computed>" as if it were the argument).
                            bound = "(computed default; exact value determined at call time)";
                        else bound = "(unset)";
                    }
                    // The capability ties the effect to a PARAMETER, not to the path the
                    // body finally passes to the effectful call — a body is free to build
                    // `dst + "." + i` out of it, which is what the shipped demo does. So
                    // `write dst = out.txt` read as a promise that the run touches exactly
                    // that path, which nothing checked (issue #425). Say what is known.
                    operand = "derived from " + paramName + " = " + bound;
                }
                sb.append("  ").append(pad(effect, 8)).append(operand).append('\n');
                // Literal operands of the same effect elsewhere in the body (a fixed log
                // file next to `write(out)`) are added below the parameter line, which
                // itself stays exactly as it was.
                List<Object> known = list(map(staticOps, effect), "known");
                if (!known.isEmpty() && staticDone.add(effect))
                    appendStaticOperands(sb, effect, map(staticOps, effect));
            }
        }
        sb.append("(nothing was executed; operands are the arguments the effects are\n");
        sb.append(" derived from, not necessarily the exact paths or hosts touched)\n");
        return sb.toString();
    }

    private static void appendStaticOperands(StringBuilder sb, String effect,
                                             Map<String, Object> entry) {
        List<Object> lines = list(entry, "known");
        List<Object> commands = list(entry, "commands");
        if (!commands.isEmpty()) {
            java.util.Set<String> rendered = new java.util.LinkedHashSet<>();
            for (Object c : commands) {
                if (c instanceof List) rendered.add(renderCommand((List<?>) c));
            }
            if (!rendered.isEmpty()) lines = new ArrayList<>(rendered);
        }
        for (Object k : lines) sb.append("  ").append(pad(effect, 8)).append(k).append('\n');
    }

    /** How wide a command may get on a plan line before it is cut with {@code …}. */
    static final int COMMAND_WIDTH = 72;

    /**
     * A command as a plan line shows it: every literal argument verbatim, and {@code …} in
     * place of an argument that is not a literal ({@code null}), consecutive holes
     * collapsing into one -- {@code gh … list --repo onion-lang/onion --search …}. Past
     * {@link #COMMAND_WIDTH} characters the line is cut and ends in {@code …}. An argument
     * that is empty or holds whitespace or a quote is shown single-quoted, so the words
     * stay countable and a literal {@code …} argument cannot pass for a hole.
     */
    static String renderCommand(List<?> argv) {
        StringBuilder sb = new StringBuilder();
        boolean lastWasHole = false;
        for (Object a : argv) {
            if (a == null && lastWasHole) continue;
            if (sb.length() > 0) sb.append(' ');
            if (a == null) sb.append('…');
            else sb.append(shellWord(String.valueOf(a)));
            lastWasHole = a == null;
        }
        if (sb.length() > COMMAND_WIDTH) {
            sb.setLength(COMMAND_WIDTH - 1);
            sb.append('…');
        }
        return sb.toString();
    }

    private static String shellWord(String s) {
        boolean plain = !s.isEmpty();
        for (int i = 0; plain && i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '\'' || c == '"' || c == '…') plain = false;
        }
        return plain ? s : "'" + s.replace("'", "'\\''") + "'";
    }

    // ---- help / usage, straight from the contract -------------------------------

    private static String help(List<Map<String, Object>> tools) {
        StringBuilder sb = new StringBuilder();
        boolean sub = tools.size() > 1;
        for (Map<String, Object> tool : tools) {
            sb.append(usage(tool, sub));
            for (Map<String, Object> p : params(tool)) {
                sb.append("  ").append(pad(paramLabel(p), 24)).append(paramDoc(p)).append('\n');
            }
            List<Object> caps = list(tool, "capabilities");
            if (!caps.isEmpty()) {
                List<String> rendered = new ArrayList<>();
                for (Object c : caps) rendered.add(String.valueOf(c));
                sb.append("  requires: ").append(String.join(", ", rendered)).append('\n');
            }
        }
        sb.append("  ").append(pad("--plan", 24)).append("show what a run would do, without doing it\n");
        sb.append("  ").append(pad("--contract", 24)).append("print the machine-readable contract\n");
        sb.append("  ").append(pad("--help, -h", 24)).append("show this help\n");
        return sb.toString();
    }

    private static String usage(Map<String, Object> tool, boolean sub) {
        StringBuilder sb = new StringBuilder("usage: ");
        String script = System.getProperty("onion.cli.script", "<script>");
        sb.append(script);
        if (sub) sb.append(' ').append(name(tool));
        for (Map<String, Object> p : params(tool)) {
            String role = str(p, "role");
            if (role.equals("positional")) sb.append(" <").append(str(p, "name")).append('>');
            else if (role.equals("switch")) sb.append(" [--").append(flagName(p)).append(']');
            else sb.append(" [--").append(flagName(p)).append(" <").append(str(p, "type")).append(">]");
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String paramLabel(Map<String, Object> p) {
        String role = str(p, "role");
        if (role.equals("positional")) return "<" + str(p, "name") + ">";
        if (role.equals("switch")) return "--" + flagName(p);
        return "--" + flagName(p) + " <" + str(p, "type").toLowerCase() + ">";
    }

    /** The spelling `--help` and usage show for a flag: kebab-case (FRICTION F13). */
    private static String flagName(Map<String, Object> p) {
        return Cli.kebab(str(p, "name"));
    }

    private static String paramDoc(Map<String, Object> p) {
        StringBuilder sb = new StringBuilder(str(p, "type"));
        Object dflt = p.get("default");
        if (dflt != null) sb.append(" (default: ").append(dflt).append(')');
        else if (Boolean.TRUE.equals(p.get("defaultComputed")))
            sb.append(" (default: computed at call time)");
        return sb.toString();
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s + ' ';
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String toolNames(List<Map<String, Object>> tools) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> t : tools) names.add(name(t));
        return "Tools: " + String.join(", ", names);
    }

    // ---- conversion --------------------------------------------------------------

    private static final Object CONVERSION_FAILED = new Object();

    private static Object convert(String raw, String type) {
        try {
            switch (type) {
                case "String":  return raw;
                case "Int":     return Integer.valueOf(raw.trim());
                case "Long":    return Long.valueOf(raw.trim());
                case "Double":  return Double.valueOf(raw.trim());
                case "Float":   return Float.valueOf(raw.trim());
                case "Short":   return Short.valueOf(raw.trim());
                case "Byte":    return Byte.valueOf(raw.trim());
                case "Boolean":
                    String t = raw.trim().toLowerCase();
                    if (t.equals("true")) return Boolean.TRUE;
                    if (t.equals("false")) return Boolean.FALSE;
                    return CONVERSION_FAILED;
                default:        return CONVERSION_FAILED;
            }
        } catch (NumberFormatException e) {
            return CONVERSION_FAILED;
        }
    }

    // ---- tiny typed views over the parsed contract -------------------------------

    private static String name(Map<String, Object> tool) { return str(tool, "tool"); }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> params(Map<String, Object> tool) {
        Object v = tool.get("params");
        return v == null ? new ArrayList<>() : (List<Map<String, Object>>) (List<?>) v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? new ArrayList<>() : (List<Object>) v;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
