# 执行SQL脚本说明

## 步骤

1. 打开MySQL命令行客户端或使用MySQL Workbench

2. 连接到数据库：
   ```bash
   mysql -u root -p
   ```

3. 选择数据库：
   ```sql
   USE internship;
   ```

4. 执行SQL脚本：
   ```sql
   SOURCE c:/Users/ASUS/Desktop/project/backend/src/main/resources/sql/add_test_archives_base64.sql;
   ```

   或者直接复制SQL文件中的内容并粘贴执行

5. 验证数据：
   ```sql
   SELECT * FROM student_archives WHERE student_id IN (7, 8);
   ```

## 说明

- 该脚本会清除之前插入的测试数据（student_id为7和8的数据）
- 插入4条新记录，使用base64 data URL作为文件内容
- 包含2个学生的个人简历和获奖证书

## 测试数据

### 赵强 (student_id = 7)
- 赵强_个人简历.pdf
- 赵强_获奖证书.jpg

### 李华 (student_id = 8)
- 李华_个人简历.pdf
- 李华_获奖证书.jpg
