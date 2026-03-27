@echo off
chcp 65001 >nul
cd /d "%~dp0"

set "CLOUDFLARED_CMD=%CLOUDFLARED_CMD%"
if "%CLOUDFLARED_CMD%"=="" set "CLOUDFLARED_CMD=cloudflared"

set "TUNNEL_NAME=%~1"
if "%TUNNEL_NAME%"=="" set "TUNNEL_NAME=xiangqiarena"

echo ========================================
echo XiangqiArena - Cloudflare Tunnel Runner
echo ========================================
echo Tunnel: %TUNNEL_NAME%
echo Command: %CLOUDFLARED_CMD%
echo.

"%CLOUDFLARED_CMD%" --version >nul 2>&1
if errorlevel 1 (
    echo cloudflared is not available.
    echo.
    echo Option 1:
    echo   install and put cloudflared on PATH
    echo.
    echo Option 2:
    echo   set CLOUDFLARED_CMD to the full executable path
    echo.
    exit /b 1
)

echo Starting Cloudflare Tunnel...
"%CLOUDFLARED_CMD%" tunnel run %TUNNEL_NAME%
