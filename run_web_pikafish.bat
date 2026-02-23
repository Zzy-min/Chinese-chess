@echo off
chcp 65001 >nul
cd /d "%~dp0"

set "ENGINE_CMD=%XQ_XIANGQI_PIKAFISH_CMD%"
set "REBUILD_ARG="

if /i "%~1"=="--rebuild" (
    set "REBUILD_ARG=--rebuild"
) else (
    if not "%~1"=="" set "ENGINE_CMD=%~1"
    if /i "%~2"=="--rebuild" set "REBUILD_ARG=--rebuild"
)

if "%ENGINE_CMD%"=="" (
    set "ENGINE_CMD=%~dp0tools\engines\pikafish.exe"
)

if not exist "%ENGINE_CMD%" (
    echo Pikafish executable not found:
    echo   %ENGINE_CMD%
    echo.
    echo Usage:
    echo   run_web_pikafish.bat "D:\path\to\pikafish.exe" [--rebuild]
    echo.
    exit /b 1
)

set "XQ_XIANGQI_ENGINE=PIKAFISH"
set "XQ_XIANGQI_PIKAFISH_CMD=%ENGINE_CMD%"
set "XQ_XIANGQI_UCI_CMD=%ENGINE_CMD%"

echo ========================================
echo Xiangqi external engine enabled
echo Engine: %XQ_XIANGQI_PIKAFISH_CMD%
echo ========================================
echo.

call run_web.bat %REBUILD_ARG%

