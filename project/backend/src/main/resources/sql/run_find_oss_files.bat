@echo off
chcp 65001 >nul
echo ========================================
echo 查找数据库中已有的OSS文件URL
echo ========================================
echo.

echo 正在连接数据库...
echo.

mysql -h localhost -u root -p1234 internship < "c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\find_existing_oss_files.sql"

echo.
echo ========================================
echo 查找完成！
echo ========================================
echo.
echo 如果找到了文件URL，请复制它们用于测试。
echo 如果没有找到文件URL，请通过以下方式获取：
echo.
echo 方法1：通过学生端上传文件
echo   1. 登录学生端
echo   2. 进入个人资料或申请页面
echo   3. 上传个人简历和获奖证书
echo   4. 上传后数据库会自动生成真实的OSS URL
echo.
echo 方法2：使用阿里云OSS控制台
echo   1. 登录阿里云OSS控制台
echo   2. 找到你的Bucket：lzj-java-ai
echo   3. 上传测试文件
echo   4. 复制文件的URL
echo.
pause
