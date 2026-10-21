@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -File "%~dp0android-apk\start-local.ps1"
pause
