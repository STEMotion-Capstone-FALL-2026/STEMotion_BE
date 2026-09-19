@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

if exist %WRAPPER_JAR% (
    "%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% ^
        -jar %WRAPPER_JAR% %*
) else (
    echo Error: Could not find maven-wrapper.jar
    exit /b 1
)
