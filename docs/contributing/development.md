# Development Guide

Guide for contributing to the Onion programming language.

## Getting Started

### Prerequisites

- JDK 17 or later
- SBT (Scala Build Tool)
- Git
- Text editor or IDE (IntelliJ IDEA recommended)

### Clone the Repository

```bash
git clone https://github.com/onion-lang/onion.git
cd onion
```

### Build the Project

```bash
sbt compile
```

This will:
- Download dependencies
- Generate the parser from JavaCC grammar
- Compile all Scala and Java source files

## Project Structure

```
onion/
├── build.sbt                 # SBT build configuration
├── grammar/
│   └── JJOnionParser.jj     # JavaCC parser grammar
├── src/
│   ├── main/
│   │   ├── scala/           # Scala source code
│   │   │   └── onion/
│   │   │       ├── compiler/        # Compiler phases
│   │   │       ├── tools/           # CLI tools
│   │   │       └── ...
│   │   └── java/            # Java runtime library
│   │       └── onion/
│   │           ├── Function0.java - Function10.java
│   │           ├── IO.java
│   │           └── ...
│   └── test/
│       ├── scala/           # Test suites
│       └── run/             # Example programs
├── run/                     # Example Onion programs
└── docs/                    # Documentation
```

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/my-new-feature
```

### 2. Make Changes

Edit source files using your preferred editor.

### 3. Compile

```bash
sbt compile
```

### 4. Run Tests

```bash
sbt test
```

### 5. Test Manually

```bash
# Run examples
sbt 'runScript run/Hello.on'

# Or use the compiler
sbt compile
sbt 'run-main onion.tools.CompilerFrontend run/Hello.on'
java -cp . Hello
```

### 6. Format Code

Follow Scala style conventions:
- 2 spaces for indentation
- Line length limit: 120 characters
- Use meaningful variable names

### 7. Commit Changes

```bash
git add .
git commit -m "Add feature: description of change"
```

### 8. Push and Create PR

```bash
git push origin feature/my-new-feature
```

Then create a Pull Request on GitHub.

## Common Development Tasks

### Modifying the Parser

Edit `grammar/JJOnionParser.jj`, then:

```bash
sbt clean compile
```

The parser will be regenerated automatically.

### Adding a Language Feature

1. Update grammar in `JJOnionParser.jj`
2. Update AST in `AST.scala`
3. Update type checking in `Typing.scala`
4. Update code generation in `AsmCodeGeneration.scala`
5. Add tests

### Adding a Test

Create a new test in `src/test/scala/onion/compiler/tools/`:

```scala
package onion.compiler.tools

class MyFeatureSpec extends AbstractShellSpec {
  "MyFeature" should "work correctly" in {
    val source = """
      |// Your test code here
      |IO::println("Test")
    """.stripMargin

    val result = runShell(source)
    result should include("Test")
  }
}
```

Run the test:

```bash
sbt 'testOnly *MyFeatureSpec'
```

### Debugging the Compiler

Add print statements or use a debugger:

```scala
// In compiler code
println(s"Debug: AST = $ast")
```

Or use IntelliJ IDEA's debugger:
1. Set breakpoints
2. Run tests in debug mode

## Code Organization

### Compiler Phases

**Parsing** (`Parsing.scala`):
- Entry point for compilation
- Uses JavaCC-generated parser
- Produces untyped AST

**Rewriting** (`Rewriting.scala`):
- Normalizes AST
- Desugars complex constructs

**Type Checking** (`Typing.scala`):
- Type inference and validation
- Name resolution
- Symbol table management

**Code Generation** (`AsmCodeGeneration.scala`):
- ASM-based bytecode generation
- JVM instruction emission

### Support Modules

**AST** (`AST.scala`, `TypedAST.scala`):
- Abstract syntax tree definitions

**Symbol Tables** (`ClassTable.scala`, `LocalContext.scala`):
- Symbol management
- Scope handling

**Error Handling** (`SemanticError.scala`, `CompilationReporter.scala`):
- Error collection
- Error formatting

## Testing Strategy

### Unit Tests

Test individual components:

```scala
class TypingSpec extends AnyFlatSpec with Matchers {
  "Type checker" should "infer Int type" in {
    // Test type inference
  }
}
```

### Integration Tests

Test complete compilation:

```scala
class IntegrationSpec extends AbstractShellSpec {
  "Compiler" should "compile and run program" in {
    val source = """IO::println("Hello")"""
    val result = runShell(source)
    result should include("Hello")
  }
}
```

### Example-Based Tests

Verify example programs compile and run:

```bash
sbt test
# Runs all tests including example verification
```

## Documentation

### Code Documentation

Use ScalaDoc for public APIs:

```scala
/**
 * Compiles Onion source code to JVM bytecode.
 *
 * @param source Source code string
 * @param config Compiler configuration
 * @return Compilation outcome
 */
def compile(source: String, config: CompilerConfig): CompilationOutcome = {
  // ...
}
```

### User Documentation

Update docs in `docs/` directory:
- Use Markdown format
- Include code examples
- Keep examples up-to-date

## Contributing Guidelines

### Code Style

- Follow Scala conventions
- Use meaningful names
- Keep functions focused
- Add comments for complex logic

### Commit Messages

Use clear, descriptive commit messages:

```
Add feature: Lambda expression support

- Implement lambda syntax in parser
- Add lambda type checking
- Generate Function interface calls
- Add lambda tests
```

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Ensure all tests pass
5. Update documentation
6. Create pull request
7. Respond to review feedback

### Issues

When creating issues:
- Use descriptive titles
- Provide example code
- Include error messages
- Specify Onion version

## Performance Considerations

- Parser generation is slow; avoid unnecessary recompilation
- Type checking can be expensive for large files
- Use incremental compilation when possible

## Debugging Tips

### Compiler Crashes

1. Identify the failing phase
2. Add debug output
3. Check AST structure
4. Verify type information

### Type Errors

1. Check symbol table
2. Verify type resolution
3. Review type conversion rules

### Bytecode Issues

1. Use `javap` to inspect generated bytecode
2. Verify stack frame correctness
3. Check local variable indices

## Release Process

1. Update version in `build.sbt`
2. Run all tests: `sbt test`
3. Create distribution: `sbt dist`
4. Tag release: `git tag v0.2.0`
5. Push tag: `git push origin v0.2.0`
6. Create GitHub release

## Next Steps

- [Building from Source](building.md) - Detailed build instructions
- [Compiler Architecture](../reference/compiler-architecture.md) - Compiler internals
- [Language Specification](../reference/specification.md) - Language details
