# Building from Source

Complete guide to building the Onion compiler from source.

## Prerequisites

### Required Software

1. **Java Development Kit (JDK) 17 or later**

   Check version:
   ```bash
   java -version
   javac -version
   ```

   Install:
   - **macOS**: `brew install openjdk@17`
   - **Ubuntu/Debian**: `sudo apt install openjdk-17-jdk`
   - **Windows**: Download from [Adoptium](https://adoptium.net/)

2. **SBT (Scala Build Tool)**

   Check version:
   ```bash
   sbt version
   ```

   Install:
   - **macOS**: `brew install sbt`
   - **Ubuntu/Debian**: Follow [official guide](https://www.scala-sbt.org/download.html)
   - **Windows**: Download MSI installer

3. **Git**

   ```bash
   git --version
   ```

## Cloning the Repository

```bash
git clone https://github.com/onion-lang/onion.git
cd onion
```

## Building

### Basic Compilation

Compile the entire project:

```bash
sbt compile
```

This will:
1. Download dependencies (first time only)
2. Generate parser from JavaCC grammar
3. Compile Scala and Java source files

Expected output:
```
[info] compiling 70 Scala sources and 15 Java sources to target/scala-3.6.2/classes ...
[success] Total time: 45 s
```

### Clean Build

Remove all generated files and rebuild:

```bash
sbt clean compile
```

### Incremental Compilation

SBT automatically performs incremental compilation. Only changed files are recompiled:

```bash
# Edit a file
vim src/main/scala/onion/compiler/Typing.scala

# Fast recompilation
sbt compile
```

## Testing

### Run All Tests

```bash
sbt test
```

### Run Specific Test Suite

```bash
sbt 'testOnly *HelloWorldSpec'
sbt 'testOnly *FactorialSpec'
```

### Run Tests Matching Pattern

```bash
sbt 'testOnly *String*'
```

## Creating Distributions

### Standalone JAR

Create a fat JAR with all dependencies:

```bash
sbt assembly
```

Output: `target/scala-3.6.2/onion.jar`

Run it:
```bash
java -jar target/scala-3.6.2/onion.jar Hello.on
```

### Distribution Package

Create a complete distribution ZIP:

```bash
sbt dist
```

Output: `target/onion-dist.zip`

Contents:
```
onion-dist/
├── onion.jar          # Main compiler JAR
├── lib/               # Dependencies
│   ├── asm-9.8.jar
│   ├── scala-library-3.6.2.jar
│   └── ...
├── bin/               # Executables
│   ├── onionc
│   └── onion
├── run/               # Example programs
│   ├── Hello.on
│   ├── Array.on
│   └── ...
└── README.md
```

Extract and use:
```bash
unzip target/onion-dist.zip
cd onion-dist
export PATH=$PATH:$PWD/bin
onionc run/Hello.on
```

## Development Build

### Continuous Compilation

Watch for changes and recompile automatically:

```bash
sbt ~compile
```

### Run from SBT

Execute without creating a JAR:

```bash
sbt 'run Hello.on'
```

Run script runner:

```bash
sbt 'runScript run/Hello.on'
```

### Interactive SBT

Start SBT shell:

```bash
sbt
```

Then run commands:
```
> compile
> test
> run Hello.on
> ~compile  # Continuous compilation
> exit
```

## Parser Development

### Modifying the Grammar

1. Edit the JavaCC grammar:
   ```bash
   vim grammar/JJOnionParser.jj
   ```

2. Regenerate parser:
   ```bash
   sbt clean compile
   ```

The parser is automatically regenerated when:
- Grammar file is newer than generated parser
- Running `sbt clean`

### Parser Location

Generated parser:
```
target/scala-3.6.2/src_managed/main/java/onion/compiler/parser/
├── JJOnionParser.java
├── Token.java
├── TokenManager.java
└── ...
```

## IDE Setup

### IntelliJ IDEA

1. Install Scala plugin
2. Open project: `File > Open > select build.sbt`
3. Wait for indexing to complete
4. Build: `Build > Build Project`

Run configuration:
- Main class: `onion.tools.CompilerFrontend`
- Program arguments: `path/to/source.on`
- Working directory: `$PROJECT_DIR$`

### Visual Studio Code

1. Install Metals extension
2. Open project folder
3. Wait for import to complete
4. Build via command palette: `Metals: Compile workspace`

## Troubleshooting

### Parser Generation Fails

```bash
# Clean and rebuild
sbt clean
rm -rf target
sbt compile
```

### Out of Memory

Increase SBT memory:
```bash
export SBT_OPTS="-Xmx2G"
sbt compile
```

Or edit `.sbtopts`:
```
-Xmx2G
-Xss2M
```

### Dependency Issues

Clear Ivy cache:
```bash
rm -rf ~/.ivy2/cache
sbt update
sbt compile
```

### Compilation Errors

Check Scala and Java versions:
```bash
sbt scalaVersion
java -version
```

Ensure they match requirements:
- Scala 3.6.2
- Java 17+

## Build Configuration

### build.sbt

Key settings:

```scala
version := "0.2.0-SNAPSHOT"
scalaVersion := "3.6.2"
name := "onion"
organization := "org.onion_lang"

// Dependencies
libraryDependencies ++= Seq(
  "org.ow2.asm" % "asm" % "9.8",
  "net.java.dev.javacc" % "javacc" % "5.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test"
)

// Main class
mainClass := Some("onion.tools.CompilerFrontend")
```

### Compiler Options

Scala compiler options:
```scala
scalacOptions ++= Seq(
  "-encoding", "utf8",
  "-unchecked",
  "-deprecation",
  "-feature"
)
```

Java compiler options:
```scala
javacOptions ++= Seq(
  "-source", "17",
  "-Xlint:unchecked"
)
```

## Performance Tips

### Faster Builds

1. Use incremental compilation (default)
2. Don't clean unless necessary
3. Increase JVM memory
4. Use SBT shell for multiple commands

### Parallel Compilation

SBT compiles in parallel by default. Adjust thread count:

```scala
Global / concurrentRestrictions := Seq(
  Tags.limitAll(4)
)
```

## Platform-Specific Notes

### macOS

May need to set JAVA_HOME:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Linux

Ensure sufficient memory:
```bash
free -h
# Increase swap if needed
```

### Windows

Use PowerShell or Git Bash. Paths use backslashes:
```powershell
sbt compile
java -jar target\scala-3.6.2\onion.jar Hello.on
```

## Next Steps

- [Development Guide](development.md) - Contributing to Onion
- [Compiler Architecture](../reference/compiler-architecture.md) - Internals
- [Running Tests](development.md#testing-strategy) - Testing guide
