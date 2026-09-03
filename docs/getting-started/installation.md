# Installation

This guide will help you install and set up the Onion programming language on your system.

## Prerequisites

Onion runs on the JVM and requires:

- **Java Development Kit (JDK) 17 or later**
- **SBT (Scala Build Tool)** - for building from source

## Installation Methods

### Method 1: Download Pre-built Binary (Recommended)

1. Download the latest release from the [GitHub Releases page](https://github.com/onion-lang/onion/releases)
2. Extract the archive:
   ```bash
   unzip onion-dist.zip
   cd onion-dist
   ```
3. Add the `bin` directory to your PATH:
   ```bash
   export PATH=$PATH:/path/to/onion-dist/bin
   ```

### Method 2: Build from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/onion-lang/onion.git
   cd onion
   ```

2. Build the project with SBT:
   ```bash
   sbt compile
   ```

3. Create the distribution package:
   ```bash
   sbt dist
   ```

   This creates a distribution ZIP in `target/onion-dist.zip`

4. Or build a standalone JAR:
   ```bash
   sbt assembly
   ```

   This creates `onion.jar` under the active Scala target directory (currently `target/scala-3.3.7/`)

5. Run the local installer if you want shell commands in `~/.local/bin`:
   ```bash
   ./install.sh
   ```

## Verify Installation

Check that Onion is installed correctly:

```bash
# If using the distribution
onionc --help
onion repl
onion-repl

# If using the JAR directly
java -jar onion.jar --help
```

## Startup Time and JVM Flags

Onion starts a JVM per invocation, and loading the compiler accounts for most of that.
Sharing those classes through an [AppCDS](https://openjdk.org/jeps/350) archive roughly
halves it — measured on JDK 25, `onion run/Hello.on` goes from 0.73s to 0.38s.

The `curl | sh` installer builds the archive for you. If you installed from the
distribution zip, build it once after unpacking:

```bash
ONION_GENERATE_CDS=1 onion run/Hello.on
```

That writes `lib/onion.jsa` (about 15 MB) next to `onion.jar`. Generation goes through the
launcher on purpose: an archive is only usable when the classpath matches the one it was
built with. Rebuild it after upgrading Onion or switching JDKs — until you do, the JVM
quietly refuses the stale archive and runs normally, just without the speedup.

Extra JVM flags go in `ONION_JAVA_OPTS`:

```bash
ONION_JAVA_OPTS="-Xmx4g" onion big-job.on
ONION_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" onion script.on
```

`ONION_DEBUG_STARTUP=1` stops the launcher silencing the JVM's class-sharing messages,
which is how to find out why an archive is being ignored.

### The compile daemon

Every `onionc` run starts a JVM, loads the compiler and compiles it just-in-time before it
gets to your file. With `ONION_DAEMON=1`, `onionc` instead hands the command line to a
resident compiler process — started on first use, one per user, JDK and Onion
installation — and relays its output and exit code. The daemon keeps the warmed-up
compiler between runs, so a typical single-file compile takes a fraction of the time:

```bash
export ONION_DAEMON=1
onionc Hello.on            # starts the daemon the first time, then reuses it
```

The daemon stops itself after 30 minutes without work; `java -cp onion.jar
onion.tools.daemon.DaemonClient stop` (or `status`) controls it by hand. It listens on a
Unix domain socket in a directory only you can read (`$XDG_RUNTIME_DIR` or the temp
directory; override with `ONION_DAEMON_SOCKET`), needs Java 16 or later for that, and
whenever it cannot be reached or started `onionc` simply compiles in-process as before.
`ONION_DAEMON_JAVA_OPTS` adds JVM flags to the daemon itself. `onion script.on` uses it
too: the daemon compiles and hands the classes back, and the program itself runs in your
own process, as always.

## IDE Setup

### Visual Studio Code

While there's no official Onion extension yet, you can use:

- Generic syntax highlighting for similar languages
- Java/Scala extensions for dependency management

### IntelliJ IDEA

For developing the Onion compiler itself:

1. Install the Scala plugin
2. Import the project as an SBT project
3. The IDE will automatically download dependencies

## Next Steps

- [Hello World Tutorial](hello-world.md) - Write your first Onion program
- [Quick Start Guide](quick-start.md) - Learn the essential features
