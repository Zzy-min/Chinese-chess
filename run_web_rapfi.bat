@echo off
chcp 65001 >nul
cd /d "%~dp0"

set "ENGINE_CMD=%XQ_GOMOKU_PISKVORK_CMD%"
set "REBUILD_ARG="

if /i "%~1"=="--rebuild" (
    set "REBUILD_ARG=--rebuild"
) else (
    if not "%~1"=="" set "ENGINE_CMD=%~1"
    if /i "%~2"=="--rebuild" set "REBUILD_ARG=--rebuild"
)

if "%ENGINE_CMD%"=="" (
    set "ENGINE_CMD=%~dp0tools\engines\rapfi.exe"
)

if not exist "%ENGINE_CMD%" (
    echo Rapfi executable not found:
    echo   %ENGINE_CMD%
    echo.
    echo Usage:
    echo   run_web_rapfi.bat "D:\path\to\rapfi.exe" [--rebuild]
    echo.
    exit /b 1
)

set "XQ_GOMOKU_ENGINE=RAPFI"
set "XQ_GOMOKU_RAPFI_CMD=%ENGINE_CMD%"
set "XQ_GOMOKU_PISKVORK_CMD=%ENGINE_CMD%"

echo ========================================
echo Gomoku external engine enabled
echo Engine: %XQ_GOMOKU_PISKVORK_CMD%
echo ========================================
echo.

call run_web.bat %REBUILD_ARG%
