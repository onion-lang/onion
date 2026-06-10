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

## Putting It Together

```onion
#!/usr/bin/env onion
val opts = Args::parse(args)
val dir = opts.option("dir", ".")

val sizes = Files::glob(dir, "*.on")
  .map { f => Files::readText(dir + "/" + f).length() }

val total = sizes.fold(0) { acc, n => (acc as Int) + (n as Int) }
IO::println("#{sizes.size()} scripts, #{total} chars")
```
