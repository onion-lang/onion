# Compiler Architecture

The Onion compiler is a multi-phase pipeline compiler that transforms source code into JVM bytecode.

## Overview

```
Source Code (.on files)
    ↓
[1] Parsing (JavaCC) → Untyped AST
    ↓
[2] Rewriting → Normalized Untyped AST
    ↓
[3] Type Checking → Typed AST
    ↓
[4] Code Generation (ASM) → JVM Bytecode (.class files)
```

## Implementation

- **Language**: Scala 3.6.2
- **Parser Generator**: JavaCC 5.0
- **Bytecode Library**: ASM 9.8
- **Build Tool**: SBT
- **JVM Target**: Java 17+

## Phase 1: Parsing

**File**: `src/main/scala/onion/compiler/Parsing.scala`

### Grammar

The parser grammar is defined in `grammar/JJOnionParser.jj` using JavaCC syntax.

### Process

1. JavaCC generates `JJOnionParser.java` from the grammar
2. Source code is tokenized and parsed
3. `ASTBuilder` constructs an untyped Abstract Syntax Tree
4. Parser errors are collected and reported

### Output

Untyped AST defined in `src/main/scala/onion/compiler/AST.scala`:

- `ClassDef` - Class definitions
- `MethodDef` - Method definitions
- `VarDef` - Variable declarations
- Expressions: `BinaryOp`, `MethodCall`, `Literal`, etc.

## Phase 2: Rewriting

**File**: `src/main/scala/onion/compiler/Rewriting.scala`

### Purpose

Transform and normalize the untyped AST:

- Desugar complex constructs
- Normalize expressions
- Prepare for type checking

### Examples

- Convert high-level constructs to simpler forms
- Expand syntactic sugar
- Normalize operator precedence

## Phase 3: Type Checking

**File**: `src/main/scala/onion/compiler/Typing.scala` (86KB - largest component)

### Responsibilities

1. **Type Inference**: Determine types of expressions
2. **Type Validation**: Ensure type compatibility
3. **Name Resolution**: Resolve variable and method names
4. **Symbol Tables**: Build and maintain symbol information

### Supporting Components

**Symbol Tables** (`src/main/scala/onion/compiler/`):
- `ClassTable.scala` - Class symbol table
- `LocalContext.scala` - Local variable environments
- `Symbol.scala` - Symbol definitions

**Error Reporting**:
- `SemanticError.scala` - Type error definitions
- `SemanticErrorReporter.scala` - Error collection

**Type System**:
- `BasicType` - Primitive types (Int, Long, Double, etc.)
- `ClassType` - Reference types
- `ArrayType` - Array types
- `NullType` - Null literal type

### Process

1. Build class table from class definitions
2. Process imports and resolve external types
3. Type check method bodies
4. Validate assignments and method calls
5. Infer types where possible
6. Report type errors

### Output

Typed AST defined in `src/main/scala/onion/compiler/TypedAST.scala`:

- Each node has type information
- Names are resolved to symbols
- Ready for code generation

## Phase 4: Code Generation

**File**: `src/main/scala/onion/compiler/AsmCodeGeneration.scala` (42KB)

### ASM Library

Uses the ASM library for bytecode generation:
- Type-safe bytecode emission
- Stack frame management
- Local variable tracking

### Supporting Components

**Bytecode Utilities** (`src/main/scala/onion/compiler/bytecode/`):
- `MethodEmitter.scala` - JVM method generation
- `LocalVarContext.scala` - Local variable management
- `AsmUtil.scala` - ASM helper functions

**Visitor Pattern**:
- `AsmCodeGenerationVisitor.scala` - AST traversal for code generation

### Process

1. Create `ClassWriter` for each class
2. Generate class metadata (name, superclass, interfaces)
3. Generate fields from member variables
4. Generate methods:
   - Create `MethodVisitor`
   - Emit bytecode instructions
   - Manage local variables and stack
5. Generate constructors
6. Finalize class bytecode

### Output

`CompiledClass` objects containing:
- Class name
- JVM bytecode (byte array)
- Can be written to `.class` files or loaded in-memory

## Compiler Orchestration

**File**: `src/main/scala/onion/compiler/OnionCompiler.scala`

### Processor Pipeline

All phases implement `Processor[A, B]` trait:

```scala
trait Processor[A, B] {
  def process(input: A): B

  def andThen[C](next: Processor[B, C]): Processor[A, C]
}
```

### Composition

Phases are composed using `andThen`:

