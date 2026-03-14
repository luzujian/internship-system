@echo off
chcp 65001 >nul
title 添加学生简历和证书测试数据

echo.
echo ========================================
echo 添加学生简历和证书测试数据
echo ========================================
echo.
echo 正在连接数据库...
echo.

"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h localhost -u root -p1234 internship < "c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\add_test_archives_online_files.sql"

echo.
if %errorlevel% equ 0 (
    echo ========================================
    echo 测试数据添加成功！
    echo ========================================
) else (
    echo ========================================
    echo 测试数据添加失败！
    echo ========================================
    echo.
    echo 可能的原因：
    echo 1. MySQL路径不正确，请修改脚本中的MySQL路径
    echo 2. MySQL服务未启动
    echo 3. 数据库连接信息不正确
    echo.
)

echo.
echo 按任意键退出...
pause >nul
