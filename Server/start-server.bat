@echo off
cd /d "%~dp0"
start "" wt.exe -w new nt -d "%CD%" --title "Fieldfolio Server" powershell.exe -NoExit -Command "mvn spring-boot:run"