@echo off
chcp 65001 >nul
echo 修复中国象棋编码问题...
echo.

echo 1. 清理旧的编译文件...
if exist "target" rmdir /s /q "target"

echo 2. 使用UTF-8编码重新编译...
javac -encoding UTF-8 -d target/classes -sourcepath src/main/java -cp ".;*" src/main/java/com/xiangqi/model/*.java
javac -encoding UTF-8 -d target/classes -sourcepath src/main/java -cp ".;target/classes" src/main/java/com/xiangqi/ai/*.java
javac -encoding UTF-8 -d target/classes -sourcepath src/main/java -cp ".;target/classes" src/main/java/com/xiangqi/controller/*.java
javac -encoding UTF-8 -d target/classes -sourcepath src/main/java -cp ".;target/classes" src/main/java/com/xiangqi/ui/*.java

echo 3. 运行程序...
cd target/classes
java -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN com.xiangqi.ui.XiangqiFrame
cd ..\..

echo.
echo 完成！
pause
