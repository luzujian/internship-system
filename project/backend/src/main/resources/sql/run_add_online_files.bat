@echo off
chcp 65001 >nul
echo ========================================
echo 使用在线测试文件添加学生简历和证书
echo ========================================
echo.
echo 说明：这个脚本使用真实的在线文件，可以立即预览和下载！
echo.

echo 正在连接数据库...
echo.

mysql -h localhost -u root -p1234 internship < "c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\add_test_archives_online_files.sql"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo 测试数据添加成功！
    echo ========================================
    echo.
    echo 现在可以：
    echo 1. 刷新企业端的岗位申请查看页面
    echo 2. 点击任意学生的"查看详情"按钮
    echo 3. 在页面底部看到"个人简历"和"获奖证书"
    echo 4. 点击"预览"按钮可以在线查看文件
    echo 5. 点击"下载"按钮可以下载文件
    echo.
    echo 测试文件说明：
    echo - 简历：使用真实的PDF文件（可预览、可下载）
    echo - 证书：使用真实的图片文件（可预览、可下载）
    echo.
) else (
    echo.
    echo ========================================
    echo 测试数据添加失败！请检查错误信息。
    echo ========================================
    echo.
)

pause
