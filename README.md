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

val double: Int -> Int = (x: Int) -> { return x * 2; }
IO::println(double.call(21))
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

`onion` compiles source files into in-memory class files and execute them.  The entry point is:

1. A main method if there is an explicit class definition and it have the main method.
2. The main method of the class on the top.
3. Otherwise, the first statement on the top.

## Limitations

* Some compilation-time checks is not implemented yet.  For example,
  It's not checked that abstract methods are implemented. Compiled
  codes maybe rejected by class file verifiers sometimes.  If you
  find the problem, reporting it to help me.

* Currently, `onionc` has edge cases.  The compiler crashes sometimes.
  The source codes in example directory compiles and is executed correctly.

## BuildHive (Jenkins)

This software includes softwares developed by [Apache Software Foundation](http://www.apache.org/).
