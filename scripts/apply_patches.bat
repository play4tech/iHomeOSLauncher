@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."

echo Applying patch to libs_systemui...
cd /d "%ROOT_DIR%\libs_systemui"
git apply "%ROOT_DIR%\patches\libs_systemui.patch"
if %ERRORLEVEL% EQU 0 (
    echo libs_systemui patch successfully applied!
) else (
    echo Failed to apply patch or patch already applied.
)
cd /d "%ROOT_DIR%"
endlocal
