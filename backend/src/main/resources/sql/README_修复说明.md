# 企业基础信息字段保留问题修复

## 问题描述
企业端基础信息字段（industry, scale, province, city, district, detailAddress, website, cooperationMode, logo, photos, videos）在保存后刷新页面被重置。

## 修复内容

### 1. 前端修复 (CompanyInfo.vue)
**问题**: `loadCompanyInfo` 函数中直接替换了整个表单对象，导致 Vue 响应式引用丢失

**修复**:
```javascript
// 修复前
basicForm.value = { ... }

// 修复后
Object.assign(basicForm.value, { ... })
```

### 2. 后端 Mapper 配置修复 (CompanyUserMapper.xml)
**问题**: ResultMap 和 update/insert 语句缺少新增字段的映射

**修复**:
- 在 `ResultMap` 中添加了 11 个新字段的映射
- 在 `insert` 和 `batchInsert` 语句中添加了这些字段
- 在 `update` 语句中添加了这些字段的动态更新支持

### 3. 后端 Service 层修复 (CompanyUserServiceImpl.java)
**问题**: `update` 方法中没有复制新增的基础信息字段到 existingCompany 对象

**修复**:
添加了以下字段的复制逻辑：
- industry
- scale
- province
- city
- district
- detailAddress
- website
- cooperationMode
- logo
- photos
- videos

## 测试步骤

1. **启动后端服务**:
```bash
cd backend
mvn spring-boot:run
```

2. **访问企业端信息管理页面**:
- 打开浏览器访问：http://localhost:5173/company/company-info
- 使用企业账号登录

3. **填写基础信息**:
- 所属行业
- 企业规模
- 企业地址（省/市/区）
- 详细地址
- 企业网站
- 合作模式
- 企业 Logo
- 企业照片
- 企业视频

4. **保存并验证**:
- 点击"保存基础信息"或"保存全部"按钮
- 等待保存成功提示
- 刷新页面
- 验证所有字段是否正确显示

## 数据库验证

执行以下 SQL 验证数据是否正确保存：

```sql
-- 设置 UTF-8 编码
SET NAMES utf8mb4;

-- 查看企业 ID=3 的数据
SELECT
    id,
    company_name AS 企业名称，
    industry AS 行业，
    scale AS 规模，
    province AS 省份，
    city AS 城市，
    district AS 区县，
    detail_address AS 详细地址，
    website AS 网站，
    cooperation_mode AS 合作模式，
    logo AS Logo,
    photos AS 照片，
    videos AS 视频，
    introduction AS 企业简介
FROM company_users
WHERE id = 3;
```

## 相关文件

- `frontend/src/views/company/CompanyInfo.vue` - 前端企业信息管理页面
- `backend/src/main/resources/com/gdmu/mapper/CompanyUserMapper.xml` - MyBatis Mapper 配置
- `backend/src/main/java/com/gdmu/service/impl/CompanyUserServiceImpl.java` - Service 层实现
- `backend/src/main/java/com/gdmu/controller/CompanyController.java` - Controller 层

## 修改清单

1. ✅ 前端：使用 Object.assign 保持响应式引用
2. ✅ Mapper: ResultMap 添加字段映射
3. ✅ Mapper: insert 语句添加字段
4. ✅ Mapper: update 语句添加字段
5. ✅ Service: update 方法添加字段复制逻辑
