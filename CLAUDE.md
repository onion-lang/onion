# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- **Build the project**: `sbt compile`
- **Run tests**: `sbt test`  
- **Run a single test**: `sbt 'testOnly *HelloWorldSpec'`
- **Package JAR**: `sbt assembly` or `sbt packageBin`
- **Create distribution**: `sbt dist` (creates a distribution zip in target/dist)
- **Run a script**: `sbt 'runScript <script.on> [args]'`
- **Clean**: `sbt clean`

## High-Level Architecture

The Onion compiler follows a pipeline architecture with distinct phases:

1. **Parsing** (`Parsing.scala`) - Uses JavaCC-generated parser (`JJOnionParser.jj`) to create untyped AST
2. **Rewriting** (`Rewriting.scala`) - Transforms and normalizes the AST
3. **Type Checking** (`Typing.scala`) - Produces typed AST from untyped AST
4. **Code Generation** (`TypedGenerating.scala`) - Generates JVM bytecode from typed AST
   - Currently transitioning from BCEL to ASM for bytecode generation
   - `AsmCodeGeneration.scala` contains the new ASM-based backend

### Key Components

- **Processor Pipeline**: Compiler phases extend `Processor[A,B]` trait and can be composed with `andThen`
- **AST Types**: 
  - Untyped AST in `AST.scala`
  - Typed AST in `TypedAST.scala`
- **Entry Points**:
  - `onion.tools.CompilerFrontend` - Main compiler executable (`onionc`)
  - `onion.tools.ScriptRunner` - Script execution (`onion`)
  - `onion.tools.Shell` - Interactive shell/REPL
- **Error Handling**: `SemanticError`, `CompileError`, and `SemanticErrorReporter` for compilation diagnostics

### Testing

Tests use ScalaTest and extend `AbstractShellSpec` for integration testing. Test files are in `src/test/scala/onion/compiler/tools/`.

### Language Features

Onion is a statically-typed, object-oriented language that compiles to JVM bytecode. Key features include:
- Classes with public/private visibility modifiers
- Static and instance methods
- Type inference
- First-class functions (Function0 through Function10 interfaces)
- Module system with imports