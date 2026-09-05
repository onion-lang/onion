## Onion — typed tools for a messy world [![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)](https://github.com/onion-lang/onion/actions)

Onion is a statically typed language for turning messy external data into checked,
reversible tools. It runs on the JVM and calls Java directly.

Most languages hand you a `String` at the boundary and wish you luck. Onion asks you to
describe the boundary once — and derives the parser, the printer, the failure channel and
the command-line interface from that one description.

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"

def main(path: String, min: Int = 400): void {
  val each = file(path).eachLine(Access::common())

  foreach a: Access in Outcome::values(each) {
    if a.status() >= min { println(a.status() + " " + a.path()) }
  }
  foreach d: Defect in Outcome::defects(each) {
    println("line " + d.origin().line() + ": " + d.expected())
  }
}
```

One declaration gives you parsing, printing, and a CLI with `--min` and `--help` derived
from `main`'s signature.

The last two lines are the point. A thousand-line log with five corrupted lines gives you
995 rows **and** the five you could not read, each with its line number — where almost
every other tool hands back 995 rows and no indication the other five ever existed.

Properties can be checked by the compiler, at build time:

```onion
record Pt(x: Int, y: Int)
  shape text = re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::text().parse(Pt::text().print(p)).get() == p }
```

That law really runs — over generated samples, during `onionc`. It is deliberately not on
the `Access` shape above: `\S+` cannot match an empty string, so printing an `Access` with
an empty `ip` produces text that does not parse back. The round trip holds on the data a
shape can actually represent, and saying which is the difference between a reversible
language and one that claims to be. Writing the law on `Access` is how the sentence you
just read got corrected.

## Installation

Install the latest release with one command (requires Java 17+):

```sh
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh
```

This downloads `onion.jar` from the latest GitHub Release and installs the
`onion`, `onionc` and `onion-repl` commands into `~/.local/bin`.

Once installed, run scripts directly — shebang scripts work too:

```sh
echo 'println("hello")' > hello.on
onion hello.on
```

```onion
#!/usr/bin/env onion
println("Hello, " + args[0] + "!")
```

Or start a multi-file project instead of a single script:

```sh
onion new hello
cd hello
onion run
onion test
```

See the [Project CLI guide](https://onion-lang.org/tools/project-cli/)
for the manifest, source layout, build cache, and test conventions.

## Language Snapshot

Local `val` / `var` declarations can omit types when an initializer is present, and control-flow constructs such as `if` are expressions.

```onion
val name = "Onion"
val label = if name.length > 0 { "ok" } else { "empty" }

val xs = [1, 2, 3]                       // List literal
val ages = ["alice": 12, "bob": 34]      // Map literal (insertion-ordered)

val double: Int -> Int = x -> x * 2      // lambdas: bare params, expression bodies
foreach i: Int in 0..<xs.size() {        // ranges: a..b inclusive, a..<b exclusive
  println("xs[#{i}] = #{xs.get(i)}") // interpolation (nested strings OK)
}

val cmp: Comparator[Integer] = (a, b) -> (b as Int) - (a as Int)  // SAM conversion
Collections::sort(mutable, cmp)

val branch = Proc::run("git", "branch", "--show-current")  // run external commands
val opts = Args::parse(args)                                // CLI flags & options
foreach f: String in Files::glob(".", "*.on") { ... }       // glob
// onion --watch script.on  -> rerun on every save

def add(x: Int, y: Int): Int = x + y

val double: Int -> Int = (x) -> { return x * 2; }
val inc = (x: Int) -> { return x + 1; }
println(double(21))
```

### Boundaries

See [Shapes](https://onion-lang.org/guide/shapes/) for the whole story: named shapes per
record, `Outcome` instead of `null`, per-line reads that keep what failed, and `canPrint()`
answering whether a shape is reversible rather than the printing method silently not
existing.

### Null Safety

Kotlin-style null safety, including nullable-aware generics:

```onion
val name: String? = lookup()
val len = name?.length() ?: 0          // safe call + Elvis
val first = rows?[0]                   // safe indexing
val sure: String = name!!              // non-null assertion (throws on null)

