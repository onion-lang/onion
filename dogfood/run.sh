#!/usr/bin/env bash
# Build and test every dogfood project with the compiler and battery from this checkout.
#
#   dogfood/run.sh                  build onion and onion-llm with sbt, then build and test all
#   dogfood/run.sh gh-digest ...    only the named projects
#   dogfood/run.sh --prebuilt ...   skip sbt: the fat jar is built; a project's onion-llm comes
#                                   from ~/.ivy2/local if `llm/publishLocal` ran, else from
#                                   Maven Central at the jar's own version
#
# ONION_JAR picks the compiler jar (default: the newest `sbt assembly` output) and SBT the
# sbt launcher (default: sbt). Exits non-zero when any project fails to build or test.
set -u

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
battery_version=0.0.0-dogfood

prebuilt=0
if [ "${1:-}" = "--prebuilt" ]; then
  prebuilt=1
  shift
fi

# Nothing here may reach a real service. Drop credentials, and point every API base URL
# at a port that refuses connections, so even a mistaken live call fails at once.
unset ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN GITHUB_TOKEN GH_TOKEN SLACK_WEBHOOK_URL
export ANTHROPIC_BASE_URL=http://127.0.0.1:9
export GITHUB_API_URL=http://127.0.0.1:9

if [ "$prebuilt" = 0 ]; then
  # onion-llm goes to ~/.ivy2/local at a fixed version, which meeting-summary depends on.
  (cd "$root" && ${SBT:-sbt} "assembly; set llm / version := \"$battery_version\"; llm/publishLocal") || exit 1
fi

jar=${ONION_JAR:-$(ls -t "$root"/target/out/jvm/scala-*/onion/onion-[0-9]*.jar 2>/dev/null | head -n 1)}
if [ -z "$jar" ] || [ ! -f "$jar" ]; then
  echo "dogfood: no onion fat jar; run \`sbt assembly\` or set ONION_JAR" >&2
  exit 1
fi
echo "dogfood: using $jar"

onion() { java -cp "$jar" onion.tools.OnionCli "$@"; }

if [ "$#" -gt 0 ]; then
  projects="$*"
else
  projects=$(cd "$here" && for d in */onion.toml; do dirname "$d"; done)
fi

failed=""
# The battery version for --prebuilt: the jar's own (onion-0.138.0.jar -> 0.138.0); a
# development jar (onion-0.0.0+260-...) has no release, so use the latest published one.
# LLM_VERSION overrides both.
jar_version=${LLM_VERSION:-$(basename "$jar" .jar | sed 's/^onion-//')}
if ! printf '%s' "$jar_version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  jar_version=$(curl -fsS https://repo1.maven.org/maven2/org/onion-lang/onion-llm/maven-metadata.xml 2>/dev/null \
    | sed -n 's|.*<release>\(.*\)</release>.*|\1|p')
fi
local_battery=0
ls -d "${HOME:-/nonexistent}"/.ivy2/local/org.onion-lang/onion-llm*/"$battery_version" >/dev/null 2>&1 && local_battery=1

workdir=""
trap '[ -n "$workdir" ] && rm -rf "$workdir"' EXIT

for p in $projects; do
  echo "== dogfood/$p"
  dir="$here/$p"
  # --prebuilt without a locally published battery: build a copy of the project that asks
  # Maven Central for the battery at the jar's version instead of the unpublished one.
  if [ "$prebuilt" = 1 ] && [ "$local_battery" = 0 ] && grep -q "$battery_version" "$dir/onion.toml" 2>/dev/null; then
    [ -n "$workdir" ] || workdir=$(mktemp -d)
    cp -R "$dir" "$workdir/$p" && sed -i "s/$battery_version/$jar_version/" "$workdir/$p/onion.toml" && rm -f "$workdir/$p/onion.lock"
    dir="$workdir/$p"
    echo "dogfood: $p uses onion-llm $jar_version from Maven Central"
  fi
  (
    cd "$dir" || exit 1
    # A lock that pins the locally published battery goes stale whenever the battery
    # changes, and then fails as "different bytes"; such locks are not committed.
    if [ -f onion.lock ] && grep -q "$battery_version" onion.lock; then rm -f onion.lock; fi
    onion build && onion test --report-xml target/test-reports/junit.xml
  ) || failed="$failed $p"
done

if [ -n "$failed" ]; then
  echo "dogfood: FAILED:$failed" >&2
  exit 1
fi
echo "dogfood: all passed"
