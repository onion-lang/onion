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

### Console encoding (Windows)

On Windows, `onion`, `onionc`, `onion repl` and the project commands write their output and
diagnostics in UTF-8 when the output is not a Windows console: a pipe, a file, or a terminal
such as Git Bash (mintty). A real console (cmd.exe, PowerShell, Windows Terminal, the VS Code
terminal) keeps its own code page, which it already displays correctly. Each of stdout and
stderr is judged separately, so `onion script.on > out.txt` writes UTF-8 to the file while
diagnostics on the console stay readable. Other platforms are unchanged. Source files are
read as UTF-8 unless `-encoding` says otherwise.

Standard input follows the same rule: `IO::readLine` and the other `IO` readers decode a pipe
or a file as UTF-8 (`echo 日本語 | onion script.on`), and a Windows console in its own code
page, on JDK 17 as on JDK 18+. Other platforms read stdin as UTF-8, as JDK 18+ already did.
`onion.Files` and `file"…"` always read and write text as UTF-8, whatever the platform charset;
`Files::readText`/`writeText` take a `java.nio.charset.Charset` for anything else.

`ONION_CONSOLE_ENCODING` overrides the choice:

```bash
ONION_CONSOLE_ENCODING=utf-8 onion script.on   # always UTF-8, consoles included (any OS)
ONION_CONSOLE_ENCODING=native onion script.on  # leave it to the JVM, as before
```

Unset, or `auto`, is the behaviour described above. To pin some other encoding, combine
`native` with the JVM's own flag in `ONION_JAVA_OPTS`: `-Dstdout.encoding=...` and
`-Dstderr.encoding=...` on JDK 19+, `-Dsun.stdout.encoding=...` and
`-Dsun.stderr.encoding=...` on JDK 17 and 18. Standard input is pinned with
`-Donion.stdin.encoding=...`, which the launchers leave as given; with `native`, stdin is
decoded in the JVM's default charset, as before.

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