if name != null {
  println(name.length())           // smart cast: name is String here
}

class Box[T] { ... }                   // bare [T] accepts String? too;
new Box[String?](name)                 // deref inside the body needs ?. / checks
```

### Records, Enums and Primary Constructors

```onion
record Pair[A, B](first: A, second: B)
val (a, b) = new Pair[String, Integer]("x", 42)   // destructuring

enum Planet(mass: Double) {
  MERCURY(3.3e23),
  EARTH(5.97e24)
public:
  def heavierThan(other: Planet): Boolean = this.mass() > other.mass()
}

class Point(val x: Int, val y: Int) {              // primary constructor
public:
  def dist(): Int = this.x * this.x + this.y * this.y
}

sealed interface Shape {}
record Circle(r: Int) conforms Shape
record Rect(w: Int, h: Int) conforms Shape
select shape {                                     // exhaustiveness-checked
  case Circle(r):  println("circle " + r)
  case Rect(w, h): println(w * h)
}
```

### Type Classes

```onion
trait Numeric[T] {
  def plus(a: T, b: T): T
  def zero(): T
}
instance Numeric[Int] {
  def plus(a: Int, b: Int): Int = a + b
  def zero(): Int = 0
}
def sum[T: Numeric](xs: List[T]): T { ... }
```

`trait` / `instance` with dictionary passing for constrained generics.

### Do Notation for Monadic Composition

Onion supports Haskell-style do notation for composing monadic operations (Option, Result, Future, etc.):

```onion
// Async computation with do notation
val result: Future[Int] = do[Future] {
  x <- Future::async { fetchUser() }
  y <- Future::async { fetchData(x) }
  ret x + y
}

// Option chaining
val user: Option[String] = do[Option] {
  id <- lookupId("alice")
  profile <- loadProfile(id)
  ret profile.name
}
```

### Trailing Lambda Syntax

Methods accepting a function as the last parameter can use trailing lambda syntax:

```onion
// Traditional call
list.map((x: Int) -> x * 2)

// With trailing lambda
list.map { x -> x * 2 }

// Multiple arguments + trailing lambda
list.fold(0) { acc, x ->
  acc + x
}

// No parameters: just the body; works on static calls too
val answer: Future[Int] = Future::async { compute() }
```

### Asynchronous Programming with Future

Built-in `Future[T]` type for async operations:

```onion
val future: Future[String] = Future::async {
  Http::get("https://api.example.com/data")
}

future.map { data -> parseJson(data) }
      .onSuccess { result -> println(result) }
      .onFailure { error -> println("Error: " + error) }
