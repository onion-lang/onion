# Script Runner (onion)

The `onion` command compiles and executes Onion source files directly in memory, without creating `.class` files.

## Usage

```bash
onion [options] source files... [program arguments]
```

## Options

### `-classpath <classpath>`

Set the classpath for compilation and execution.

```bash
onion -classpath lib/mylib.jar MyScript.on
```

### `-encoding <encoding>`

Specify the character encoding of source files.

```bash
onion -encoding UTF-8 MyScript.on
```

### `-maxErrorReports <count>`

Limit the number of compilation errors reported.

```bash
onion -maxErrorReports 10 MyScript.on
```

### `--dump-ast`

Print the parsed AST to stderr before running the script.

```bash
onion --dump-ast MyScript.on
```

### `--dump-typed-ast`

Print a typed AST summary (classes, fields, methods) to stderr before running the script.

```bash
onion --dump-typed-ast MyScript.on
```

### `--warn <off|on|error>`

Control warning reporting. `error` treats warnings as compilation errors.

```bash
onion --warn error MyScript.on
```

### `--Wno <codes>`

Suppress specific warning categories by code or name.

```bash
onion --Wno W0001,unused-parameter MyScript.on
```

## Program Arguments

Arguments after the source file(s) are passed to your program:

```bash
onion MyScript.on arg1 arg2 arg3
```

Access them in your code:

```onion
class MyScript {
  public:
    static def main(args :String[]) {
      foreach arg :String in args {
        IO::println("Argument: " + arg)
      }
    }
}
```

## Entry Point

The script runner determines the entry point automatically:

### 1. Explicit Main Method

If a class has a `main` method, it's used as the entry point:

```onion
class MyProgram {
  public:
    static def main(args :String[]) {
      IO::println("Hello from main method")
    }
}
```

### 2. First Class with Main

If multiple classes have `main` methods, the first one is used:

```onion
class First {
  public:
    static def main(args :String[]) {
      IO::println("This will run")
    }
}

class Second {
  public:
    static def main(args :String[]) {
      IO::println("This won't run")
    }
}
```

### 3. Top-Level Statements

If there's no explicit `main` method, the first top-level statement is the entry point:

```onion
IO::println("Hello, World!")

val x: Int = 10
IO::println("x = " + x)

// These statements execute immediately
```

## Examples

### Simple Script

**hello.on:**
```onion
IO::println("Hello, World!")
```

Run:
```bash
$ onion hello.on
Hello, World!
```

### With Arguments

**greet.on:**
```onion
class Greeter {
  public:
    static def main(args :String[]) {
      if args.length > 0 {
        IO::println("Hello, " + args[0] + "!")
      } else {
        IO::println("Hello, stranger!")
      }
    }
}
```

Run:
```bash
$ onion greet.on Alice
Hello, Alice!

$ onion greet.on
Hello, stranger!
```

### Quick Calculations

**calc.on:**
```onion
val a: Int = 10
val b: Int = 20
IO::println("Sum: " + (a + b))
IO::println("Product: " + (a * b))
```

Run:
```bash
$ onion calc.on
Sum: 30
Product: 200
```

### File Processing

**count_lines.on:**
```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

class LineCounter {
  public:
    static def main(args :String[]) {
      if args.length == 0 {
        IO::println("Usage: onion count_lines.on <filename>")
        return
      }

      val filename: String = args[0]
      val reader: BufferedReader = new BufferedReader(
        new FileReader(filename)
      )

      var count: Int = 0
      var line: String = null
      while (line = reader.readLine()) != null {
        count = count + 1
      }

      reader.close()
      IO::println("Lines: " + count)
    }
}
```

Run:
```bash
$ onion count_lines.on data.txt
Lines: 42
```

## In-Memory Compilation

The `onion` command:

1. Compiles source files to bytecode
2. Loads classes into memory
3. Executes the entry point
4. No `.class` files are created

This is ideal for:
- Quick scripts
- Testing code snippets
- Automation tasks
- One-off programs

## Multiple Source Files

Compile and run multiple files:

```bash
onion Main.on Utils.on Helper.on
```

All files are compiled together, and the entry point is determined from the first file.

## Error Handling

### Compilation Errors

```bash
$ onion bad_syntax.on
Error: Type mismatch at bad_syntax.on:5
Compilation failed
```

### Runtime Errors

```bash
$ onion runtime_error.on
Exception in thread "main" java.lang.ArithmeticException: / by zero
    at RuntimeError.main(runtime_error.on:10)
```

## Comparison with onionc

| Feature | onion | onionc |
|---------|-------|--------|
| Creates .class files | No | Yes |
| Execution | Immediate | Requires `java` command |
| Use case | Scripts, testing | Production, libraries |
| Speed | Fast for small programs | Better for repeated runs |
| Distribution | Requires source | Can distribute .class/.jar |

## Scripting Best Practices

### Shebang Line (Unix-like systems)

Make scripts executable:

**hello.on:**
```onion
#!/usr/bin/env onion
IO::println("Hello from script!")
```

Make executable:
```bash
chmod +x hello.on
./hello.on
```

### Error Messages

Provide helpful error messages:

```onion
class Script {
  public:
    static def main(args :String[]) {
      if args.length < 2 {
        IO::println("Error: Missing arguments")
        IO::println("Usage: onion script.on <input> <output>")
        return
      }

      // Process arguments...
    }
}
```

### Exit Codes

Return appropriate exit codes:

```onion
class Script {
  public:
    static def main(args :String[]) {
      if args.length == 0 {
        System::exit(1)  // Error
      }

      // Success
      System::exit(0)
    }
}
```

## Next Steps

- [Compiler (onionc)](compiler.md) - Compile to class files
- [REPL Shell](repl.md) - Interactive programming
- [Examples](../examples/basic.md) - Example scripts
