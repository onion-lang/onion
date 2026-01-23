## Onion - A Statically Typed Programming Language on JVM [![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)](https://github.com/onion-lang/onion/actions)

Onion is an object-oriented and statically typed programming language. Source codes of Onion 
compiles into JVM class files as in-memory or real files.

Originally, Onion was written in Java.  It has been rewritten in Scala completely except Parser,
using JavaCC.

## Language Snapshot

Local `val` / `var` declarations can omit types when an initializer is present, and control-flow constructs such as `if` are expressions.

```onion
val name = "Onion"
val label = if name.length > 0 { "ok" } else { "empty" }

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

The compiler parses source code into an untyped AST and then performs type
checking to produce a **typed AST**.  The old intermediate representation (IRT)
has been folded into this typed tree.  Code generation now runs on the typed
AST via a thin compatibility layer using ASM.

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
* --warn <off|on|error> Set warning level.
* --Wno <codes> Suppress warnings (e.g., W0001,unused-parameter).

`onion` compiles source files into in-memory class files and execute them.  The entry point is:

1. A main method if there is an explicit class definition and it have the main method.
2. The main method of the class on the top.
3. Otherwise, the first statement on the top.

## Limitations

* The compiler still has edge cases and can crash; please report minimal repros.
* Generics are erasure-based (no variance, wildcards, or reified type info).
* Diagnostics are still improving; some errors may be reported later in the pipeline.

## BuildHive (Jenkins)

This software includes softwares developed by [Apache Software Foundation](http://www.apache.org/).
