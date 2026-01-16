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

### `-maxErrorReports <count>`

Limit the number of compilation errors reported. Useful for large projects with many errors.

```bash
onionc -maxErrorReports 10 MyProgram.on
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
  -maxErrorReports 20 \
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
    static def main(args :String[]) {
      IO::println("Hello")
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

## Next Steps

- [Script Runner](script-runner.md) - Run Onion scripts directly
- [REPL Shell](repl.md) - Interactive programming
- [Building from Source](../contributing/building.md) - Build the compiler
