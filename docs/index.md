# Onion Programming Language

![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)

**Onion** is a statically-typed, object-oriented programming language that compiles to JVM bytecode. It combines the power of static typing with a concise, expressive syntax and seamless Java interoperability.

## Key Features

- **Statically Typed** - Catch errors at compile time with local type inference
- **Object-Oriented** - Classes, inheritance, interfaces, and method overloading
- **Functional Elements** - Lambda expressions, closures, and first-class functions
- **Java Interoperability** - Direct access to Java libraries and frameworks
- **JVM Target** - Compiles to efficient JVM bytecode
- **Concise Syntax** - Clean, readable code with minimal boilerplate

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
```

## Getting Started

- [Installation Guide](getting-started/installation.md) - Set up Onion on your system
- [Hello World Tutorial](getting-started/hello-world.md) - Your first Onion program
- [Quick Start](getting-started/quick-start.md) - Essential language features

## Language Guide

- [Overview](guide/overview.md) - Language philosophy and design
- [Basic Syntax](guide/basic-syntax.md) - Variables, operators, and expressions
- [Control Flow](guide/control-flow.md) - if, while, for, foreach, and select
- [Functions](guide/functions.md) - Function definitions and lambda expressions
- [Classes and Objects](guide/classes-and-objects.md) - Object-oriented programming
- [Java Interoperability](guide/java-interop.md) - Using Java libraries

## Tools

- **onionc** - Compile Onion source files to .class files
- **onion** - Run Onion scripts directly (compile and execute)
- **Shell** - Interactive REPL for experimentation

## Project History

Originally written in Java in 2005, Onion has been completely rewritten in Scala 3.6.2, with only the parser using JavaCC. The compiler follows a classic multi-phase architecture:

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
