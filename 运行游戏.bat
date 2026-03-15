@echo off
chcp 65001 >nul
echo ========================================
echo       轻·棋局 Web 启动程序
echo ========================================
echo.

cd /d "%~dp0"
call run_web.bat %*
