@echo off
chcp 65001 >nul
cd /d "%~dp0\services\go-engine"

set "KATAGO_HOME=%USERPROFILE%\tools\katago"
set "KATAGO_MODELS=%KATAGO_HOME%\models"

if "%KATAGO_CMD%"=="" if "%KATAGO_BIN%"=="" call :autoconfigure

if "%KATAGO_CMD%"=="" if "%KATAGO_BIN%"=="" (
    echo No KataGo installation was detected.
    echo Install the official engine under %KATAGO_HOME% or set KATAGO_CMD / KATAGO_BIN manually.
    exit /b 1
)

if "%KATAGO_CMD%"=="" (
    if "%KATAGO_CONFIG%"=="" (
        echo KATAGO_CONFIG is not set.
        exit /b 1
    )
    if "%KATAGO_MODEL%"=="" (
        echo KATAGO_MODEL is not set.
        exit /b 1
    )
    echo Using KataGo binary: %KATAGO_BIN%
    echo Using config: %KATAGO_CONFIG%
    echo Using model: %KATAGO_MODEL%
) else (
    echo Using KATAGO_CMD: %KATAGO_CMD%
)

python server.py
exit /b %errorlevel%

:autoconfigure
if "%KATAGO_MODEL%"=="" if exist "%KATAGO_MODELS%\*.bin.gz" (
    for %%F in ("%KATAGO_MODELS%\*.bin.gz") do if not defined KATAGO_MODEL set "KATAGO_MODEL=%%~fF"
)

call :try_engine "%KATAGO_HOME%\engines\katago-v1.16.4-cuda12.8-cudnn9.8.0-windows-x64" cuda12.8
if defined KATAGO_BIN goto :eof

call :try_engine "%KATAGO_HOME%\engines\katago-v1.16.4-opencl-windows-x64" opencl
goto :eof

:try_engine
set "ENGINE_DIR=%~1"
set "ENGINE_LABEL=%~2"
if not exist "%ENGINE_DIR%\katago.exe" goto :eof

if /i "%ENGINE_LABEL%"=="cuda12.8" (
    call :has_cuda128_runtime "%ENGINE_DIR%"
    if errorlevel 1 goto :eof
)

set "KATAGO_BIN=%ENGINE_DIR%\katago.exe"
if "%KATAGO_CONFIG%"=="" set "KATAGO_CONFIG=%ENGINE_DIR%\default_gtp.cfg"
echo Auto-detected %ENGINE_LABEL% KataGo in %ENGINE_DIR%
goto :eof

:has_cuda128_runtime
if exist "%~1\cublas64_12.dll" if exist "%~1\cudart64_12.dll" if exist "%~1\cudnn64_9.dll" exit /b 0
where cublas64_12.dll >nul 2>nul || exit /b 1
where cudart64_12.dll >nul 2>nul || exit /b 1
where cudnn64_9.dll >nul 2>nul || exit /b 1
exit /b 0
