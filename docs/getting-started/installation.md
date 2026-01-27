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

   This creates `onion.jar` in the `target/scala-3.3.7/` directory

## Verify Installation

Check that Onion is installed correctly:

```bash
# If using the distribution
onionc --help

# If using the JAR directly
java -jar onion.jar --help
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
