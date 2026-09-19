@echo off
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8088 ^| findstr LISTENING') do taskkill /pid %%a /f
echo Done
