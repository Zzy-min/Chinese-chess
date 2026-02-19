@echo off
chcp 65001 >nul
echo 中国象棋 - 完整修复脚本
echo ========================================
echo.

echo [1] 清理旧的class文件...
if exist "bin" rmdir /s /q "bin"
if exist "target" rmdir /s /q "target"
mkdir bin

echo [2] 一次性编译所有Java文件（UTF-8编码）...
javac -encoding UTF-8 -d bin -cp ".;bin" src/main/java/com/xiangqi/model/*.java src/main/java/com/xiangqi/ai/*.java src/main/java/com/xiangqi/controller/*.java src/main/java/com/xiangqi/ui/*.java

if errorlevel 1 (
    echo.
    echo 编译失败！请检查代码。
    pause
    exit /b 1
)

echo.
echo [3] 编译成功！运行程序...
cd bin
java -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN com.xiangqi.ui.XiangqiFrame

echo.
echo 运行完成！
pause
