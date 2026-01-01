#!/bin/bash
set -e

# Onion Language Local Installer
# Installs onion and onionc commands to ~/.local/bin

INSTALL_DIR="${ONION_INSTALL_DIR:-$HOME/.local}"
BIN_DIR="$INSTALL_DIR/bin"
LIB_DIR="$INSTALL_DIR/lib/onion"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Onion Language Installer ==="
echo ""
echo "Install location: $INSTALL_DIR"
echo ""

# Check Java
if ! command -v java > /dev/null 2>&1; then
    echo "Error: Java not found. Please install Java 17+."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Found Java version: $JAVA_VERSION"

# Build fat jar
echo ""
echo "Building onion.jar..."
cd "$SCRIPT_DIR"
sbt assembly

JAR_PATH="$SCRIPT_DIR/target/scala-3.3.7/onion.jar"
if [ ! -f "$JAR_PATH" ]; then
    # Try alternative scala version paths
    JAR_PATH=$(find "$SCRIPT_DIR/target" -name "onion.jar" 2>/dev/null | head -1)
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: onion.jar not found after build"
    exit 1
fi

echo "Built: $JAR_PATH"

# Create directories
echo ""
echo "Installing..."
mkdir -p "$BIN_DIR"
mkdir -p "$LIB_DIR"

# Copy jar
cp "$JAR_PATH" "$LIB_DIR/onion.jar"
echo "  $LIB_DIR/onion.jar"

# Create onion script (script runner)
cat > "$BIN_DIR/onion" << 'SCRIPT'
#!/bin/sh
ONION_JAR="$HOME/.local/lib/onion/onion.jar"
if [ -z "$JAVA_HOME" ]; then
    JAVA_CMD="java"
else
    JAVA_CMD="$JAVA_HOME/bin/java"
fi
exec "$JAVA_CMD" -cp "$ONION_JAR" onion.tools.ScriptRunner "$@"
SCRIPT
chmod +x "$BIN_DIR/onion"
echo "  $BIN_DIR/onion"

# Create onionc script (compiler)
cat > "$BIN_DIR/onionc" << 'SCRIPT'
#!/bin/sh
ONION_JAR="$HOME/.local/lib/onion/onion.jar"
if [ -z "$JAVA_HOME" ]; then
    JAVA_CMD="java"
else
    JAVA_CMD="$JAVA_HOME/bin/java"
fi
exec "$JAVA_CMD" -cp "$ONION_JAR" onion.tools.CompilerFrontend "$@"
SCRIPT
chmod +x "$BIN_DIR/onionc"
echo "  $BIN_DIR/onionc"

echo ""
echo "=== Installation Complete ==="
echo ""

# Check PATH
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    echo "Add this to your ~/.bashrc or ~/.zshrc:"
    echo ""
    echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
    echo ""
fi

echo "Usage:"
echo "  onion script.on      # Run a script"
echo "  onionc -d out src/   # Compile to .class files"
echo ""
