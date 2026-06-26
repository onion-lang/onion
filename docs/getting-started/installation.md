# Installation

This guide will help you install and set up the Onion programming language on your system.

## Prerequisites

Onion runs on the JVM and requires:

- **Java Development Kit (JDK) 17 or later**

## Quick Install (Recommended)

Run the installer script. It downloads the latest `onion.jar` from GitHub Releases and installs the `onion`, `onionc`, and `onion-repl` commands into `~/.local/bin`.

```bash
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh
```

Make sure `~/.local/bin` is on your PATH:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

## Verify Installation

Check that Onion is installed correctly:

```bash
onionc --help
onion repl
onion-repl
```

## Other Installation Methods

### Install a Specific Version

```bash
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh -s -- --version=v0.2.0-M14
```

### Build from a Source Checkout

Requires **SBT (Scala Build Tool)**.

```bash
git clone https://github.com/onion-lang/onion && cd onion && ./install.sh --from-source
```

### Install to a Custom Location

The default install prefix is `~/.local`. Use `ONION_INSTALL_DIR` to change it:

```bash
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | ONION_INSTALL_DIR=/opt/onion sh
```

### Download a Pre-built Distribution

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
