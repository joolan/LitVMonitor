@echo off
cd /d "%~dp0"
if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -jar target\litv-monitor-1.3.1.jar > stdout.log 2>&1
) else (
    java -jar target\litv-monitor-1.3.1.jar > stdout.log 2>&1
)
