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

### `-maxErrorReport <count>`

Limit the number of compilation errors reported.

```bash
onion -maxErrorReport 10 MyScript.on
```

### `-super <super class>`

Specify the class a top-level script's synthesized class extends. Only meaningful when
the source has no explicit class declaration.

```bash
onion -super java.lang.Object MyScript.on
```

### `--verbose`

Show timing for each compilation phase (parsing, rewriting, type checking, code
generation) before the script runs.

```bash
onion --verbose MyScript.on
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

### `--profile-compile`

Emit a compile profile before the script is executed.

```bash
onion --profile-compile MyScript.on
```

### `--profile-format <text|json>`

Choose text or JSON profile output.

```bash
onion --profile-compile --profile-format json MyScript.on
```

### `--profile-output <target>`

Write the compile profile to `stderr`, `stdout`, or a file path.

```bash
onion --profile-compile --profile-format json \
      --profile-output target/script-profile.json \
      MyScript.on
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

### `--no-check-laws`

Do not execute a record's `law` / `example` clauses.

```bash
onion --no-check-laws MyScript.on
```

### `--law-seed <n>` / `--law-samples <n>`

Control how `law` clauses are sampled; a falsified law reports the settings that
produced its counterexample.

```bash
onion --law-samples 500 MyScript.on
```

### `--effects`

Print each compiled method's inferred effect set (`read write net exec env clock rand console unknown`; empty means pure) to stderr.

```bash
onion --effects MyScript.on
```

### `--stacktrace`

Print the raw JVM trace for an uncaught runtime error instead of the rendered diagnostic report.

```bash
onion --stacktrace MyScript.on
```

### `--watch`

Run the script, then re-run it automatically whenever its file changes. Compile errors and runtime exceptions are printed without stopping the watch loop; press Ctrl-C to stop.

```bash
onion --watch MyScript.on
```

## Dependencies (`//> using dep`)

A script declares the Maven libraries it needs in its own header, in scala-cli's
syntax, instead of taking them from `-classpath`:

```onion
//> using dep "org.apache.poi:poi-ooxml:5.5.1"

import { org.apache.poi.xssf.usermodel.XSSFWorkbook }

tool book(dst: String): Int requires { write(dst), unknown } {
  val wb = new XSSFWorkbook()
  wb.createSheet("report").createRow(0).createCell(0).setCellValue("hello")
  val bytes = new java.io.ByteArrayOutputStream()
  wb.write(bytes)
  wb.close()
  Files::writeBytes(dst, bytes.toByteArray())   // the `write(dst)` the tool declares
  return 0
}
```

```bash
onion book.on report.xlsx --plan
```

A library that is not on Maven Central needs its repository too:

```onion
//> using repository "https://nexus.example.com/repository/maven-public"   // optional
//> using dep "com.example.internal:ledger:2.3.0"
```

- `using dep` takes one or more `"group:artifact:version"` coordinates, and
  `using repository` one or more absolute `http`, `https` or `file` URLs. Values may be
  quoted or bare, and a trailing `// comment` is allowed. `deps` and `repositories` are
  accepted as the plural spellings.
- Repositories are searched **before** Maven Central, in the order written, exactly as a
  project's `[[repositories]]`. Resolution is the same coursier resolver a project uses.
- The resolved jars, transitives included, are on the classpath for compiling and for
  running, including with `--effects`, a tool's `--plan`/`--help`, `--watch`, and
  `ONION_DAEMON=1` (the daemon compiles with the same jars).
- Directives are read only from the **leading comment block**: an optional `#!` line, then
  blank lines and comments, before any code. A `//>` line there must be a well-formed
  directive, and a `//> using` line after code is an error, not an ignored comment. A
  malformed directive names the script, line and column and stops the run before
  compiling.
- Versions must be exact: a range (`[1.0,2.0)`), `latest.*`, `LATEST`, `RELEASE` or `1.+`
  is rejected, as is the same module at two versions. This is the rule `onion.toml`'s
  `[dependencies]` applies, from the same code, so a coordinate a project accepts a script
  accepts too. Scala's `group::artifact` form is not supported; name the full artifact.
- Download progress is printed to stderr.
- `onionc` reads the same directives from the files it compiles, through the same parser
  and cache (see [the compiler](compiler.md)).

**A script has no lock file.** The directives pin each direct dependency, but transitive
versions are whatever resolution picks at the time, so two machines can run the same script
against different transitive jars. When that matters, make it a
[project](project-cli.md): `[dependencies]` in `onion.toml` plus a committed `onion.lock`.

Resolving through coursier costs about a second even when everything is already downloaded,
so the resolved classpath is cached per set of directives (a hash of the sorted
dependencies and the ordered repositories) in `script-deps/` under Onion's cache directory,
and reused as long as every jar it lists still exists. The cache directory is
`$ONION_CACHE_DIR` (or `-Donion.cache.dir`) when set, else `%LOCALAPPDATA%\onion\cache` on
Windows, `~/Library/Caches/onion` on macOS and `$XDG_CACHE_HOME/onion` (`~/.cache/onion`)
elsewhere. Deleting it is always safe; the next run resolves afresh.

## Program Arguments

Arguments after the source file(s) are passed to your program:

```bash
onion MyScript.on arg1 arg2 arg3
```

Access them in your code:

```onion
class MyScript {
  public:
    static def main(args :String[]): void {
      foreach arg :String in args {
        println("Argument: " + arg)
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
    static def main(args :String[]): void {
      println("Hello from main method")
    }
}
```

### 2. First Class with Main

If multiple classes have `main` methods, the first one is used:

```onion
class First {
  public:
    static def main(args :String[]): void {
      println("This will run")
    }
}

class Second {
  public:
    static def main(args :String[]): void {
      println("This won't run")
    }
}
```

### 3. Top-Level Declarations and Expressions

If there's no explicit `main` method, the first top-level declaration or expression is the entry point:

```onion
println("Hello, World!")

val x: Int = 10
println("x = " + x)

// These block elements execute immediately
```

## Examples

### Simple Script

**hello.on:**
```onion
println("Hello, World!")
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
    static def main(args :String[]): void {
      if args.length > 0 {
        println("Hello, " + args[0] + "!")
      } else {
        println("Hello, stranger!")
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
println("Sum: " + (a + b))
println("Product: " + (a * b))
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
    static def main(args :String[]): void {
      if args.length == 0 {
        println("Usage: onion count_lines.on <filename>")
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
      println("Lines: " + count)
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
println("Hello from script!")
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
    static def main(args :String[]): void {
      if args.length < 2 {
        println("Error: Missing arguments")
        println("Usage: onion script.on <input> <output>")
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
    static def main(args :String[]): void {
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
