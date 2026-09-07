@REM ---------------------------------------------------------------------------
@REM LM-GUARD lightweight Maven wrapper (Windows).
@REM   1. Uses `mvn` from the PATH when available.
@REM   2. Otherwise downloads the distribution pinned in
@REM      .mvn\wrapper\maven-wrapper.properties into %USERPROFILE%\.m2\wrapper\dists
@REM ---------------------------------------------------------------------------
@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "WRAPPER_PROPS=%SCRIPT_DIR%.mvn\wrapper\maven-wrapper.properties"

where mvn >NUL 2>&1
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

if not exist "%WRAPPER_PROPS%" (
  echo mvnw: missing %WRAPPER_PROPS% and no 'mvn' on PATH.
  echo mvnw: install Maven 3.9+ from https://maven.apache.org/download.cgi and retry.
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPS%") do (
  if "%%A"=="distributionUrl" set "DIST_URL=%%B"
)

if "%DIST_URL%"=="" (
  echo mvnw: distributionUrl not set in %WRAPPER_PROPS%
  exit /b 1
)

for %%F in ("%DIST_URL%") do set "ZIP_NAME=%%~nxF"
set "DIST_NAME=%ZIP_NAME:-bin.zip=%"
set "DIST_HOME=%USERPROFILE%\.m2\wrapper\dists\%DIST_NAME%"

if not exist "%DIST_HOME%\bin\mvn.cmd" (
  echo mvnw: downloading %DIST_URL% ...
  if not exist "%DIST_HOME%.tmp" mkdir "%DIST_HOME%.tmp"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%DIST_HOME%.tmp\%ZIP_NAME%';" ^
    "Expand-Archive -Path '%DIST_HOME%.tmp\%ZIP_NAME%' -DestinationPath '%DIST_HOME%.tmp' -Force;" ^
    "Move-Item -Path '%DIST_HOME%.tmp\%DIST_NAME%' -Destination '%DIST_HOME%' -Force;" ^
    "Remove-Item -Recurse -Force '%DIST_HOME%.tmp'"
  if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

"%DIST_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
