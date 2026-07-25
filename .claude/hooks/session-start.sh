#!/bin/bash
# SessionStart hook for Claude Code on the web.
#
# Ensures `sbt` (required by CLAUDE.md's build/test commands) is usable in a
# freshly cloned container:
#
#  1. Origin carries remote-tracking branches whose names contain byte
#     sequences that are not valid in this container's ASCII (POSIX/C)
#     locale (e.g. `codex/bcel...asm...` with mojibake Japanese text). sbt's
#     scalafix plugin scans all refs at project-load time via jgit, and
#     `File.toPath()` throws `InvalidPathException` on those names, which
#     hangs `sbt` at a "Project loading failed: (r)etry..." prompt -- so
#     *every* sbt invocation fails before compiling or testing anything.
#     Switching to the `C.utf8` locale makes those byte sequences valid
#     path characters again, so the ref scan succeeds.
#  2. `sbt` itself is not preinstalled in this container. Download the
#     launcher matching project/build.properties if it's missing.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

if locale -a 2>/dev/null | grep -qi '^C\.utf8$'; then
  echo 'export LC_ALL=C.utf8' >> "$CLAUDE_ENV_FILE"
  echo 'export LANG=C.utf8' >> "$CLAUDE_ENV_FILE"
elif locale -a 2>/dev/null | grep -qi '^C\.UTF-8$'; then
  echo 'export LC_ALL=C.UTF-8' >> "$CLAUDE_ENV_FILE"
  echo 'export LANG=C.UTF-8' >> "$CLAUDE_ENV_FILE"
fi

SBT_HOME="$HOME/.local/sbt-launcher"

if ! command -v sbt >/dev/null 2>&1 && [ ! -x "$SBT_HOME/bin/sbt" ]; then
  SBT_VERSION="$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' "$CLAUDE_PROJECT_DIR/project/build.properties" | head -1)"
  mkdir -p "$SBT_HOME"
  curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" \
    | tar xz -C "$SBT_HOME" --strip-components=1
fi

if [ -x "$SBT_HOME/bin/sbt" ] && ! command -v sbt >/dev/null 2>&1; then
  echo "export PATH=\"$SBT_HOME/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi

# Warm the build (dependency resolution + compile) so later `sbt compile`/
# `sbt test` invocations in the session don't pay the cold-cache cost.
cd "$CLAUDE_PROJECT_DIR"
PATH="$SBT_HOME/bin:$PATH" LC_ALL=C.utf8 LANG=C.utf8 SBT_OPTS="-Xmx4G -XX:+UseG1GC -Xss16m" sbt compile
