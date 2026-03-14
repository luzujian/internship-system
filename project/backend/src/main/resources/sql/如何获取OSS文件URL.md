# 如何获取真实的OSS文件URL

## 方法1：从数据库中查找已有的文件（推荐）

### 步骤：
1. **执行查找脚本**
   - 双击运行：`c:\Users\ASUS\Desktop\project\backend\src\main\resources\sql\run_find_oss_files.bat`
   - 或者在MySQL客户端中执行：`find_existing_oss_files.sql`

2. **查看结果**
   - 脚本会显示所有包含OSS URL的文件
   - 包括企业logo、照片、视频等
   - 复制这些URL用于测试

3. **使用这些URL**
   - 将找到的URL复制到SQL脚本中
   - 替换示例URL

## 方法2：通过学生端上传文件（最简单）

### 步骤：
1. **启动项目**
   - 启动后端服务
   - 启动前端服务

2. **登录学生端**
   - 使用学生账号登录
   - 例如：学号 `20200001`，密码 `123456`

3. **上传文件**
   - 进入"个人资料"或"申请实习"页面
   - 找到"上传简历"或"上传证书"功能
   - 选择本地文件上传
   - 系统会自动上传到阿里云OSS并生成URL

4. **获取URL**
   - 上传成功后，查看数据库
   - 执行：`SELECT * FROM student_archives ORDER BY upload_time DESC LIMIT 5;`
   - 复制 `file_url` 字段的值

## 方法3：通过阿里云OSS控制台上传

### 步骤：
1. **登录阿里云**
   - 访问：https://oss.console.aliyun.com/
   - 使用你的阿里云账号登录

2. **找到Bucket**
   - 在左侧菜单找到"Bucket列表"
   - 找到Bucket：`lzj-java-ai`
   - 点击进入

3. **上传测试文件**
   - 点击"文件管理"
   - 点击"上传文件"
   - 选择本地文件（简历PDF、证书图片等）
   - 点击上传

4. **获取URL**
   - 上传成功后，找到文件
   - 右键点击文件
   - 选择"复制文件URL"
   - URL格式类似：`https://lzj-java-ai.oss-cn-beijing.aliyuncs.com/文件路径/文件名`

5. **使用URL**
   - 将URL复制到SQL脚本中
   - 替换示例URL

## 方法4：使用在线测试文件URL（快速测试）

如果你只是想快速测试UI效果，可以使用一些公开的测试文件URL：

### PDF文件示例：
```
https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf
https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf
```

### 图片文件示例：
```
https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800
https://images.unsplash.com/photo-1557683316-973673baf926?w=800
https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800
```

## 推荐方案

### 最佳实践：
1. **先执行查找脚本**，看数据库中是否已有文件
2. **如果有文件**，直接使用这些URL
3. **如果没有文件**，通过学生端上传（最真实）
4. **如果只是测试UI**，使用在线测试文件URL

### 具体操作：

#### 方案A：使用数据库中的文件（推荐）
```sql
-- 1. 执行查找脚本
mysql -h localhost -u root -p1234 internship < find_existing_oss_files.sql

-- 2. 复制找到的URL

-- 3. 更新SQL脚本中的URL
UPDATE student_archives
SET file_url = '复制的URL'
WHERE id = 1;
```

#### 方案B：使用在线测试文件（快速测试）
```sql
-- 使用在线PDF文件作为简历
UPDATE student_archives
SET file_url = 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf'
WHERE file_type = '简历';

-- 使用在线图片作为证书
UPDATE student_archives
SET file_url = 'https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800'
WHERE file_type = '证书';
```

#### 方案C：通过学生端上传（最真实）
1. 启动前后端项目
2. 登录学生端
3. 上传真实的简历和证书文件
4. 在数据库中查看生成的URL
5. 使用这些URL

## 注意事项

⚠️ **重要提醒**：
- 确保文件URL是可访问的（不要使用私有Bucket的URL）
- PDF文件可以在线预览
- 图片文件可以在线预览
- Word、Excel等文件只能下载，不能预览

## 快速开始

如果你想立即看到效果，我建议：

1. **执行查找脚本**，看是否有现有文件
2. **如果没有**，使用在线测试文件URL
3. **更新SQL脚本**，替换URL
4. **执行SQL脚本**，插入测试数据
5. **刷新页面**，查看效果

需要我帮你准备一个使用在线测试文件的SQL脚本吗？
