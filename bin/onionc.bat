@echo off
setlocal enabledelayedexpansion

if not defined ONION_HOME (
    set "ONION_HOME=%~dp0.."
)

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

set "CP=%ONION_HOME%\onion.jar"
if exist "%ONION_HOME%\lib" (
    for %%F in ("%ONION_HOME%\lib\*.jar") do (
        set "CP=!CP!;%%F"
    )
)

if defined CLASSPATH (
    set "CP=%CP%;%CLASSPATH%"
)

rem Run the Onion compiler
"%JAVA_CMD%" -classpath "%CP%" onion.tools.CompilerFrontend %*
