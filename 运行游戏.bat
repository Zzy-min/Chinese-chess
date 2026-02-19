@echo off
chcp 65001 >nul
echo ========================================
echo       中国象棋游戏 - 启动程序
echo ========================================
echo.

cd /d "%~dp0"

if not exist "target\XiangqiGame-1.0.0.jar" (
    echo 首次运行，正在编译项目...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo.
        echo 编译失败！请确保已安装Maven。
        echo 按任意键退出...
        pause >nul
        exit /b 1
    )
)

echo.
echo 正在启动游戏...
java -jar target\XiangqiGame-1.0.0.jar

if errorlevel 1 (
    echo.
    echo 启动失败！请确保已安装Java 11或更高版本。
    pause >nul
)
