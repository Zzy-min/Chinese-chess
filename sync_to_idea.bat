@echo off
chcp 65001 >nul
echo 将正确的class文件同步到IDEA输出目录
echo ========================================
echo.

echo [1] 复制正确的class文件到IDEA out目录...
if exist "out" rmdir /s /q "out"
xcopy /s /y /i "bin" "out\production\classes"

echo [2] 复制到target目录（如果有）...
if exist "target" rmdir /s /q "target"
xcopy /s /y /i "bin" "target\classes"

echo.
echo [3] 同步完成！
echo.
echo 现在可以在IDEA中运行了，程序会使用正确的class文件。
echo.
echo 如果还有问题，请在IDEA中添加VM options：
echo -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN
echo.
pause
