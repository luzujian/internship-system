# 文件上传配置说明

## 功能说明

系统已实现完整的文件上传功能，支持：

1. **单文件上传**：上传单个图片文件
2. **批量上传**：一次上传多个图片文件
3. **文件删除**：删除已上传的文件
4. **文件预览**：支持图片预览功能

## 配置说明

### application.yml配置

```yaml
# Spring文件上传配置
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 100MB
      enabled: true

# 文件上传配置
file:
  upload:
    path: D:/Web-work/Internship/uploads
    url-prefix: /uploads
    max-size: 10485760
```

### 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| max-file-size | 单个文件最大大小 | 10MB |
| max-request-size | 请求最大大小 | 100MB |
| path | 文件存储路径 | /uploads |
| url-prefix | URL访问前缀 | /uploads |
| max-size | 文件大小限制（字节） | 10485760（10MB） |

## 文件存储结构

上传的文件按日期分层存储：

```
uploads/
├── 2024/
│   ├── 01/
│   │   ├── 15/
│   │   │   ├── uuid1.jpg
│   │   │   ├── uuid2.png
│   │   ├── 16/
│   │   │   ├── uuid3.jpg
```

## 支持的文件类型

系统支持以下图片格式：

- image/jpeg
- image/jpg
- image/png
- image/gif
- image/bmp
- image/webp

## API接口

### 1. 单文件上传

**接口**：`POST /upload/image`

**参数**：
- file：MultipartFile（必填）

**返回**：
```json
{
  "code": 200,
  "msg": "上传成功",
  "data": {
    "url": "/uploads/2024/01/15/uuid.jpg",
    "filename": "uuid.jpg",
    "size": "102400"
  }
}
```

### 2. 批量上传

**接口**：`POST /upload/images`

**参数**：
- files：MultipartFile[]（必填）

**返回**：
```json
{
  "code": 200,
  "msg": "上传完成",
  "data": {
    "uploadedFiles": [
      {
        "url": "/uploads/2024/01/15/uuid1.jpg",
        "filename": "uuid1.jpg",
        "size": "102400"
      }
    ],
    "errors": [],
    "total": 1,
    "success": 1,
    "failed": 0
  }
}
```

### 3. 文件删除

**接口**：`DELETE /upload/image`

**参数**：
```json
{
  "url": "/uploads/2024/01/15/uuid.jpg"
}
```

**返回**：
```json
{
  "code": 200,
  "msg": "删除成功"
}
```

## 前端使用示例

### 单文件上传

```javascript
const handleFileChange = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    
    const response = await axios.post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    if (response.data.code === 200) {
      const fileUrl = response.data.data.url
      console.log('文件URL:', fileUrl)
    }
  } catch (error) {
    console.error('上传失败:', error)
  }
}
```

### 文件预览

```javascript
// 判断文件URL类型
const imageUrl = fileUrl.startsWith('data:') 
  ? fileUrl 
  : '/api/' + fileUrl

// 在模板中使用
<img :src="imageUrl" alt="图片预览" />
```

## 生产环境建议

### 1. 使用独立的文件服务器

对于生产环境，建议使用专业的文件存储服务：

- **阿里云OSS**
- **腾讯云COS**
- **七牛云**
- **MinIO**（自建）

### 2. 修改上传逻辑

如果使用云存储，需要修改`FileUploadController.java`：

```java
// 替换本地文件上传逻辑为云存储上传
String fileUrl = ossService.upload(file);
```

### 3. 配置CDN加速

为上传的文件配置CDN加速，提高访问速度。

## 安全建议

1. **文件类型验证**：系统已实现，只允许图片格式
2. **文件大小限制**：根据实际需求调整
3. **文件名处理**：使用UUID重命名，避免冲突
4. **访问控制**：配置防火墙，限制直接访问
5. **定期清理**：定期清理无用文件

## 常见问题

### 1. 上传失败

- 检查文件大小是否超过限制
- 检查文件格式是否支持
- 检查上传目录是否有写权限

### 2. 文件无法访问

- 检查`FileUploadConfig`配置是否正确
- 检查文件路径是否正确
- 检查防火墙设置

### 3. 文件预览失败

- 检查URL前缀配置
- 检查文件是否存在
- 检查浏览器控制台错误信息
