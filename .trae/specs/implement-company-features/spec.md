# 企业端功能实现规范

## Why
企业端菜单栏的多个功能模块存在接口调用错误、数据获取失败等问题，需要系统性地修复和实现所有功能，确保企业用户能够正常使用系统的各项功能。

## What Changes
- 修复企业端首页统计数据、最新动态、通知公告的接口调用
- 修复招聘管理模块的岗位CRUD操作
- 修复实习确认模块的确认/拒绝功能
- 修复岗位申请查看模块的数据获取和查看详情功能
- 修复企业信息模块的基础信息和宣传资料编辑功能
- 修复账号设置模块的个人信息和密码修改功能
- 确保所有API接口正确对接后端Controller
- 优化错误处理和用户提示

## Impact
- Affected specs: 企业端所有功能模块
- Affected code: 
  - `frontend/src/views/company/CompanyHome.vue`
  - `frontend/src/views/company/RecruitmentManagement.vue`
  - `frontend/src/views/company/InternshipConfirm.vue`
  - `frontend/src/views/company/ApplicationView.vue`
  - `frontend/src/views/company/CompanyInfo.vue`
  - `frontend/src/views/company/AccountSettings.vue`
  - `frontend/src/api/CompanyService.ts`
  - `frontend/src/store/position.js`
  - `backend/src/main/java/com/gdmu/controller/CompanyController.java`
  - `backend/src/main/java/com/gdmu/controller/PositionController.java`
  - `backend/src/main/java/com/gdmu/controller/StudentInternshipStatusController.java`

## ADDED Requirements

### Requirement: 企业端首页功能
系统 SHALL 提供企业端首页，显示统计数据、快捷操作、最新动态和通知公告。

#### Scenario: 首页统计数据加载成功
- **WHEN** 企业用户登录并访问首页
- **THEN** 系统应调用 `/api/company/stats` 接口获取统计数据
- **AND** 显示发布职位数、收到岗位申请数、待确认数、已确认数
- **AND** 数据应正确显示在统计卡片中

#### Scenario: 首页最新动态加载成功
- **WHEN** 企业用户访问首页
- **THEN** 系统应调用 `/api/company/applications/recent` 接口获取最新动态
- **AND** 显示最近5条学生申请记录
- **AND** 每条记录应显示学生姓名、岗位名称、申请时间和状态

#### Scenario: 首页通知公告加载成功
- **WHEN** 企业用户访问首页
- **THEN** 系统应调用 `/api/company/notifications` 接口获取通知公告
- **AND** 显示已发布且对企业可见的公告
- **AND** 每条公告应显示标题、内容、时间和优先级

### Requirement: 招聘管理功能
系统 SHALL 提供完整的招聘管理功能，包括岗位的发布、编辑、暂停、恢复和删除。

#### Scenario: 发布岗位成功
- **WHEN** 企业用户填写完整岗位信息并点击发布
- **THEN** 系统应调用 `/api/company/positions` POST 接口
- **AND** 岗位信息应保存到数据库
- **AND** 返回成功提示

#### Scenario: 编辑岗位成功
- **WHEN** 企业用户修改岗位信息并保存
- **THEN** 系统应调用 `/api/company/positions/{id}` PUT 接口
- **AND** 岗位信息应更新到数据库
- **AND** 返回成功提示

#### Scenario: 暂停岗位招聘成功
- **WHEN** 企业用户点击暂停按钮
- **THEN** 系统应调用 `/api/admin/positions/{id}/pause` PUT 接口
- **AND** 岗位状态应更新为 paused
- **AND** 返回成功提示

#### Scenario: 恢复岗位招聘成功
- **WHEN** 企业用户点击恢复按钮
- **THEN** 系统应调用 `/api/admin/positions/{id}/resume` PUT 接口
- **AND** 岗位状态应更新为 active
- **AND** 返回成功提示

#### Scenario: 删除岗位成功
- **WHEN** 企业用户确认删除岗位
- **THEN** 系统应调用 `/api/company/positions/{id}` DELETE 接口
- **AND** 岗位应从数据库中删除
- **AND** 返回成功提示

### Requirement: 实习确认功能
系统 SHALL 提供实习确认功能，允许企业确认或拒绝学生的实习申请。

#### Scenario: 确认实习申请成功
- **WHEN** 企业用户点击确认按钮
- **THEN** 系统应调用 `/api/admin/internship-status/{id}/approve` PUT 接口
- **AND** 实习状态应更新为已确认
- **AND** 岗位招聘人数应相应减一
- **AND** 返回成功提示

#### Scenario: 拒绝实习申请成功
- **WHEN** 企业用户点击拒绝按钮
- **THEN** 系统应调用 `/api/admin/internship-status/{id}/reject` PUT 接口
- **AND** 实习状态应更新为已拒绝
- **AND** 返回成功提示

