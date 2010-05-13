@echo off

rem This variable represents the directory in which Onion is installed.
rem set ONION_HOME=

rem This variable represents the directory in which J2SE is installed.
rem set JAVA_HOME=$JAVA_HOME$ 

if /i %ONION_HOME%/ == / goto NO_ONION_HOME

if /i %JAVA_HOME%/ == / goto NO_JAVA_HOME

goto START

:NO_JAVA_HOME
echo Please set the environment variable JAVA_HOME
goto END

:NO_ONION_HOME
echo Please set the environment variable ONION_HOME
goto END

:START
%JAVA_HOME%\bin\java -classpath %CLASSPATH%;%ONION_HOME%\onion.jar;%ONION_HOME%\lib\bcel.jar;%ONION_HOME%\onion-library.jar onion.tools.ScriptRunner %*

:END