```

### Shape-First Scripting

Onion collapses the boilerplate layers a script usually needs. Scheme-prefixed
raw literals turn a shape into a value; `prefix"raw"` desugars to `prefix("raw")`
for **any** identifier, so `re`/`file`/`http` are built in and you can define your
own prefix just by defining a function of that name:

```onion
val p    = re"\d+-\d+"                          // compiled Pattern (raw: no \\ escaping)
val rows = file"data.csv".csvRows()             // read + RFC 4180 parse, header-mapped
def sql(q: String): String = "[SQL] " + q      // a user-defined prefix...
sql"SELECT * FROM t"                            // ...used as sql("SELECT * FROM t")
```

A record can derive a typed parser straight from a regex shape, and the pipeline
operator `|>` threads a value through functions:

```onion
record Hit(ip: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

Hit::parseAll(file"access.log".text())          // List[Hit], bad lines skipped
  .filter { h -> h.status() >= 500 }
  .groupBy { h -> h.path() }
  |> println
```

A top-level `main` with typed parameters derives its whole command-line interface
(required params are positional, defaulted params become `--flags`, usage is
generated on error):

```onion
def main(name: String, count: Int = 3, loud: Boolean = false): void { ... }
// $ onion greet.on world --count 5 --loud
```

## Architecture

Onion was originally written in Java, and has been rewritten in Scala completely except
the parser, which uses JavaCC.

The post-parse compiler is now an explicit pipeline:

```text
Parsing -> Rewriting -> Typing -> TailCallOptimization
        -> MutualRecursionOptimization -> TypedAstCodeGeneration -> backend.asm.AsmBackend
```

- `Typing.scala` is the orchestration layer for header/outline/body/duplication passes, with per-unit state held in `typing.session.TypingSession` / `TypingUnitContext`.
- Type name resolution now lives in `onion.compiler.typing.NameResolver`, with `NameMapper` retained only as a compatibility facade.
- `TypedGenerating.scala` is now just a public legacy facade over `onion.compiler.codegen.legacy.TypedGeneratingBridge`; the main pipeline targets `onion.compiler.codegen.TypedAstCodeGeneration` and `onion.compiler.backend.asm.AsmBackend`.
- `OnionCompiler` delegates execution and timing to `onion.compiler.pipeline.CompilerPipeline`, which can emit phase-by-phase compile profiles.

## Tools

### onionc

#### Usage

```txt
onionc [options] source files...
```

####  Available options:

* -classpath <classpath> Set classpath of source files in compilation.
* -encoding <encoding> Set encoding of source files.
* -d <output directory> Set output directory of results.
* -maxErrorReport <error count> Set the maximum number of compilation errors reported.
* --dump-ast Print parsed AST to stderr.
* --dump-typed-ast Print typed AST summary to stderr.
* --profile-compile Emit a phase-by-phase compile profile.
* --profile-format <text|json> Set compile profile format.
* --profile-output <target> Write the profile to stderr, stdout, or a file.
* --warn <off|on|error> Set warning level.
* --Wno <codes> Suppress warnings (e.g., W0001,unused-parameter).

`onionc` compiles source files into class files in the directorys corresponding to module names
of source files rooted by "-d" option.  If "-d" is not specified, the value of "-d" is specified as the current directory.

For example, if source files which module name is "org.onion_lang" is compiled, class files are generated under:

* Unix-like OS : org/onion_lang
* Windows: org\onion_lang

### onion

#### Usage

```
    onion [options] source files... [command line arguments]
```

#### Available options
* -classpath <classpath> classpath of source files in compilation.
* -encoding <encoding> encoding of source files.
* -maxErrorReport <error count> the maximum number of compilation errors reported.
* --dump-ast Print parsed AST to stderr.
* --dump-typed-ast Print typed AST summary to stderr.
* --profile-compile Emit a phase-by-phase compile profile.
* --profile-format <text|json> Set compile profile format.
* --profile-output <target> Write the profile to stderr, stdout, or a file.
* --warn <off|on|error> Set warning level.
* --Wno <codes> Suppress warnings (e.g., W0001,unused-parameter).

### Project CLI

`onion new <name>` scaffolds a convention-over-configuration project with a
manifest, `src/`, and `tests/`; `onion build`, `onion run [-- args...]`,
`onion test`, and `onion clean` then work from the project root or any
nested directory. See the
[Project CLI guide](https://onion-lang.org/tools/project-cli/) for the full
manifest, layout, cache, and test conventions.

### REPL

Use `onion repl`, `onion-repl`, or `sbt repl` to start the interactive REPL. The promoted REPL supports multi-line input, persistent `resN` bindings, `:load`, `:time`, `:classpath`, `:type`, `:typed`, `:ast`, and `:bytecode`.

`onion` compiles source files into in-memory class files and execute them.  The entry point is:

1. A main method if there is an explicit class definition and it have the main method.
2. The main method of the class on the top.
3. Otherwise, the first top-level declaration or expression.

## Limitations

* The compiler enforces a no-crash / no-miscompile bar (mutation fuzzer, crash-reproducer corpus, codegen-correctness tests); if you find a crash or miscompilation, please file a minimal repro.
* Generics are erasure-based: no reified type info, and type arguments are invariant (no variance or wildcards).
* Tail-call optimization covers direct and mutual self-recursion (not general continuation-passing style).
* Diagnostics are still improving; some errors may be reported later in the pipeline than ideal.
