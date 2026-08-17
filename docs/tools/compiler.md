# Compiler (onionc)

The `onionc` command compiles Onion source files into JVM class files.

## Usage

```bash
onionc [options] source files...
```

## Options

### `-classpath <classpath>`

Set the classpath for compilation. Used when your code references external Java libraries or other compiled Onion classes.

```bash
onionc -classpath lib/mylib.jar:lib/other.jar MyProgram.on
```

### `-encoding <encoding>`

Specify the character encoding of source files. Default is platform-dependent.

```bash
onionc -encoding UTF-8 MyProgram.on
```

### `-d <output directory>`

Set the output directory for generated class files. If not specified, classes are written to the current directory.

```bash
onionc -d build/classes MyProgram.on
```

Class files are organized by module name:
- Unix-like: `org/onion_lang/MyClass.class`
- Windows: `org\onion_lang\MyClass.class`

### `-maxErrorReport <count>`

Limit the number of compilation errors reported. Useful for large projects with many errors.

```bash
onionc -maxErrorReport 10 MyProgram.on
```

### `-super <super class>`

Specify the class a top-level script's synthesized class extends. Only meaningful when
the source has no explicit class declaration.

```bash
onionc -super java.lang.Object MyScript.on
```

### `--verbose`

Show timing for each compilation phase (parsing, rewriting, type checking, code
generation) as it runs.

```bash
onionc --verbose MyProgram.on
```

### `--dump-ast`

Print the parsed AST to stderr. Useful when debugging syntax and parsing.

```bash
onionc --dump-ast MyProgram.on
```

### `--dump-typed-ast`

Print a typed AST summary (classes, fields, methods) to stderr.

```bash
onionc --dump-typed-ast MyProgram.on
```

### `--profile-compile`

Emit a compile profile with per-phase timing, source count, classpath size, and generated class count.

```bash
onionc --profile-compile MyProgram.on
```

### `--profile-format <text|json>`

Choose how the compile profile is rendered.

```bash
onionc --profile-compile --profile-format json MyProgram.on
```

### `--profile-output <target>`

Write the compile profile to `stderr`, `stdout`, or a file path.

```bash
onionc --profile-compile --profile-format json \
       --profile-output target/profile.json \
       MyProgram.on
```

### `--warn <off|on|error>`

Control warning reporting. `error` treats warnings as compilation errors.

```bash
onionc --warn error MyProgram.on
```

### `--Wno <codes>`

Suppress specific warning categories by code or name.

```bash
onionc --Wno W0001,unused-parameter MyProgram.on
```

Every code accepts either its `W####` form or the `--Wno` name shown below:

| Code | Name | Description |
|------|------|--------------|
| `W0001` | `unused-variable` | Unused variable |
| `W0002` | `unused-import` | Unused import |
| `W0003` | `unreachable-code` | Unreachable code |
| `W0004` | `deprecated`, `deprecated-feature` | Deprecated feature |
| `W0005` | `shadowed-variable` | Shadowed variable |
| `W0006` | `unused-parameter` | Unused parameter |
| `W0007` | `empty-block` | Empty block |
| `W0008` | `redundant-cast` | Redundant cast |
| `W0009` | `possible-null-deref`, `null-deref` | Possible null dereference |
| `W0010` | `unnecessary-conversion` | Unnecessary type conversion |
| `W0011` | `unchecked-cast` | Unchecked cast |
| `W0012` | `null-to-non-nullable` | Null assigned to non-nullable type |
| `W0013` | `suspicious-interpolation` | Suspicious string interpolation syntax |
| `W0014` | `discarded-toplevel` | Top-level statements ignored because a main is defined |
| `W0015` | `platform-unboxing` | Boxed platform value implicitly unboxed to a non-null primitive |

### `--no-check-laws`

Do not execute a record's `law` / `example` clauses.

They run at compile time by default, which means the compiler executes that code.
Turn them off when compiling a file you do not want to run — the trade-off is that
invariants like `parse ∘ format == id` stop being checked.

```bash
onionc --no-check-laws MyProgram.on
```

### `--law-seed <n>` / `--law-samples <n>`

Control how `law` clauses are sampled. A falsified law reports the settings that
produced its counterexample, so the run can be repeated exactly; raising the sample
count widens the search for others.

```bash
onionc --law-samples 500 MyProgram.on
onionc --law-seed 7 MyProgram.on
```

### `--effects`

Print each compiled method's inferred effect set (`read write net exec env clock rand
console unknown`; empty means pure) to stderr.

```bash
onionc --effects MyProgram.on
```

### `-g:none`

Omit the LocalVariableTable from the generated class files.

Without it a JVM debugger can still step through `.on` source — line numbers are always
emitted — but shows nothing for any variable, which is most of the way to useless. The
table is on by default and costs a few bytes per method; this is the way out for anyone
who wants the smaller class file.

```bash
onionc -g:none MyProgram.on
```

Running a script with `onion` always emits the table, since a script is compiled in
memory and there is no artefact to keep small.

## Examples

### Basic Compilation

Compile a single file:

```bash
onionc Hello.on
```

This creates `Hello.class` in the current directory.

### Multiple Files

Compile multiple source files:

```bash
onionc Person.on Employee.on Manager.on
```

### With Output Directory

Organize output:

```bash
onionc -d out/classes src/Main.on src/Utils.on
```

Class files appear in `out/classes/`.

### With Classpath

Reference external libraries:

```bash
onionc -classpath lib/gson-2.8.jar:lib/commons-lang.jar \
       src/JsonParser.on
