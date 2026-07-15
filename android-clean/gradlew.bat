@echo off
setlocal
set DIR=%~dp0
set PROJECT_DIR=%DIR:~0,-1%
set CACHED_GRADLE=%USERPROFILE%\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat
if exist "%CACHED_GRADLE%" (
  call "%CACHED_GRADLE%" -p "%PROJECT_DIR%" %*
  exit /b %ERRORLEVEL%
)
echo Cached Gradle distribution not found: %CACHED_GRADLE%
exit /b 1
