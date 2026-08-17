# Shared JVM setup for the Onion launchers. Sourced, never executed.
#
# Expects ONION_HOME to be set. Leaves the flags to pass before -classpath in
# ONION_JVM_ARGS; the user's own flags stay in ONION_JAVA_OPTS.
#
# This exists because there are two launcher implementations — these scripts, which ship
# in the distribution zip and are what a source checkout runs, and the ones `install.sh`
# writes for a `curl | sh` install — and they had silently diverged. The installed
# launchers had class-data sharing and the `sun.misc.Unsafe` workaround; these had
# neither, so the same program started in half the time depending on how you got Onion.
# `LauncherParitySpec` now fails the build if the two drift apart again.
#
# Environment:
#   ONION_JAVA_OPTS     extra JVM flags (heap, an agent, -XX:TieredStopAtLevel=1, …).
#                       There was previously no way to set any of these short of
#                       hand-writing a `java -cp` command.
#   ONION_CDS_ARCHIVE   override the archive location.
#   ONION_GENERATE_CDS  write the archive instead of using it.
#   ONION_DEBUG_STARTUP show what the JVM makes of the archive rather than silencing it.

ONION_JVM_ARGS=""

# JDK 24 turned the `sun.misc.Unsafe` memory-access methods into a terminal deprecation
# warning, and Scala 3.3's `LazyVals` calls one — so every single run printed four lines
# of warnings to stderr, which lands in anything capturing a tool's output. The option
# does not exist before JDK 23, and an unknown `--` option makes the JVM refuse to start,
# so the version is read from the JDK's own `release` file rather than probed by running
# java (which would cost a process launch on every invocation).
onion_java_home() {
    if [ -n "$JAVA_HOME" ]; then
        printf '%s' "$JAVA_HOME"
        return
    fi
    _onion_java=$(command -v java 2>/dev/null) || return
    # `readlink -f` is not POSIX, but every platform that has these launchers has it.
    _onion_real=$(readlink -f "$_onion_java" 2>/dev/null) || return
    printf '%s' "$(dirname "$(dirname "$_onion_real")")"
}

_onion_release="$(onion_java_home)/release"
if [ -r "$_onion_release" ]; then
    _onion_major=$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$_onion_release" 2>/dev/null)
    if [ -n "$_onion_major" ] && [ "$_onion_major" -ge 23 ] 2>/dev/null; then
        ONION_JVM_ARGS="--sun-misc-unsafe-memory-access=allow"
    fi
fi

# Application class-data sharing. Starting `onion` on a trivial script loads about 2650
# classes; sharing them costs ~15 MB on disk and cuts the run roughly in half (measured
# on JDK 25: 0.68s -> 0.35s for `onion run/Hello.on`).
#
# Generation goes back through the launcher rather than being scripted separately,
# because an archive is only usable when the classpath string matches the one it was
# built with — reconstructing that classpath somewhere else is exactly the duplication
# that stops matching later.
ONION_CDS_ARCHIVE="${ONION_CDS_ARCHIVE:-$ONION_HOME/lib/onion.jsa}"

if [ -n "$ONION_GENERATE_CDS" ]; then
    ONION_JVM_ARGS="$ONION_JVM_ARGS -XX:ArchiveClassesAtExit=$ONION_CDS_ARCHIVE"
elif [ -f "$ONION_CDS_ARCHIVE" ]; then
    # -Xshare:auto is the default, but say it: with it, an archive built by a different
    # JDK or for an install that has since moved is refused and the JVM carries on
    # normally. Correct — but it narrates the refusal on stderr every run. Silence that
    # unless someone is deliberately looking.
    ONION_JVM_ARGS="$ONION_JVM_ARGS -XX:SharedArchiveFile=$ONION_CDS_ARCHIVE -Xshare:auto"
    if [ -z "$ONION_DEBUG_STARTUP" ]; then
        ONION_JVM_ARGS="$ONION_JVM_ARGS -Xlog:cds=off -Xlog:cds+dynamic=off"
    fi
fi
