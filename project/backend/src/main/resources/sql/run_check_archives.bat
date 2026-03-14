@echo off
chcp 65001 >nul
echo 正在检查学生归档文件数据...
echo.

mysql -h localhost -u root -p1234 internship < "c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\check_student_archives.sql"

echo.
echo 检查完成！
pause