#### Scenario: 加载实习确认列表成功
- **WHEN** 企业用户访问实习确认页面
- **THEN** 系统应调用 `/api/admin/internship-status/company/{companyId}` GET 接口
- **AND** 显示所有该企业的实习申请记录
- **AND** 按状态排序显示

### Requirement: 岗位申请查看功能
系统 SHALL 提供岗位申请查看功能，允许企业查看学生提交的岗位申请详情。

#### Scenario: 加载岗位申请列表成功
- **WHEN** 企业用户访问岗位申请查看页面
- **THEN** 系统应调用 `/api/admin/applications/company/{companyId}` GET 接口
- **AND** 显示所有该企业的岗位申请记录
- **AND** 按岗位分组显示统计数据

#### Scenario: 查看学生申请详情成功
- **WHEN** 企业用户点击查看详情按钮
- **THEN** 系统应调用 `/api/student-archives/student/{studentDbId}` GET 接口获取学生档案
- **AND** 显示学生基本信息、专业技能、项目经验、自我评价
- **AND** 显示学生上传的简历和证书

#### Scenario: 标记申请已读成功
- **WHEN** 企业用户查看申请详情
- **THEN** 系统应调用 `/api/admin/applications/{id}/view` PUT 接口
- **AND** 申请状态应更新为已查看

#### Scenario: 导出申请数据成功
- **WHEN** 企业用户点击导出按钮
- **THEN** 系统应生成Excel文件
- **AND** 文件应包含所有申请记录的详细信息
- **AND** 浏览器应自动下载文件

### Requirement: 企业信息管理功能
系统 SHALL 提供企业信息管理功能，允许企业编辑基础信息和宣传资料。

#### Scenario: 加载企业信息成功
- **WHEN** 企业用户访问企业信息页面
- **THEN** 系统应调用 `/api/company/info?companyId={companyId}` GET 接口
- **AND** 显示企业基础信息和宣传资料
- **AND** 省市区选择器应正确显示当前地址

#### Scenario: 保存基础信息成功
- **WHEN** 企业用户修改基础信息并保存
- **THEN** 系统应调用 `/api/company/info` PUT 接口
- **AND** 企业信息应更新到数据库
- **AND** 返回成功提示

#### Scenario: 保存宣传资料成功
- **WHEN** 企业用户上传logo、照片、视频或修改介绍并保存
- **THEN** 系统应先调用上传接口获取文件URL
- **AND** 然后调用 `/api/company/info` PUT 接口保存
- **AND** 宣传资料应更新到数据库
- **AND** 返回成功提示

### Requirement: 账号设置功能
系统 SHALL 提供账号设置功能，允许企业修改个人信息和密码。

#### Scenario: 加载个人信息成功
- **WHEN** 企业用户访问账号设置页面
- **THEN** 系统应调用 `/api/company/profile` GET 接口
- **AND** 显示当前用户名、手机号、邮箱
- **AND** 显示企业名称、所属行业、企业规模

#### Scenario: 修改个人信息成功
- **WHEN** 企业用户修改个人信息并保存
- **THEN** 系统应调用 `/api/company/profile` PUT 接口
- **AND** 个人信息应更新到数据库
- **AND** 返回成功提示

#### Scenario: 修改密码成功
- **WHEN** 企业用户输入原密码和新密码并确认
- **THEN** 系统应调用 `/api/company/password` PUT 接口
- **AND** 验证原密码是否正确
- **AND** 新密码应更新到数据库
- **AND** 返回成功提示

#### Scenario: 退出登录成功
- **WHEN** 企业用户点击退出登录按钮
- **THEN** 系统应清除登录状态
- **AND** 跳转到登录页面
- **AND** 返回成功提示

## MODIFIED Requirements

### Requirement: Position Store
修改 position store 以正确处理企业端的岗位、申请和实习状态数据。

- 添加 `fetchApplications(companyId)` 方法获取岗位申请列表
- 添加 `fetchInternshipStatuses(companyId)` 方法获取实习状态列表
- 添加 `markAsViewed(applicationId)` 方法标记申请已读
- 添加 `approveInternshipStatus(statusId)` 方法确认实习
- 添加 `rejectInternshipStatus(statusId)` 方法拒绝实习
- 修复 `addPosition`、`updatePosition`、`deletePosition` 方法的API调用
- 添加 `pausePosition` 和 `resumePosition` 方法

### Requirement: Company Service API
修改 CompanyService API 以正确对接后端接口。

- 修复 `getCompanyInfo` 方法的参数传递
- 确保 `updateCompanyInfo` 方法正确处理所有字段
- 确保 `getStats` 方法正确获取统计数据
- 确保 `getRecentApplications` 方法正确获取最新动态
- 确保 `getNotifications` 方法正确获取通知公告
- 确保 `getProfile` 和 `updateProfile` 方法正确处理个人信息
- 确保 `changePassword` 方法正确处理密码修改

## REMOVED Requirements
无
