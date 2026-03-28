-- 简化教师端权限系统
-- 生成时间：2026-03-19
-- 说明：只保留菜单级别的权限，用于控制教师端菜单显示

-- ========================================
-- 1. 创建菜单权限（如果不存在）
-- ========================================

-- 通用菜单权限（所有教师都有）
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES 
('menu:teacherHome', '首页', '访问首页', '菜单-通用', NOW(), NOW()),
('menu:teacherDashboard', '实习状态看板', '访问实习状态看板', '菜单-通用', NOW(), NOW()),
('menu:teacherResources', '资源管理', '访问资源管理', '菜单-通用', NOW(), NOW()),
('menu:teacherAccountSettings', '账号设置', '访问账号设置', '菜单-通用', NOW(), NOW()),
('menu:teacherProfile', '个人中心', '访问个人中心', '菜单-通用', NOW(), NOW());

-- 学院教师专属菜单权限
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES 
('menu:teacherApproval', '审核管理', '访问审核管理', '菜单-学院教师', NOW(), NOW()),
('menu:teacherEvaluation', '智慧评分', '访问智慧评分', '菜单-学院教师', NOW(), NOW()),
('menu:teacherAnnouncements', '公告管理', '访问公告管理', '菜单-学院教师', NOW(), NOW()),
('menu:teacherCompanyList', '企业列表', '访问企业列表', '菜单-学院教师', NOW(), NOW()),
('menu:teacherStudents', '学生管理', '访问学生管理', '菜单-学院教师', NOW(), NOW()),
('menu:teacherReports', '统计报表', '访问统计报表', '菜单-学院教师', NOW(), NOW()),
('menu:teacherSettings', '系统设置', '访问系统设置', '菜单-学院教师', NOW(), NOW()),
('menu:teacherTimelineConfig', '时间节点设置', '访问时间节点设置', '菜单-学院教师', NOW(), NOW()),
('menu:teacherClasses', '班级管理', '访问班级管理', '菜单-学院教师', NOW(), NOW()),
('menu:teacherInternship', '实习管理', '访问实习管理', '菜单-学院教师', NOW(), NOW());

-- 系室教师专属菜单权限
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES 
('menu:teacherCompanyAudit', '企业审核', '访问企业审核', '菜单-系室教师', NOW(), NOW()),
('menu:teacherStudentApplicationAudit', '学生申请审核', '访问学生申请审核', '菜单-系室教师', NOW(), NOW());

-- 辅导员专属菜单权限
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES
('menu:teacherKeyTimelineConfig', '关键时间节点设置', '访问关键时间节点设置', '菜单-辅导员', NOW(), NOW()),
('menu:teacherAiKeywordConfig', 'AI关键词配置', '访问AI关键词配置', '菜单-辅导员', NOW(), NOW()),
('menu:teacherInternshipReports', '实习心得', '访问实习心得', '菜单-辅导员', NOW(), NOW()),
('menu:teacherGradeFinalize', '成绩评定', '访问成绩评定', '菜单-辅导员', NOW(), NOW()),
('menu:teacherScoringRules', '评分规则管理', '访问评分规则管理', '菜单-辅导员', NOW(), NOW()),
('menu:teacherKeywordLibrary', '关键词库管理', '访问关键词库管理', '菜单-辅导员', NOW(), NOW()),
('menu:teacherReflectionEvaluation', '实习心得评分', '访问实习心得评分', '菜单-辅导员', NOW(), NOW()),
('menu:teacherAiScoringConfigPage', 'AI评分配置', '访问AI评分配置', '菜单-辅导员', NOW(), NOW());

-- ========================================
-- 2. 为基础教师(ROLE_TEACHER)分配菜单权限
-- ========================================

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
('ROLE_TEACHER', 'menu:teacherHome', NOW()),
('ROLE_TEACHER', 'menu:teacherDashboard', NOW()),
('ROLE_TEACHER', 'menu:teacherResources', NOW()),
('ROLE_TEACHER', 'menu:teacherAccountSettings', NOW()),
('ROLE_TEACHER', 'menu:teacherProfile', NOW());

-- ========================================
-- 3. 为学院教师(ROLE_TEACHER_COLLEGE)分配菜单权限
-- ========================================

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
-- 通用权限
('ROLE_TEACHER_COLLEGE', 'menu:teacherHome', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherDashboard', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherResources', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherAccountSettings', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherProfile', NOW()),
-- 学院教师专属权限
('ROLE_TEACHER_COLLEGE', 'menu:teacherApproval', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherEvaluation', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherAnnouncements', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherCompanyList', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherStudents', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherReports', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherSettings', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherTimelineConfig', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherClasses', NOW()),
('ROLE_TEACHER_COLLEGE', 'menu:teacherInternship', NOW());

-- ========================================
-- 4. 为系室教师(ROLE_TEACHER_DEPARTMENT)分配菜单权限
-- ========================================

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
-- 通用权限
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherHome', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherDashboard', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherResources', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherAccountSettings', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherProfile', NOW()),
-- 学院教师专属权限
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherApproval', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherEvaluation', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherAnnouncements', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherCompanyList', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherStudents', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherReports', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherClasses', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherInternship', NOW()),
-- 系室教师专属权限
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherCompanyAudit', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'menu:teacherStudentApplicationAudit', NOW());

-- ========================================
-- 5. 为辅导员(ROLE_TEACHER_COUNSELOR)分配菜单权限
-- ========================================

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
-- 通用权限
('ROLE_TEACHER_COUNSELOR', 'menu:teacherHome', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherDashboard', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherResources', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherAccountSettings', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherProfile', NOW()),
-- 学院教师专属权限
('ROLE_TEACHER_COUNSELOR', 'menu:teacherApproval', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherEvaluation', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherAnnouncements', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherCompanyList', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherStudents', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherReports', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherClasses', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherInternship', NOW()),
-- 辅导员专属权限
('ROLE_TEACHER_COUNSELOR', 'menu:teacherKeyTimelineConfig', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherAiKeywordConfig', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherInternshipReports', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherGradeFinalize', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherScoringRules', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherKeywordLibrary', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherReflectionEvaluation', NOW()),
('ROLE_TEACHER_COUNSELOR', 'menu:teacherAiScoringConfigPage', NOW());

-- ========================================
-- 验证菜单权限分配结果
-- ========================================

-- 查看基础教师的菜单权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER' AND p.permission_code LIKE 'menu:%'
ORDER BY p.module, p.permission_code;

-- 查看学院教师的菜单权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_COLLEGE' AND p.permission_code LIKE 'menu:%'
ORDER BY p.module, p.permission_code;

-- 查看系室教师的菜单权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_DEPARTMENT' AND p.permission_code LIKE 'menu:%'
ORDER BY p.module, p.permission_code;

-- 查看辅导员的菜单权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_COUNSELOR' AND p.permission_code LIKE 'menu:%'
ORDER BY p.module, p.permission_code;