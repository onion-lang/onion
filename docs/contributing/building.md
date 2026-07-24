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
[info] compiling ... Scala sources and ... Java sources to target/scala-3.3.7/classes ...
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

Output: `target/scala-3.3.7/onion.jar`

The JAR's default main class is the compiler (`onionc`), so `java -jar` **compiles**
a source file to `.class` files:

```bash
java -jar target/scala-3.3.7/onion.jar Hello.on   # compiles Hello.on
```

To **run** a script instead, invoke the `ScriptRunner` main class (this is what the
`onion` launcher does):

```bash
java -cp target/scala-3.3.7/onion.jar onion.tools.ScriptRunner Hello.on
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
│   ├── scala-library-3.3.7.jar
│   └── ...
├── bin/               # Executables
│   ├── onionc
│   ├── onion
│   └── onion-repl
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

Run the promoted REPL:

```bash
sbt repl
```

Run the readiness benchmark suite:

```bash
sbt benchmark
```

The default suite reports six explicit protocols:

- steady-state fresh compiler measurements for `run/Hello.on`,
  `run/TodoManager.on`, and `run/StatsApp.on`;
- a process-cold `onion run/Hello.on` measurement that includes child-JVM
  startup and shutdown;
- submissions to one persistent, growing Onion REPL session; and
- one compilation of the deterministic 20-file automation fixture under
  `benchmarks/fixtures/automation-project/`.

Process-cold uses 3 warmups by default; the other protocols use 8. Every
protocol uses 25 measured iterations and a 30-second iteration timeout.
`--warmups N` overrides the scenario defaults. The schema-v3 JSON report stores
the effective configuration beside every scenario so unlike lifecycles are
never presented as identical measurements. It also retains raw nanosecond
observations, median and p95 latency, phase timings where available, source
metrics, JVM/OS metadata, assigned memory, and typed absolute-policy checks.

The first practical milestone uses these inclusive latency ceilings:

| Protocol | Median | p95 |
|---|---:|---:|
| Fresh `onion Hello.on` process | 1.5 s | 2.5 s |
| Steady-state compile of `Hello.on` | 150 ms | 300 ms |
| Steady-state compile of `StatsApp.on` | 750 ms | 1.2 s |
| Subsequent REPL snippet | 100 ms | 250 ms |
| 20-file/~2,000-line project | 2.0 s | 3.0 s |

The absolute policy is enforced only when the captured environment exactly
matches the reference lane: Ubuntu 24.04 x86-64, Eclipse Adoptium Temurin
JDK 21, two assigned processors, 4 GiB assigned memory, a 2 GiB maximum heap,
and G1. A breach on that lane fails the benchmark task. On every other
machine, the checks are `not-applicable` and the overall policy status is
`informational`; those measurements are useful for profiling but do not count
as release evidence.

The machine-readable report is written to
`target/readiness/benchmark-v3.json`. For a quick protocol smoke test:

```bash
sbt 'benchmark --warmups 0 --iterations 1'
```

`sbt bench` remains a compatibility alias and accepts the same options.

Emit compile profiles:

```bash
sbt 'runMain onion.tools.CompilerFrontend --profile-compile run/Hello.on'
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
target/scala-3.3.7/src_managed/main/java/onion/compiler/parser/
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
- Scala 3.3.7
- Java 17+

## Build Configuration

### build.sbt

Key settings:

```scala
// Version is derived from git tags via sbt-dynver
scalaVersion := "3.3.7"
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
sbt assembly
# compile a source file:
java -jar target\scala-3.3.7\onion.jar Hello.on
# or run a script:
java -cp target\scala-3.3.7\onion.jar onion.tools.ScriptRunner Hello.on
```

## Next Steps

- [Development Guide](development.md) - Contributing to Onion
- [Compiler Architecture](../reference/compiler-architecture.md) - Internals
- [Running Tests](development.md#testing-strategy) - Testing guide
