@echo off
setlocal EnableExtensions

rem Run from MobileApp regardless of where the file was double-clicked.
cd /d "%~dp0"
if errorlevel 1 goto failed

if not exist "scripts\format_kotlin.py" (
    echo [ERROR] Put this BAT file in the MobileApp folder next to gradlew.bat.
    goto failed
)

where python >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not available in PATH.
    goto failed
)

set "KTFMT_JAR=%~dp0scripts\ktfmt-0.55-with-dependencies.jar"
echo.
echo [1/2] Formatting ALL Kotlin sources in app\src with ktfmt 0.55...

rem Prefer the local JAR (works when Maven Central returns HTTP 403).
if exist "%KTFMT_JAR%" goto local_jar

rem No local JAR: the project script downloads and verifies the pinned JAR.
python "scripts\format_kotlin.py"
if errorlevel 1 goto failed

echo [2/2] Verifying CI formatting check...
python "scripts\format_kotlin.py" --check
if errorlevel 1 goto failed
goto success

:local_jar
python "scripts\format_kotlin.py" --jar "%KTFMT_JAR%"
if errorlevel 1 goto failed

echo [2/2] Verifying CI formatting check...
python "scripts\format_kotlin.py" --check --jar "%KTFMT_JAR%"
if errorlevel 1 goto failed
goto success

:success
echo.
echo [OK] Formatting passed. The same format_kotlin.py --check was run.
echo.
echo Changed Kotlin files:
where git >nul 2>&1
if not errorlevel 1 git status --short -- app/src
echo.
pause
exit /b 0

:failed
echo.
echo [ERROR] Formatting or verification failed; no commit was made.
echo.
pause
exit /b 1
