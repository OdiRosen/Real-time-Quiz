@echo off
set "JAVA_HOME=C:\Users\OdiR\.jdks\ms-17.0.18"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
mvnw.cmd -q test
if %errorlevel% equ 0 echo TESTS_OK
exit /b %errorlevel%
