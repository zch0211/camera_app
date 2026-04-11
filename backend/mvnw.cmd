@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Required ENV vars:
@REM   JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars:
@REM   MAVEN_OPTS        - parameters passed to the Java VM running Maven
@REM   MAVEN_BATCH_ECHO  - set to "on" to echo batch commands
@REM   MAVEN_BATCH_PAUSE - set to "on" to pause before exit
@REM ----------------------------------------------------------------------------

@echo off
if "%MAVEN_BATCH_ECHO%"=="on" echo %0 %*

setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
REM Strip trailing backslash
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

REM ---- Read wrapperUrl from properties ------------------------------------
set "WRAPPER_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPERTIES%") do (
    if /i "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
)

REM ---- Download maven-wrapper.jar if missing ------------------------------
if not exist "%WRAPPER_JAR%" (
    echo Downloading maven-wrapper.jar from %WRAPPER_URL%
    if "%WRAPPER_URL%"=="" (
        echo ERROR: wrapperUrl not set in %WRAPPER_PROPERTIES% 1>&2
        goto error
    )
    powershell -NonInteractive -NoProfile -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'" 2>nul
    if not exist "%WRAPPER_JAR%" (
        echo ERROR: Failed to download maven-wrapper.jar 1>&2
        goto error
    )
)

REM ---- Locate Java -------------------------------------------------------
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    for /f "delims=" %%i in ('where java 2^>nul') do (
        if not defined JAVA_CMD set "JAVA_CMD=%%i"
    )
)
if not defined JAVA_CMD (
    echo ERROR: JAVA_HOME is not set and java was not found on PATH 1>&2
    goto error
)

REM ---- Execute Maven via wrapper jar -------------------------------------
"%JAVA_CMD%" %MAVEN_OPTS% ^
    -classpath "%WRAPPER_JAR%" ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    org.apache.maven.wrapper.MavenWrapperMain %*

set ERROR_CODE=%ERRORLEVEL%
goto end

:error
set ERROR_CODE=1

:end
if "%MAVEN_BATCH_PAUSE%"=="on" pause
endlocal & exit /b %ERROR_CODE%
