# Onion Programming Language

![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)

**Onion** is a statically-typed, object-oriented programming language that compiles to JVM bytecode. It combines the power of static typing with a concise, expressive syntax and seamless Java interoperability.

## Key Features

- **Statically Typed** - Catch errors at compile time with local type inference
- **Null Safety** - Nullable types (`T?`) and safe call operator (`?.`) prevent null pointer exceptions
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
IO::println("Hello, World!")

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
    IO::println("Low")
  case 3, 4, 5:
    IO::println("Medium")
  else:
    IO::println("High")
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
      .onSuccess { result => IO::println(result) }

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
- [Java Interoperability](guide/java-interop.md) - Using Java libraries

## Reference

- [Standard Library](reference/stdlib.md) - IO, Option, Result, Future, and more
- [Compiler Architecture](reference/compiler-architecture.md) - How the compiler works

## Tools

- **onionc** - Compile Onion source files to .class files
- **onion** - Run Onion scripts directly (compile and execute)
- **Shell** - Interactive REPL for experimentation

## Project History

Originally written in Java in 2005, Onion has been completely rewritten in Scala 3.3.7, with only the parser using JavaCC. The compiler follows a classic multi-phase architecture:

1. **Parsing** - JavaCC-generated parser produces untyped AST
2. **Rewriting** - AST normalization and transformation
3. **Type Checking** - Type inference and validation
4. **Code Generation** - ASM-based bytecode generation

## Community

- [GitHub Repository](https://github.com/onion-lang/onion)
- [Issue Tracker](https://github.com/onion-lang/onion/issues)
- [Contributing Guide](contributing/development.md)

## License

Onion is open source software. See [LICENSE](https://github.com/onion-lang/onion/blob/main/LICENSE) for details.

This software includes software developed by [Apache Software Foundation](http://www.apache.org/).
