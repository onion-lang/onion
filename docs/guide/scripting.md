# Scripting

Onion is built to replace shell scripts when they outgrow bash. This
page tours the scripting toolkit end to end.

## Running Scripts

```bash
onion script.on arg1 arg2     # compile in memory and run
onion --watch script.on       # rerun on every save
onion repl                    # interactive REPL
```

Scripts can use a shebang:

```onion
#!/usr/bin/env onion
IO::println("hello")
```

Top-level statements run in order; `args` holds the command line.
Compile errors exit with a non-zero status.

## Command-Line Arguments

```onion
val opts = Args::parse(args)

if opts.flag("verbose") { ... }          // --verbose or -v
val out = opts.option("output", "a.txt") // --output=x or --output x
val n = opts.intOption("count", 1)       // numeric with default
val files = opts.positional()            // everything else
```

## Running External Commands

```onion
val branch = Proc::run("git", "branch", "--show-current")  // capture stdout, throw on failure

val r = Proc::capture("sh", "-c", "ls missing")            // never throws
if r.failed() { IO::println(r.stderr()) }

val code = Proc::exec("make", "build")                     // inherit console, exit status
```

`Proc::runIn(dir, ...)` variants take a working directory.

## Files and Globs

```onion
val text = Files::readText("config.txt")
Files::writeText("out.txt", text.toUpperCase())

foreach f: String in Files::glob(".", "*.on") {
  IO::println(f)
}
Files::glob("src", "**/*.java")    // recursive
```

## JSON and HTTP

```onion
val body = Http::get("https://api.github.com/repos/onion-lang/onion")
val v = Json::value(body)
IO::println(v["name"].asString() + " stars=" + v["stargazers_count"].asInt())
```

`Json::value` returns a navigable value: index into objects and arrays,
then convert with `asString` / `asInt` / `asDouble` / `asBoolean`;
`isNull()` reports missing paths instead of throwing.

## Scheme-Prefixed Literals

`re"..."` / `file"..."` / `http"..."` are RAW string literals (backslashes
pass through — no `\\d` escaping) that desugar to the calls `re(...)`,
`file(...)`, `http(...)`:

```onion
val p    = re"\d+-\d+"                        // compiled Pattern
val rows = file"data.csv".csvRows()           // read + RFC 4180 parse, header-mapped
val text = file"notes.txt".text()             // also: lines() / json() / write() / append()
val body = http"https://api.example.com".get() // also: getJson() / post() / postJson()
```

The literal and the function form are equivalent, so dynamic values just
use `file(path)`.

## Pattern-Attached Records

Attach a regex to a record and a typed parser is derived from the shape —
capture groups convert to the component types:

```onion
record Access(time: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

val hits = Access::parseAll(file"access.log".text())   // List[Access], bad lines skipped
val one  = Access::parse(line)                          // Access? (null on no match)
```

## Pipeline Operator

`e |> f` calls `f(e)`; `e |> f(a)` injects `e` as the first argument. A
newline before `|>` continues the pipeline:

```onion
file"access.log".lines()
  .map { l => classify(l) }
  .groupBy { c => c }
  .mapValues { xs => xs.size }
  |> println
```

## Auto-CLI

A top-level `main` with typed parameters derives its whole command-line
interface: required parameters are positional, defaulted parameters become
`--name` flags (`Boolean` defaults become switches), and a usage line is
generated on error:

```onion
def main(name: String, count: Int = 3, loud: Boolean = false): void {
  var msg = "hello " + name + " x" + count
  if loud { msg = msg.toUpperCase() }
  println(msg)
}
```

```console
$ onion greet.on world --count 5 --loud
HELLO WORLD X5
$ onion greet.on
error: missing argument: name
usage: <script> <name> [--count VALUE] [--loud]
```

## Putting It Together

```onion
#!/usr/bin/env onion
record Hit(ip: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

def main(log: String, minStatus: Int = 500): void {
  Hit::parseAll(file(log).text())
    .filter { h => h.status() >= minStatus }
    .groupBy { h => h.path() }
    .mapValues { xs => xs.size }
    |> println
}
```
