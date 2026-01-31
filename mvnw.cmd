@REM Maven Wrapper script for Windows
@echo off
setlocal

set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD=%JAVA_HOME%\bin\java

set BASE_DIR=%~dp0
set WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar

%JAVA_CMD% -jar "%WRAPPER_JAR%" %*

endlocal