```

### Complete Example

```bash
onionc \
  -d build/classes \
  -classpath lib/external.jar \
  -encoding UTF-8 \
  -maxErrorReport 20 \
  src/*.on
```

## Running Compiled Programs

After compilation, run with Java:

```bash
# Compile
onionc -d build Main.on

# Run with Java
java -cp build Main
```

Or with a JAR:

```bash
# Compile
onionc -d build Main.on Helper.on

# Create JAR
jar cvfe program.jar Main -C build .

# Run JAR
java -jar program.jar
```

## Module Organization

Onion uses module names (packages) similar to Java:

**MyClass.on:**
```onion
module com.example.myapp

class MyClass {
  public:
    static def main(args :String[]): void {
      println("Hello")
    }
}
```

Compile:
```bash
onionc -d build MyClass.on
```

Output:
```
build/com/example/myapp/MyClass.class
```

Run:
```bash
java -cp build com.example.myapp.MyClass
```

## Compilation Errors

### Common Errors

**Type mismatch:**
```
Error: Type mismatch
  Expected: Int
  Found: String
  at MyProgram.on:10
```

**Undefined variable:**
```
Error: Undefined variable 'count'
  at MyProgram.on:15
```

**Method not found:**
```
Error: Method 'getValue()' not found in class Person
  at MyProgram.on:23
```

## Incremental Compilation

`onionc` compiles all specified files each time. For large projects, consider:

1. Compile only changed files
2. Use a build tool (Make, SBT, Gradle)
3. Organize code into modules

## Build Integration

### Makefile Example

```makefile
SRC_DIR = src
OUT_DIR = build/classes
SOURCES = $(wildcard $(SRC_DIR)/*.on)

all: compile

compile:
	mkdir -p $(OUT_DIR)
	onionc -d $(OUT_DIR) $(SOURCES)

clean:
	rm -rf $(OUT_DIR)

run: compile
	java -cp $(OUT_DIR) Main
```

### Shell Script Example

```bash
#!/bin/bash

SRC_DIR="src"
OUT_DIR="build/classes"
CLASSPATH="lib/*"

mkdir -p "$OUT_DIR"

echo "Compiling Onion sources..."
onionc -d "$OUT_DIR" -classpath "$CLASSPATH" "$SRC_DIR"/*.on

if [ $? -eq 0 ]; then
    echo "Compilation successful"
    echo "Running program..."
    java -cp "$OUT_DIR:$CLASSPATH" Main
else
    echo "Compilation failed"
    exit 1
fi
```

## Compiler Output

### Successful Compilation

No output typically means success:

```bash
$ onionc Hello.on
$ ls
Hello.class  Hello.on
```

### Compilation Errors

Errors are written to standard error:

```bash
$ onionc BadProgram.on
Error: Type mismatch at BadProgram.on:5
Error: Undefined variable at BadProgram.on:10
Compilation failed with 2 errors
```

## Separate Compilation and Linking

Onion units can be compiled independently and linked at load time through the
classpath — you do not need all sources in one `onionc` invocation. Compile a
library, then compile clients against the emitted `.class` files.

Compile the library first:

```onion
// greeter/Greeter.on
class Greeter {
public:
  def this {}
  def greet(name: String): String = "Hello, " + name
}
```

```bash
onionc -d out/lib greeter/Greeter.on
```

Then compile a client against it by putting the library output on the classpath:

```onion
// app/Main.on
class Main {
public:
  static def main(args: String[]): void {
    IO::println((new Greeter()).greet("Onion"))
  }
}
```

```bash
onionc -d out/app -classpath out/lib app/Main.on
```

Linking is the JVM's job — run with every output directory (and `onion.jar` for
the runtime) on the classpath:

```bash
java -cp onion.jar:out/lib:out/app Main   # prints: Hello, Onion
```

Classes, interfaces, records, enums, inheritance, static members **and generic
types** all cross unit boundaries. A generic type keeps its type parameters,
because `onionc` writes JVM generic signatures into the `.class` file:

```bash
onionc -d out/lib Container.on            # class Container[T]
onionc -d out/app -classpath out/lib App.on   # new Container[String](x) resolves
```

Compile units in dependency order (a unit must be compiled after the units it
references). There is no incremental build cache — recompile a unit's dependents
when its public API changes.

## Next Steps

- [Script Runner](script-runner.md) - Run Onion scripts directly
- [REPL Shell](repl.md) - Interactive programming
- [Building from Source](../contributing/building.md) - Build the compiler
