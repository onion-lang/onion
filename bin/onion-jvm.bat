@echo off
rem Shared JVM setup for the Onion launchers. Called by them, never run directly.
rem
rem Expects ONION_HOME. Leaves the flags to pass before -classpath in ONION_JVM_ARGS;
rem the user's own flags stay in ONION_JAVA_OPTS.
rem
rem   ONION_JAVA_OPTS     extra JVM flags (heap, an agent, ...)
rem   ONION_CDS_ARCHIVE   override the archive location
rem   ONION_GENERATE_CDS  write the archive instead of using it (install-time only)
rem   ONION_DEBUG_STARTUP show what the JVM thinks of the archive rather than silencing it

set "ONION_JVM_ARGS="

rem The parallel collector: a compile is one short-lived burst of allocation, and G1 costs
rem it about 10% of its time for nothing (see bin/onion-jvm.sh). A collector named
rem in ONION_JAVA_OPTS wins -- the JVM refuses two.
echo.%ONION_JAVA_OPTS% | findstr /C:"GC" >nul
if errorlevel 1 set "ONION_JVM_ARGS=-XX:+UseParallelGC"

if not defined ONION_CDS_ARCHIVE set "ONION_CDS_ARCHIVE=%ONION_HOME%\lib\onion.jsa"

if defined ONION_GENERATE_CDS (
    set "ONION_JVM_ARGS=%ONION_JVM_ARGS% -XX:ArchiveClassesAtExit=%ONION_CDS_ARCHIVE%"
    goto :eof
)

if not exist "%ONION_CDS_ARCHIVE%" goto :eof

rem -Xshare:auto means an archive from another JDK, or from an install that has since
rem moved, is refused and the JVM carries on normally -- but it says so on stderr every
rem run, which would leak into anything capturing a tool's output. Silence unless asked.
if defined ONION_DEBUG_STARTUP (
    set "ONION_JVM_ARGS=%ONION_JVM_ARGS% -XX:SharedArchiveFile=%ONION_CDS_ARCHIVE% -Xshare:auto"
) else (
    set "ONION_JVM_ARGS=%ONION_JVM_ARGS% -XX:SharedArchiveFile=%ONION_CDS_ARCHIVE% -Xshare:auto -Xlog:cds=off -Xlog:cds+dynamic=off"
)
