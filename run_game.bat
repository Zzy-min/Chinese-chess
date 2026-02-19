@echo off
chcp 65001 >nul
echo 中国象棋 - 修复版本启动脚本
echo ========================================
echo.

echo [1] 删除旧的class文件...
if exist "bin" rmdir /s /q "bin"
if exist "target" rmdir /s /q "target"

echo [2] 使用备份目录中正确的class文件...
xcopy /s /y /i "src\main\java_backup\java\*.class" "bin\"

echo [3] 运行程序...
cd bin
java -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN com.xiangqi.ui.XiangqiFrame

echo.
pause
