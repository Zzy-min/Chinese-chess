@echo off
chcp 65001 >nul
echo 中国象棋 - UTF-8编码编译脚本
echo ========================================
echo.

echo [1] 清理旧的编译文件...
if exist "target" rmdir /s /q "target"
if exist "bin" rmdir /s /q "bin"
mkdir target\classes

echo [2] 使用UTF-8编码编译Java文件...
javac -encoding UTF-8 -d target/classes ^
    -sourcepath src/main/java ^
    -cp ".;target/classes" ^
    src/main/java/com/xiangqi/model/*.java

javac -encoding UTF-8 -d target/classes ^
    -sourcepath src/main/java ^
    -cp ".;target/classes" ^
    src/main/java/com/xiangqi/ai/*.java

javac -encoding UTF-8 -d target/classes ^
    -sourcepath src/main/java ^
    -cp ".;target/classes" ^
    src/main/java/com/xiangqi/controller/*.java

javac -encoding UTF-8 -d target/classes ^
    -sourcepath src/main/java ^
    -cp ".;target/classes" ^
    src/main/java/com/xiangqi/ui/*.java

echo.
echo [3] 编译完成！
echo.
echo 运行游戏请执行: run_game.bat
pause
