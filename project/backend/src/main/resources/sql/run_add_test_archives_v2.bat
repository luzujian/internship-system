@echo off
chcp 65001 >nul
echo ========================================
echo 为学生添加测试的个人简历和获奖证书数据
echo ========================================
echo.

echo 正在连接数据库...
echo.

mysql -h localhost -u root -p1234 internship < "c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\add_test_student_archives_v2.sql"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo 测试数据添加成功！
    echo ========================================
    echo.
    echo 现在可以在企业端的岗位申请查看页面查看学生的简历和证书了。
    echo.
) else (
    echo.
    echo ========================================
    echo 测试数据添加失败！请检查错误信息。
    echo ========================================
    echo.
)

pause
