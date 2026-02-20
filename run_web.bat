@echo off
chcp 65001 >nul
cd /d "%~dp0"

set "URL=http://127.0.0.1:18388/"
set "MAIN_CLASS=com.xiangqi.web.BrowserModeMain"

echo ========================================
echo Xiangqi - Web Quick Start
echo ========================================
echo.

set "PORT_READY="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /i ":18388" ^| findstr /i "LISTENING"') do (
    set "PORT_READY=1"
    goto :port_checked
)

:port_checked
if defined PORT_READY (
    echo [1/3] Web server already running. Opening browser...
    start "" "%URL%"
    goto :eof
)

if not exist "target\classes\com\xiangqi\web\BrowserModeMain.class" goto :compile
if not exist "target\classes\com\xiangqi\web\WebXiangqiServer.class" goto :compile
if /i "%~1"=="--rebuild" goto :compile
echo [1/3] Using existing classes. Use --rebuild to force compile.
goto :start_server

:compile
echo [1/3] Compiling latest source...
call compile_fix.bat ^< nul ^> nul
if errorlevel 1 (
    echo Compile failed. Please check your Java setup.
    exit /b 1
)

:start_server
echo [2/3] Starting web server...
start "Xiangqi Web Server" cmd /c "cd /d ""%~dp0"" && java -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN -cp target/classes %MAIN_CLASS%"

echo [3/3] Opening browser...
timeout /t 1 /nobreak >nul
start "" "%URL%"
