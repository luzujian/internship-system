@echo off
chcp 65001 >nul
echo 正在为学生添加测试的个人简历和获奖证书数据...
echo.

mysql -h localhost -u root -p1234 internship < "c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\add_test_student_archives.sql"

echo.
echo 测试数据添加完成！
pause
