# Examples Overview

This section provides practical examples of Onion programs, demonstrating various language features and programming patterns.

## Example Programs

All examples can be found in the `run/` directory of the Onion repository.

### Basic Programs

- [Hello World](basic.md#hello-world) - Simple output
- [Arrays](basic.md#arrays) - Array manipulation
- [User Input](basic.md#user-input) - Reading input

### Object-Oriented Examples

- [Classes and Objects](oop.md#classes-and-objects) - Basic OOP
- [Inheritance](oop.md#inheritance) - Extending classes
- [Interfaces](oop.md#interfaces) - Interface implementation

### Functional Programming

- [Lambda Expressions](functional.md#lambda-expressions) - Anonymous functions
- [Closures](functional.md#closures) - Capturing variables
- [Recursion](functional.md#recursion) - Recursive algorithms

### Practical Use-Cases

- [Scripting & CLI](scripting.md) - Command-line arguments, process execution, file I/O
- [JSON & HTTP](json-http.md) - JSON/YAML parsing, HTTP client basics
- [Async & Concurrency](async.md) - Futures, async composition, do-notation
- [Error Handling](error-handling.md) - Option, Result, validation patterns

## Running Examples

### Using the Script Runner

```bash
cd run
onion Hello.on
```

### Compiling and Running

```bash
onionc Hello.on
java Hello
```

## Example Index

| File | Description | Topics Covered |
|------|-------------|----------------|
| `Hello.on` | Hello World | Basic I/O |
| `Array.on` | Array operations | Arrays, loops |
| `StringCat.on` | String concatenation | Strings, arrays |
| `ReadLine.on` | User input | I/O, strings |
| `List.on` | Dynamic lists | ArrayList, `<<` operator |
| `Foreach.on` | Foreach loop | Iteration, collections |
| `Select.on` | Pattern matching | Select expression |
| `LineFilter.on` | Lambda filtering | Lambdas, closures |
| `LineCounter.on` | File processing | Recursion, I/O |
| `Factorial.on` | Recursion | Recursive functions |
| `Delegation.on` | Delegation pattern | Interfaces, forward |
| `Calculator.on` | GUI calculator | Swing, event handling |
| `Bean.on` | JavaBean pattern | Serialization, getters/setters |
| `OrderReport.on` | Larger data pipeline | Records, enums, collection pipelines |
| `ExprEval.on` | Expression evaluator | Interfaces, polymorphism |
| `StatsApp.on` | Statistics processing | Generics, extension methods |
| `TodoManager.on` | Task manager | Records, enums, extension methods |
| `ShapeProcessor.on` | Geometry shapes | Inheritance, extension methods |
| `TextAnalyzer.on` | Text statistics | String/List extension methods |
| `ConfigApp.on` | CLI + config file | Args, YAML, `derive!(Yaml)` |
| `JsonApiClient.on` | JSON + HTTP client | Http, Json, records |
| `AsyncDownloader.on` | Concurrent futures | Future, do-notation |
| `ShellPipeline.on` | Shell process pipeline | Proc, file I/O |
| `ResultValidation.on` | Validation with errors | Option, Result |
| `StaticImports.on` | Static method imports | `import { Class::method }` |
| `UnitConverter.on` | CLI unit converter | Extension methods, Args |

## Learning Path

### Beginner

1. Start with `Hello.on` - Basic syntax
2. Try `Array.on` - Data structures
3. Explore `Foreach.on` - Iteration

### Intermediate

1. Study `Factorial.on` - Recursion
2. Learn `LineFilter.on` - Lambdas
3. Review `Bean.on` - OOP patterns
4. Try `UnitConverter.on` - Extension methods and CLI args

### Advanced

1. Analyze `Calculator.on` - GUI programming
2. Understand `Delegation.on` - Design patterns
3. Examine `LineCounter.on` - File processing

### Practical

1. `ConfigApp.on` - Build a CLI-driven config loader
2. `JsonApiClient.on` - Parse JSON and call HTTP APIs
3. `AsyncDownloader.on` - Run tasks concurrently
4. `ShellPipeline.on` - Orchestrate shell commands
5. `ResultValidation.on` - Handle errors functionally

## Next Steps

- [Basic Examples](basic.md) - Simple programs
- [OOP Examples](oop.md) - Object-oriented code
- [Functional Examples](functional.md) - Functional programming
- [Scripting Examples](scripting.md) - CLI and process automation
- [JSON & HTTP Examples](json-http.md) - Network and data formats
- [Async Examples](async.md) - Concurrent programming
- [Error Handling Examples](error-handling.md) - Validation and errors
