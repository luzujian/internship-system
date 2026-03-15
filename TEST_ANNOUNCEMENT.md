# 公告发布功能修复验证

## 修复内容

### 1. 后端修复 (AnnouncementController.java)

**问题**:
- 日期格式解析失败（前端发送 ISO 8601 格式，后端只支持简单格式）
- targetType 格式处理不当（前端发送数组，后端期望字符串）
- attachments 为空时可能导致 null 值

**修复**:
- 支持多种日期格式解析：ISO 8601 (`yyyy-MM-dd'T'HH:mm:ss.SSSX`)、简单格式 (`yyyy-MM-dd'T'HH:mm:ss`)、纯日期格式 (`yyyy-MM-dd`)
- 自动检测 `targetType` 类型，如果是数组则序列化为 JSON 字符串
- 当 `attachments` 为空时，默认设置为空数组 `[]`
- 添加详细的日志记录，便于调试

### 2. 前端修复 (AnnouncementManagement.vue, Dashboard.vue)

**问题**: 前端将 `targetType` 数组序列化为 JSON 字符串后发送

**修复**: 直接发送数组，让后端处理序列化

```javascript
// 修复前
targetType: formData.targetType && formData.targetType.length > 0 ? JSON.stringify(formData.targetType) : null

// 修复后
targetType: formData.targetType && formData.targetType.length > 0 ? formData.targetType : ['ALL']
```

### 3. 数据库修复 (announcements 表)

**问题**: `target_type` 字段是 ENUM 类型，无法存储 JSON 数组

**修复**: 将字段类型修改为 `VARCHAR(500)`

```sql
ALTER TABLE announcements
MODIFY COLUMN target_type VARCHAR(500)
COMMENT '目标类型：JSON 数组，如 ["ALL","STUDENT"]。支持：STUDENT-全体学生，TEACHER-全体教师，TEACHER_TYPE-特定教师类别，MAJOR-特定专业学生，ALL-全体师生，COMPANY-企业用户';
```

### 4. Mapper 修复 (AnnouncementMapper.xml)

**问题**: SQL 查询使用精确匹配，不支持 JSON 数组格式

**修复**: 使用 `LIKE` 模糊匹配

```xml
<!-- 修复前 -->
a.target_type = 'ALL'

<!-- 修复后 -->
a.target_type LIKE '%ALL%'
```

## 测试步骤

1. **启动后端服务**
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. **启动前端服务**
   ```bash
   cd frontend
   npm run dev
   ```

3. **测试公告发布**
   - 登录管理员账号
   - 进入公告管理页面
   - 点击"新增公告"按钮
   - 填写公告信息（标题、内容、发布人、发布人身份等）
   - 选择目标群体（可多选）
   - 点击"确定"按钮

4. **验证结果**
   - 检查是否显示"新增成功"提示
   - 检查公告列表中是否显示新增的公告
   - 检查数据库中 `announcements` 表的 `target_type` 字段是否为 JSON 数组格式

## 预期结果

- 公告发布成功，无错误提示
- 数据库记录中 `target_type` 字段存储为 JSON 数组格式（如 `["STUDENT","TEACHER_TYPE"]`）
- 公告可以正常被目标用户群体查看

## 注意事项

1. 如果后端服务已经在运行，需要重启服务才能使代码修改生效
2. 数据库修改只需执行一次
3. 如果使用 Docker 或其他部署方式，需要重新构建镜像
