@echo off
chcp 65001 >nul
echo 中国象棋 - 快速修复启动
echo ========================================
echo.

echo [1] 删除旧的XiangqiPanel.class文件...
del /q "bin\com\xiangqi\ui\XiangqiPanel.class"
del /q "bin\com\xiangqi\ui\XiangqiPanel$1.class"
del /q "bin\com\xiangqi\ui\XiangqiPanel$GameMode.class"

echo [2] 重新编译XiangqiPanel.java...
javac -encoding UTF-8 -d bin -cp ".;bin" src/main/java/com/xiangqi/ui/XiangqiPanel.java

echo [3] 运行程序...
cd bin
java -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN com.xiangqi.ui.XiangqiFrame

echo.
pause
