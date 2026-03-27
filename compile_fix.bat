@echo off
chcp 65001 >nul
echo 轻·棋局 - Web 编译脚本
echo ========================================
echo.

echo [1] 清理旧的编译文件...
if exist "target" rmdir /s /q "target"
if exist "bin" rmdir /s /q "bin"

echo [2] 使用 Maven 打包 Web 版本并复制运行时依赖...
call mvn -q -DskipTests package
if errorlevel 1 (
    echo.
    echo 编译失败！请确认 Java / Maven 环境正常。
    exit /b 1
)

echo.
echo [3] 编译完成！
echo.
echo 启动网页版请执行: run_web.bat
echo 运行时依赖已复制到: target\dependency
