# 直接在MySQL中执行SQL（最简单，无编码问题）

## 🎯 方法1：直接复制SQL语句到MySQL（推荐）

### 步骤1：打开命令行
- 按 `Win + R` 键
- 输入 `cmd`
- 按回车

### 步骤2：进入MySQL
输入以下命令：

```cmd
mysql -h localhost -u root -p1234 internship
```

### 步骤3：复制并执行SQL
复制以下SQL语句（全部复制），粘贴到MySQL命令行：

```sql
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_个人简历.pdf', '简历', 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf', NOW(), NOW(), NOW()),
(1, '张三_英语六级证书.jpg', '证书', 'https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800', NOW(), NOW(), NOW()),
(1, '张三_计算机二级证书.jpg', '证书', 'https://images.unsplash.com/photo-1557683316-973673baf926?w=800', NOW(), NOW(), NOW()),
(2, '李四_个人简历.pdf', '简历', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), NOW(), NOW()),
(2, '李四_奖学金证书.jpg', '证书', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800', NOW(), NOW(), NOW()),
(2, '李四_优秀学生干部证书.jpg', '证书', 'https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=800', NOW(), NOW(), NOW()),
(3, '王五_个人简历.pdf', '简历', 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf', NOW(), NOW(), NOW()),
(3, '王五_数学建模竞赛证书.jpg', '证书', 'https://images.unsplash.com/photo-1557682257-93bd80d0f78d?w=800', NOW(), NOW(), NOW());
```

### 步骤4：查看结果
输入：

```sql
SELECT 
    sa.id,
    sa.student_id,
    su.name AS student_name,
    sa.file_name,
    sa.file_type,
    sa.file_url
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
ORDER BY sa.upload_time DESC;
```

---

## 🎯 方法2：使用MySQL客户端（如果有的话）

### 步骤1：打开MySQL客户端
- 打开Navicat、phpMyAdmin、MySQL Workbench等
- 或者使用IntelliJ IDEA的数据库工具

### 步骤2：连接数据库
- 主机：`localhost`
- 端口：`3306`
- 用户名：`root`
- 密码：`1234`
- 数据库：`internship`

### 步骤3：执行SQL
1. 打开文件：`simple_add_archives.sql`
2. 复制所有SQL语句
3. 粘贴到MySQL客户端的查询窗口
4. 点击"执行"或按F5

---

## 🎯 方法3：使用IntelliJ IDEA（推荐）

### 步骤1：打开IDEA
- 打开IntelliJ IDEA
- 打开项目：`c:\Users\ASUS\Desktop\project\backend`

### 步骤2：打开Database工具
- 点击右侧的"Database"标签
- 找到`internship`数据库连接
- 如果没有，创建新的连接

### 步骤3：执行SQL
1. 右键点击`internship`数据库
2. 选择"New" -> "Query Console"
3. 复制上面的SQL语句
4. 粘贴到查询控制台
5. 点击绿色的执行按钮

---

## ✅ 验证是否成功

### 验证1：查询总数
```sql
SELECT COUNT(*) AS total FROM student_archives;
```

应该看到至少8条记录

### 验证2：查看详细数据
```sql
SELECT 
    sa.id,
    sa.student_id,
    su.name AS student_name,
    sa.file_name,
    sa.file_type,
    sa.file_url
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
ORDER BY sa.upload_time DESC;
```

### 验证3：查看前端页面
1. 打开浏览器，访问：`http://localhost:5173`
2. 登录企业端
3. 导航到"岗位申请查看"页面
4. 点击任意学生的"查看详情"按钮
5. 滚动到页面底部
6. 应该看到"个人简历"和"获奖证书"部分

---

## 📞 常见问题

### Q1: 提示"Access denied for user 'root'@'localhost'"

**A:** 检查MySQL密码是否为 `1234`，如果不是，修改连接命令中的密码

### Q2: 提示"Unknown database 'internship'"

**A:** 确认数据库名称是否为 `internship`，如果不是，修改连接命令中的数据库名称

### Q3: 提示"Can't connect to MySQL server"

**A:** 检查MySQL服务是否正在运行

---

## 🎉 推荐方式

**最简单的方式（复制粘贴）：**

1. **打开命令行**：按 `Win + R`，输入 `cmd`，按回车
2. **进入MySQL**：输入 `mysql -h localhost -u root -p1234 internship`
3. **复制SQL**：复制上面的SQL语句
4. **粘贴执行**：粘贴到MySQL命令行，按回车
5. **查看结果**：输入查询命令查看数据

**现在就开始吧！** 🚀
