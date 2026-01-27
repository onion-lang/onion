# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Onion is a statically-typed, object-oriented programming language that compiles to JVM bytecode. Originally written in Java, it has been rewritten in Scala (except the parser, which uses JavaCC).

**Configuration:**
- Scala version: 3.3.7
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
- **Start REPL**: `sbt repl`
- **Clean build**: `sbt clean`
- **Scala console**: `sbt console`

### Compiler Options (for onionc/onion)

- `-classpath <path>` - Set classpath for compilation
- `-encoding <encoding>` - Set source file encoding
- `-d <dir>` - Set output directory for class files
- `-maxErrorReports <n>` - Limit number of errors reported
- `--dump-ast` - Print parsed AST to stderr
- `--dump-typed-ast` - Print typed AST summary to stderr
- `--warn <off|on|error>` - Set warning level
- `--Wno <codes>` - Suppress specific warnings (e.g., W0001,unused-parameter)

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
[4] Tail Call Optimization → Optimized Typed AST
    ↓
[5] Code Generation (ASM) → JVM Bytecode
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

4. **Tail Call Optimization** (`src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`)
   - Detects tail-recursive methods (self-calls in return position)
   - Transforms tail recursion into loops to prevent stack overflow
   - Strategy:
     1. Copy parameters to loop variables at method entry
     2. Rewrite all parameter references to use loop variables
     3. Wrap method body in `while(true)` loop
     4. Replace tail calls with variable updates + continue
   - Prevents StackOverflowError for deep recursion (e.g., 10000+ calls)
   - Output: Optimized Typed AST

5. **Code Generation** (`src/main/scala/onion/compiler/AsmCodeGeneration.scala`, 42KB)
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

**Standard Library** (`src/main/java/onion/`):
- `IO` - Console I/O (println, readLine)
- `Strings` - String utilities
- `Rand` - Random number generation (int, long, double, boolean, nextInt, shuffle)
- `Assert` - Testing assertions (assertTrue, assertEquals, assertNotNull, fail)
- `Timing` - Time measurement (nanos, millis, measure, time, sleep)
- `Files` - File operations
- `DateTime` - Date/time utilities
- `Json` - JSON parsing/serialization
- `Http` - HTTP client
- `Regex` - Regular expressions
- `Option`, `Result`, `Future` - Functional types

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
3. Generated parser appears in `target/scala-3.3.7/src_managed/main/java/onion/compiler/parser/`

## Important Code Locations

- **Main compiler logic**: `src/main/scala/onion/compiler/`
- **Optimizations**: `src/main/scala/onion/compiler/optimization/`
  - `TailCallOptimization.scala` - Tail recursion → loop transformation
- **Parser grammar**: `grammar/JJOnionParser.jj`
- **Runtime library**: `src/main/java/onion/` (Java interfaces)
- **Tools (CLI)**: `src/main/scala/onion/tools/`
- **Tests**: `src/test/scala/onion/compiler/tools/`
- **Test programs**: `src/test/run/` (example Onion programs)
- **Build config**: `build.sbt`

## Language Syntax

### Basic Syntax

```onion
// Class definition with inheritance and interface implementation
class MyClass : ParentClass <: Interface1, Interface2 {
  val immutableField: String      // immutable field
  var mutableField: Int           // mutable field
public:
  def method(arg: Type): ReturnType { ... }
  static def staticMethod(): void { ... }
  def this { /* constructor */ }
}

// Import syntax with alias
import {
  java.util.*
  java.lang.Long as JLong;
}

// Type cast (requires parentheses for method chaining)
val btn: JButton = (event.source as JButton)
val text: String = (obj as JButton).getText()

// Static method call
IO::println("Hello")
Long::toString(42L)

// Instance method call
obj.method()
obj?.safeMethod()  // safe call operator (returns null if obj is null)
```

### Control Flow

```onion
// if/else
if condition { ... } else { ... }

// while loop
while condition { ... }

// for loop
for i = 0; i < 10; i++ { ... }

// foreach
foreach item: Type in collection { ... }

// select (pattern matching on values)
select value {
case 1, 2, 3: ...
case 4: ...
else: ...
}

// break and continue
while true {
  if done { break }
  if skip { continue }
}
```

### Functions and Lambdas

```onion
// Lambda expressions
val f: Function1[Int, Int] = (x: Int) -> x * 2
val g = (x, y) -> x + y

// Trailing lambda syntax
list.map { x => x * 2 }
list.filter { x => x > 0 }

// Method reference (static)
Type::methodName
```

### Advanced Features

**Do Notation for Monadic Composition:**
```onion
do[Future] { x <- asyncOp(); ret x + 1 }
do[Option] { a <- getA(); b <- getB(); ret a + b }
```

**Asynchronous Programming:**
```onion
val future: Future[String] = Future::async(() -> { longOperation() })
future.map((s) -> s.toUpperCase())
future.onSuccess((s) -> IO::println(s))
future.onFailure((e) -> IO::println("Error: " + e.message()))
```

**Try-Catch:**
```onion
try {
  riskyOperation()
} catch e: Exception {
  IO::println("Error: " + e.message())
}
```

## Common Syntax Mistakes (IMPORTANT)

These are frequently confused with other languages. **Always check these:**

| Wrong (Java/Scala style) | Correct (Onion) |
|--------------------------|-----------------|
| `catch (e: Exception) { }` | `catch e: Exception { }` - no parentheses |
| `expr$Type` | `expr as Type` - use `as` keyword |
| `(expr as Type).method()` requires parentheses | ✓ correct for method chaining |
| `import java.util.*;` | `import { java.util.* }` - braces, no semicolon after `*` |
| `import { Foo = pkg.Class; }` | `import { pkg.Class as Foo; }` - `as` for alias |
| `for (int i = 0; ...)` | `for var i: Int = 0; ...` - no parentheses |
| `public void method()` | `public: def method(): void` - section-based access |
| `this.field = value` in constructor | ✓ correct - fields must use `this.` |
| `new int[10]` | `new Int[10]` - capitalized type names |
| `Long.toString(0)` | `Long::toString(0L)` - `::` for static, `L` suffix for long literal |

## Known Limitations

- Compiler may crash on certain edge cases (examples in `run/` are verified to work)
- Generics are erasure-based (no variance, wildcards, or reified type info)
- Diagnostics are still improving; some errors may be reported later in the pipeline
- Tail call optimization only handles direct self-recursion (not mutual recursion or continuation-passing style)

## Entry Points for Execution

When using `onion` command (or `sbt runScript`), the entry point is determined by:
1. A main method if there is an explicit class definition and it has the main method
2. The main method of the class on the top
3. Otherwise, the first statement on the top