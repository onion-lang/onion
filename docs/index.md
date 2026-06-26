# Onion Programming Language

![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)

**Onion** is a statically-typed, object-oriented programming language that compiles to JVM bytecode. It combines the power of static typing with a concise, expressive syntax and seamless Java interoperability.

## Key Features

- **Statically Typed** - Catch errors at compile time with local type inference
- **Null Safety** - Nullable types (`T?`), safe calls (`?.`) and indexing (`?[]`), Elvis (`?:`), non-null assertion (`!!`), smart casts, and nullable-aware generics
- **Data Types** - Records (generic, destructurable), data-carrying enums, and exhaustiveness-checked pattern matching over sealed hierarchies
- **Bidirectional Records** - One `record ... from re"..." derive!(Json, Yaml)` declaration derives parse/format and JSON/YAML serde in *both* directions
- **Compile-Time Specs** - `law` / `example` clauses on a record are executed by the compiler at build time, so invariants like `parse∘format == id` are machine-checked
- **Concise Classes** - Primary constructors: `class Point(val x: Int, val y: Int)`
- **Object-Oriented** - Classes, inheritance, interfaces, and method overloading
- **Functional Elements** - Lambda expressions, closures, and first-class functions
- **Monadic Composition** - Do notation for Option, Result, and Future types
- **Asynchronous Programming** - Built-in Future type with functional combinators
- **Java Interoperability** - Direct access to Java libraries and frameworks
- **JVM Target** - Compiles to efficient JVM bytecode
- **Concise Syntax** - Clean, readable code with trailing lambda support

## Quick Example

```onion
// Hello World
println("Hello, World!")

// Class definition with inheritance
class Calculator : JFrame <: ActionListener {
  var result: Long

  public:
    def this {
      // Constructor logic
      this.result = 0L;
    }

    def calculate(x: Long, y: Long): Long {
      return x + y;
    }
}

// Lambda expressions
val filter: String -> Boolean = (line: String) -> {
  return line.startsWith("ERROR");
}

// Pattern matching with select
val value = 3
select value {
  case 0, 1, 2:
    println("Low")
  case 3, 4, 5:
    println("Medium")
  else:
    println("High")
}

// Do notation for monadic composition
val result: Option[Int] = do[Option] {
  x <- parseNumber("42")
  y <- parseNumber("10")
  ret x + y
}

// Async programming with Future
val future: Future[String] = Future::async(() -> { return fetchData(); })
future.map { data => processData(data) }
      .onSuccess { result => println(result) }

// Null safety with safe call operator
val name: String? = getUserName()  // Nullable type
val upper: Object? = name?.toUpperCase()  // Safe call - returns null if name is null
val display: String = name ?: "unknown"  // Elvis operator for default value
```

## Getting Started

- [Installation Guide](getting-started/installation.md) - Set up Onion on your system
- [Hello World Tutorial](getting-started/hello-world.md) - Your first Onion program
- [Quick Start](getting-started/quick-start.md) - Essential language features

## Language Guide

- [Overview](guide/overview.md) - Language philosophy and design
- [Basic Syntax](guide/basic-syntax.md) - Variables, operators, and expressions
- [Variables and Types](guide/variables-and-types.md) - Type system and type annotations
- [Null Safety](guide/null-safety.md) - Nullable types (`T?`) and safe call operator (`?.`)
- [Control Flow](guide/control-flow.md) - if, while, for, foreach, select, and do notation
- [Functions](guide/functions.md) - Function definitions and lambda expressions
- [Lambda Expressions](guide/lambda-expressions.md) - Closures and trailing lambda syntax
- [Classes and Objects](guide/classes-and-objects.md) - Object-oriented programming
- [Inheritance](guide/inheritance.md) - Subclassing and interface implementation
- [Collections](guide/collections.md) - Lists, maps, and built-in pipelines
- [Java Interoperability](guide/java-interop.md) - Using Java libraries
- [Scripting](guide/scripting.md) - re""/file"" literals, derive!, law/example, pipelines, auto-CLI

## Examples

- [Overview](examples/overview.md) - Collection of example programs
- [Basic Programs](examples/basic.md) - Hello World, arrays, loops, I/O
- [Object-Oriented Examples](examples/oop.md) - Classes, inheritance, interfaces, delegation
- [Functional Programming](examples/functional.md) - Lambdas, closures, recursion, do notation, Future

## Tools

- [Compiler (onionc)](tools/compiler.md) - Compile Onion source files to .class files
- [Script Runner (onion)](tools/script-runner.md) - Run Onion scripts directly (compile and execute)
- [REPL](tools/repl.md) - Interactive REPL for experimentation

## Reference

- [Language Specification](reference/specification.md) - Complete Onion language specification
- [Standard Library](reference/stdlib.md) - IO, Option, Result, Future, and more
- [Compiler Architecture](reference/compiler-architecture.md) - How the compiler works
- [Error Codes](reference/error-codes.md) - Common compilation errors and fixes
- [Compiler Internals](compiler/tail-call-optimization.md) - Tail call optimization

## Contributing

- [Development Guide](contributing/development.md) - How to contribute to Onion
- [Building from Source](contributing/building.md) - Build the compiler from source
- [Releasing](RELEASING.md) - Release process

## Design Notes

- [Generics Design](GENERICS_DESIGN.md) - Erasure-based generics design
- [Parser Refactoring](parser-refactoring.md) - Separating grammar from AST building
- [Quality Bar](quality-bar.md) - Measurable practical-quality indicators

## Project History

Originally written in Java in 2005, Onion has been completely rewritten in Scala 3.3.7, with only the parser using JavaCC. The compiler follows a classic multi-phase architecture:

1. **Parsing** - JavaCC-generated parser produces untyped AST
2. **Rewriting** - AST normalization and transformation
3. **Type Checking** - Type inference, name resolution, overload resolution, and typed AST construction
4. **Tail-Call Optimization** - Self-recursive loop lowering
5. **Mutual Recursion Optimization** - State-machine lowering for `@TailRecursive` groups
6. **Code Generation** - Direct typed-AST bytecode generation via ASM

## Community

- [GitHub Repository](https://github.com/onion-lang/onion)
- [Issue Tracker](https://github.com/onion-lang/onion/issues)
- [Contributing Guide](contributing/development.md)

## License

Onion is open source software. See [LICENSE](https://github.com/onion-lang/onion/blob/main/LICENSE) for details.

This software includes software developed by [Apache Software Foundation](http://www.apache.org/).
