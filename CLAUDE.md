# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Onion is a statically-typed, object-oriented programming language that compiles to JVM bytecode. Originally written in Java, it has been rewritten in Scala (except the parser, which uses JavaCC).

**Configuration:**
- Scala version: 3.6.2
- Java version: 17
- SBT version: ~1.9+
- Main dependencies: ASM 9.8 (bytecode), JavaCC 5.0 (parser), ScalaTest 3.2.19 (testing)

## Build Commands

- **Compile project**: `sbt compile`
  - Automatically regenerates parser from `grammar/JJOnionParser.jj` if modified
- **Run tests**: `sbt test`
- **Run single test suite**: `sbt 'testOnly *HelloWorldSpec'`
- **Package JAR with dependencies**: `sbt assembly` (creates `onion.jar`)
- **Create distribution package**: `sbt dist` (creates ZIP with lib/, bin/, run/, onion.jar in target/)
- **Run Onion script**: `sbt 'runScript path/to/script.on [args]'`
- **Clean build**: `sbt clean`
- **Interactive console**: `sbt console`

## High-Level Architecture

The Onion compiler is a **multi-phase pipeline compiler** following the classic compiler architecture:

### Compilation Pipeline

```
Source Files (.on)
    ↓
[1] Parsing (JavaCC) → Untyped AST
    ↓
[2] Rewriting → Normalized Untyped AST
    ↓
[3] Type Checking → Typed AST
    ↓
[4] Code Generation (ASM) → JVM Bytecode
    ↓
Class Loading & Execution
```

### Core Compiler Phases

All phases extend `Processor[A, B]` trait and can be composed using `andThen()`:

1. **Parsing** (`src/main/scala/onion/compiler/Parsing.scala`)
   - Uses JavaCC-generated parser from `grammar/JJOnionParser.jj` (36KB grammar file)
   - Parser class: `JJOnionParser` (auto-generated in `sourceManaged/`)
   - Converts source text → Untyped AST (`AST.scala`)
   - Uses `ASTBuilder.scala` to construct AST from parser tokens

2. **Rewriting** (`src/main/scala/onion/compiler/Rewriting.scala`)
   - AST transformation and normalization
   - Simplifies complex constructs
   - Output: Normalized untyped AST

3. **Type Checking** (`src/main/scala/onion/compiler/Typing.scala`, 86KB - largest component)
   - Type inference and validation
   - Symbol resolution and name binding
   - Method resolution with overloading
   - Access control checking
   - Key supporting files:
     - `ClassTable.scala` - Class symbol table
     - `LocalContext.scala` - Local variable environments
     - `Symbol.scala` - Symbol definitions
     - `SemanticErrorReporter.scala` - Error collection
   - Output: Typed AST (`TypedAST.scala`, 37KB)

4. **Code Generation** (`src/main/scala/onion/compiler/AsmCodeGeneration.scala`, 42KB)
   - **ASM-based bytecode generation** (current implementation)
   - Visitor pattern: `AsmCodeGenerationVisitor.scala`
   - Bytecode utilities:
     - `bytecode/MethodEmitter.scala` - JVM method generation
     - `bytecode/LocalVarContext.scala` - Local variable tracking
     - `bytecode/AsmUtil.scala` - ASM helper functions
   - Output: `CompiledClass` objects (in-memory or file)

### Key Architectural Components

**Orchestration:**
- `OnionCompiler.scala` - Main compiler orchestrator, composes all phases
- Returns `CompilationOutcome` (Success with classes or Failure with errors)

**Entry Points:**
- `onion.tools.CompilerFrontend` - CLI for `onionc` command (compile to .class files)
- `onion.tools.ScriptRunner` - CLI for `onion` command (compile and run in-memory)
- `onion.tools.Shell` - Interactive REPL shell

**Type System:**
- `BasicType` - Primitives (int, long, double, boolean, byte, short, char, float)
- `ClassType` - Reference types
- `ArrayType` - Array types with component tracking
- `NullType` - Null literal type
- `OnionTypeConversion.scala` - Type conversion logic

**Symbol Tables & Environments:**
- `AbstractTable.scala`, `OrderedTable.scala`, `MultiTable.scala` - Symbol storage
- `LocalBinding.scala`, `ClosureLocalBinding.scala` - Variable bindings
- `LocalFrame.scala`, `LocalScope.scala` - Scope management
- `environment/ClassFileTable.scala` - Caches loaded class metadata

**Error Handling:**
- `CompileError.scala`, `SemanticError.scala` - Error types
- `SemanticErrorReporter.scala` - Error collection and reporting
- `CompilationReporter.scala` - User-facing error formatting

**Runtime Support:**
- `onion/Function0.java` through `Function10.java` - Function interfaces for closures
- `onion/IO.java` - I/O utilities for Onion programs
- `OnionClassLoader.scala` - Custom class loading for compiled classes

## Testing

**Framework:** ScalaTest 3.2.19

**Test Location:** `src/test/scala/onion/compiler/tools/`

**Base Class:** Tests extend `AbstractShellSpec` for integration testing

**Test Suites:**
- `HelloWorldSpec.scala` - Basic output
- `FactorialSpec.scala` - Recursion
- `StringInterpolationSpec.scala` - String features
- `BreakContinueSpec.scala` - Control flow
- `ImportSpec.scala` - Module system
- `BeanSpec.scala` - OOP features
- `ForeachSpec.scala` - Iteration
- `CompilationFailureSpec.scala` - Error handling

**Test Resources:** `src/test/run/` contains example Onion programs used by tests

## Example Programs

Located in `run/` directory:
- `Hello.on` - Simplest program: `IO::println("Hello")`
- `Factorial.on`, `Calculator.on`, `Array.on`, `Bean.on`, `List.on`, etc.

## Parser Grammar Modification

If modifying the parser grammar (`grammar/JJOnionParser.jj`):
1. Edit the JavaCC grammar file
2. Run `sbt compile` - parser will auto-regenerate
3. Generated parser appears in `target/scala-3.6.2/src_managed/main/java/onion/compiler/parser/`

## Important Code Locations

- **Main compiler logic**: `src/main/scala/onion/compiler/`
- **Parser grammar**: `grammar/JJOnionParser.jj`
- **Runtime library**: `src/main/java/onion/` (Java interfaces)
- **Tools (CLI)**: `src/main/scala/onion/tools/`
- **Tests**: `src/test/scala/onion/compiler/tools/`
- **Build config**: `build.sbt`

## Language Features

- Statically typed with type inference
- Object-oriented with classes, inheritance, access modifiers
- First-class functions (Function0-Function10 interfaces)
- Module system with imports
- Control flow: if/else, while, for, break, continue
- String interpolation
- Arrays and collections
- Static and instance methods

## Known Limitations (per README.md)

- Some compile-time checks not fully implemented (abstract methods, final method overriding)
- Compiler may crash on certain code patterns (examples in `run/` are verified to work)
- Finally clause in try-catch not yet supported