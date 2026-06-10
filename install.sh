#!/bin/sh
# Onion Language Installer
#
# Quick install (downloads the latest release):
#   curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh
#
# Install a specific version:
#   curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh -s -- --version=v0.2.0-M2
#
# Build from a source checkout instead of downloading:
#   ./install.sh --from-source
#
# Environment variables:
#   ONION_INSTALL_DIR  Install prefix (default: ~/.local)
#   ONION_VERSION      Release tag to install (default: latest)
set -e

REPO="onion-lang/onion"
INSTALL_DIR="${ONION_INSTALL_DIR:-$HOME/.local}"
VERSION="${ONION_VERSION:-latest}"
FROM_SOURCE=0

for arg in "$@"; do
  case "$arg" in
    --from-source) FROM_SOURCE=1 ;;
    --version=*) VERSION="${arg#--version=}" ;;
    -h|--help)
      sed -n '2,16p' "$0" 2>/dev/null || echo "see header of install.sh"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

BIN_DIR="$INSTALL_DIR/bin"
LIB_DIR="$INSTALL_DIR/lib/onion"

echo "=== Onion Language Installer ==="
echo ""
echo "Install location: $INSTALL_DIR"

# ---- Check Java ----
if ! command -v java >/dev/null 2>&1; then
  echo "Error: Java not found. Please install Java 17+." >&2
  exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
case "$JAVA_VERSION" in
  ''|*[!0-9]*)
    echo "Warning: could not determine Java version; continuing." ;;
  *)
    if [ "$JAVA_VERSION" -lt 17 ]; then
      echo "Error: Java 17+ is required (found $JAVA_VERSION)." >&2
      exit 1
    fi
    echo "Found Java version: $JAVA_VERSION" ;;
esac

mkdir -p "$BIN_DIR" "$LIB_DIR"

# ---- Obtain onion.jar ----
if [ "$FROM_SOURCE" = "1" ]; then
  # Find the checkout: the directory this script lives in, or the current directory.
  SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" 2>/dev/null && pwd)
  if [ -f "$SCRIPT_DIR/build.sbt" ]; then
    SRC_DIR="$SCRIPT_DIR"
  elif [ -f "./build.sbt" ]; then
    SRC_DIR="$(pwd)"
  else
    echo "Error: --from-source requires running inside an onion checkout." >&2
    echo "  git clone https://github.com/$REPO && cd onion && ./install.sh --from-source" >&2
    exit 1
  fi
  echo ""
  echo "Building onion.jar from source ($SRC_DIR)..."
  (cd "$SRC_DIR" && sbt assembly)
  JAR_PATH=$(find "$SRC_DIR/target" -name "onion.jar" -path "*scala-*" -not -path "*/dist/*" 2>/dev/null | sort | head -1)
  if [ ! -f "$JAR_PATH" ]; then
    echo "Error: onion.jar not found after build" >&2
    exit 1
  fi
  cp "$JAR_PATH" "$LIB_DIR/onion.jar"
else
  fetch() { # fetch URL [output]
    if command -v curl >/dev/null 2>&1; then
      if [ -n "$2" ]; then curl -fL --progress-bar -o "$2" "$1"; else curl -fsSL "$1"; fi
    elif command -v wget >/dev/null 2>&1; then
      if [ -n "$2" ]; then wget -q --show-progress -O "$2" "$1"; else wget -qO- "$1"; fi
    else
      echo "Error: curl or wget is required." >&2
      exit 1
    fi
  }

  if [ "$VERSION" = "latest" ]; then
    # 'releases/latest' ignores prereleases, so resolve the newest release
    # (prereleases included) through the API instead.
    VERSION=$(fetch "https://api.github.com/repos/$REPO/releases?per_page=1" \
      | grep '"tag_name"' | head -1 | sed 's/.*"tag_name"[^"]*"\([^"]*\)".*/\1/')
    if [ -z "$VERSION" ]; then
      echo "Error: could not determine the latest release of $REPO." >&2
      exit 1
    fi
    echo ""
    echo "Latest release: $VERSION"
  fi
  URL="https://github.com/$REPO/releases/download/$VERSION/onion.jar"
  echo ""
  echo "Downloading $URL ..."
  fetch "$URL" "$LIB_DIR/onion.jar.tmp"
  mv "$LIB_DIR/onion.jar.tmp" "$LIB_DIR/onion.jar"
fi
echo "  $LIB_DIR/onion.jar"

# ---- Extra JVM flags (suppress the harmless sun.misc.Unsafe warning on new JDKs) ----
JVM_FLAGS=""
if java --sun-misc-unsafe-memory-access=allow -version >/dev/null 2>&1; then
  JVM_FLAGS="--sun-misc-unsafe-memory-access=allow"
fi

# ---- Write launchers ----
write_launcher() {
  name="$1"
  main_class="$2"
  extra="$3"
  cat > "$BIN_DIR/$name" <<LAUNCHER
#!/bin/sh
ONION_JAR="$LIB_DIR/onion.jar"
if [ -z "\$JAVA_HOME" ]; then
    JAVA_CMD="java"
else
    JAVA_CMD="\$JAVA_HOME/bin/java"
fi
$extra
exec "\$JAVA_CMD" $JVM_FLAGS -cp "\$ONION_JAR\${CLASSPATH:+:\$CLASSPATH}" $main_class "\$@"
LAUNCHER
  chmod +x "$BIN_DIR/$name"
  echo "  $BIN_DIR/$name"
}

write_launcher onion onion.tools.ScriptRunner 'if [ "$1" = "repl" ]; then
    shift
    exec "$JAVA_CMD" '"$JVM_FLAGS"' -cp "$ONION_JAR${CLASSPATH:+:$CLASSPATH}" onion.tools.Repl "$@"
fi'
write_launcher onionc onion.tools.CompilerFrontend ""
write_launcher onion-repl onion.tools.Repl ""

echo ""
echo "=== Installation Complete ==="
echo ""

case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *)
    echo "Add this to your ~/.bashrc or ~/.zshrc:"
    echo ""
    echo "  export PATH=\"$BIN_DIR:\$PATH\""
    echo ""
    ;;
esac

echo "Usage:"
echo "  onion script.on      # Run a script"
echo "  onion repl           # Start the REPL"
echo "  onionc -d out src/   # Compile to .class files"
echo ""
echo "Shebang scripts work too:"
echo "  #!/usr/bin/env onion"