```scala
val compiler =
  Parsing
    .andThen(Rewriting)
    .andThen(Typing)
    .andThen(AsmCodeGeneration)
```

### Compilation Result

Returns `CompilationOutcome`:
- `Success(classes: Seq[CompiledClass])` - Compilation succeeded
- `Failure(errors: Seq[CompileError])` - Compilation failed

## Entry Points

### CompilerFrontend

**File**: `src/main/scala/onion/tools/CompilerFrontend.scala`

Command-line interface for `onionc`:
- Parses command-line arguments
- Invokes compiler
- Writes `.class` files to disk

### ScriptRunner

**File**: `src/main/scala/onion/tools/ScriptRunner.scala`

Command-line interface for `onion`:
- Compiles source files to memory
- Loads classes with custom classloader
- Executes main method or top-level code

### Shell

**File**: `src/main/scala/onion/tools/Shell.scala`

Interactive REPL:
- Reads expressions
- Compiles incrementally
- Evaluates and prints results

## Class Loading

**File**: `src/main/scala/onion/compiler/OnionClassLoader.scala`

Custom classloader for in-memory compilation:
- Accepts bytecode directly
- Defines classes at runtime
- Supports script execution

## Error Handling

### Compilation Errors

**Types**:
- `ParseError` - Syntax errors
- `SemanticError` - Type errors
- `CompileError` - General compilation errors

**Reporting**:
- `CompilationReporter` - Formats and displays errors
- Includes source location (file, line, column)
- Shows context around error

### Example Error

```
Error: Type mismatch
  Expected: Int
  Found: String
  at MyProgram.on:15
    val x: Int = "hello"
                 ^^^^^^^
```

## Utilities

### Toolbox

**Location**: `src/main/scala/onion/compiler/toolbox/`

Helper utilities:
- `Classes.scala` - Class manipulation
- `Paths.scala` - Path handling
- `SymbolGenerator.scala` - Unique symbol generation
- `MethodInvoker.scala` - Reflection-based invocation
- `Boxing.scala` - Primitive/object boxing

### Environment

**Location**: `src/main/scala/onion/compiler/environment/`

Type resolution:
- `ClassFileTable.scala` - Caches loaded classes
- `AsmRefs.scala` - ASM type references
- `ReflectionRefs.scala` - Reflection-based types

## Build Process

### SBT Configuration

**File**: `build.sbt`

- Version: 0.2.0-SNAPSHOT
- Scala version: 3.6.2
- Main class: `onion.tools.CompilerFrontend`

### Parser Generation

Automatically regenerates parser when grammar changes:

```scala
sourceGenerators in Compile += Def.task {
  val grammar = file("grammar/JJOnionParser.jj")
  val parser = sourceManaged.value / "java" / "onion" / "compiler" / "parser" / "JJOnionParser.java"

  if (grammar.lastModified() > parser.lastModified()) {
    javacc(classpath.value, sourceManaged.value / "java", streams.value.log)
  }
}
```

### Assembly

Creates standalone JAR:

```bash
sbt assembly
# Creates onion.jar
```

### Distribution

Creates distribution ZIP:

```bash
sbt dist
# Creates target/onion-dist.zip with:
#   - onion.jar
#   - lib/ (dependencies)
#   - bin/ (scripts)
#   - run/ (examples)
```

## Testing

**Framework**: ScalaTest 3.2.19

**Location**: `src/test/scala/onion/compiler/tools/`

**Base Class**: `AbstractShellSpec`
- Runs Onion code
- Captures output
- Verifies results

**Example Tests**:
- `HelloWorldSpec` - Basic output
- `FactorialSpec` - Recursion
- `StringInterpolationSpec` - String features

## Development Workflow

1. **Modify Grammar**: Edit `grammar/JJOnionParser.jj`
2. **Compile**: Run `sbt compile` (regenerates parser)
3. **Test**: Run `sbt test`
4. **Iterate**: Repeat

## Performance Characteristics

- **Parsing**: O(n) where n = source size
- **Type Checking**: O(n × m) where n = nodes, m = symbols
- **Code Generation**: O(n) where n = typed AST nodes

## Future Enhancements

Potential improvements:
- Incremental compilation
- Better error messages
- Type inference improvements
- Optimization passes
- Debugging support

## Next Steps

- [Language Specification](specification.md) - Language details
- [Building from Source](../contributing/building.md) - Developer guide
- [Contributing](../contributing/development.md) - How to contribute
