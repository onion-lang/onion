## Onion - A Statically Typed Programming Language on JVM [![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)](https://github.com/onion-lang/onion/actions)

Onion is an object-oriented and statically typed programming language. Source codes of Onion 
compiles into JVM class files as in-memory or real files.

Originally, Onion was written in Java.  It has been rewritten in Scala completely except Parser,
using JavaCC.

## Installation

Install the latest release with one command (requires Java 17+):

```sh
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh
```

This downloads `onion.jar` from the latest GitHub Release and installs the
`onion`, `onionc` and `onion-repl` commands into `~/.local/bin`.

More options:

```sh
# Install a specific version
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh -s -- --version=v0.2.0-M2

# Build from a source checkout instead of downloading
git clone https://github.com/onion-lang/onion && cd onion && ./install.sh --from-source

# Install somewhere else (default: ~/.local)
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | ONION_INSTALL_DIR=/opt/onion sh
```

Once installed, run scripts directly — shebang scripts work too:

```sh
echo 'IO::println("hello")' > hello.on
onion hello.on
```

```onion
#!/usr/bin/env onion
IO::println("Hello, " + args[0] + "!")
```

## Language Snapshot

Local `val` / `var` declarations can omit types when an initializer is present, and control-flow constructs such as `if` are expressions.

```onion
val name = "Onion"
val label = if name.length > 0 { "ok" } else { "empty" }

val xs = [1, 2, 3]                       // List literal
val ages = ["alice": 12, "bob": 34]      // Map literal (insertion-ordered)

val double: Int -> Int = x -> x * 2      // lambdas: bare params, expression bodies
foreach i: Int in 0..<xs.size() {        // ranges: a..b inclusive, a..<b exclusive
  IO::println("xs[#{i}] = #{xs.get(i)}") // interpolation (nested strings OK)
}

val cmp: Comparator[Integer] = (a, b) -> (b as Int) - (a as Int)  // SAM conversion
Collections::sort(mutable, cmp)

val branch = Proc::run("git", "branch", "--show-current")  // run external commands

def add(x: Int, y: Int): Int = x + y

val double: Int -> Int = (x) -> { return x * 2; }
val inc = (x: Int) -> { return x + 1; }
IO::println(double(21))
```

### Do Notation for Monadic Composition

Onion supports Haskell-style do notation for composing monadic operations (Option, Result, Future, etc.):

```onion
// Async computation with do notation
val result: Future[Int] = do[Future] {
  x <- Future::async(() -> { return fetchUser(); })
  y <- Future::async(() -> { return fetchData(x); })
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
list.map((x: Int) -> { return x * 2; })

// With trailing lambda
list.map { x => x * 2 }

// Multiple arguments + trailing lambda
future.onComplete(onSuccess, onFailure) { result =>
  IO::println("Done: " + result)
}
```

### Asynchronous Programming with Future

Built-in `Future[T]` type for async operations:

```onion
val future: Future[String] = Future::async(() -> {
  return Http::get("https://api.example.com/data");
})

future.map((data: String) -> { return parseJson(data); })
      .onSuccess((result: Object) -> { IO::println(result); })
      .onFailure((error: Throwable) -> { IO::println("Error: " + error); })
```

## Architecture

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
* -maxErrorReports <error count> Set the maximum number of comiplation errors reported.
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
* -maxErrorReports <error count> the maximum number of comiplation errors reported.
* --dump-ast Print parsed AST to stderr.
* --dump-typed-ast Print typed AST summary to stderr.
* --profile-compile Emit a phase-by-phase compile profile.
* --profile-format <text|json> Set compile profile format.
* --profile-output <target> Write the profile to stderr, stdout, or a file.
* --warn <off|on|error> Set warning level.
* --Wno <codes> Suppress warnings (e.g., W0001,unused-parameter).

### REPL

Use `onion repl`, `onion-repl`, or `sbt repl` to start the interactive REPL. The promoted REPL supports multi-line input, persistent `resN` bindings, `:load`, `:time`, `:classpath`, `:type`, `:typed`, `:ast`, and `:bytecode`.

`onion` compiles source files into in-memory class files and execute them.  The entry point is:

1. A main method if there is an explicit class definition and it have the main method.
2. The main method of the class on the top.
3. Otherwise, the first top-level declaration or expression.

## Limitations

* The compiler still has edge cases and can crash; please report minimal repros.
* Generics are erasure-based (no variance, wildcards, or reified type info).
* Diagnostics are still improving; some errors may be reported later in the pipeline.
