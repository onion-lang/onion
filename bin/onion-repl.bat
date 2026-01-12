@echo off
setlocal enabledelayedexpansion

rem Remove trailing backslash from ONION_HOME if present
if "%ONION_HOME:~-1%"=="\" set "ONION_HOME=%ONION_HOME:~0,-1%"

rem Use java from PATH if JAVA_HOME is not set
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java"
) else (
    set "JAVA_CMD=java"
)

rem Check if java is available
"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo Error: Java not found. Please install Java or set JAVA_HOME environment variable.
    exit /b 1
)

rem Build classpath with all jars in lib directory
set "CP=%ONION_HOME%\onion.jar"
if exist "%ONION_HOME%\lib" (
    for %%F in ("%ONION_HOME%\lib\*.jar") do (
        set "CP=!CP!;%%F"
    )
)

rem Add user classpath if set
if defined CLASSPATH (
    set "CP=%CP%;%CLASSPATH%"
)

rem Run the Onion REPL
"%JAVA_CMD%" -classpath "%CP%" onion.tools.Repl %*
