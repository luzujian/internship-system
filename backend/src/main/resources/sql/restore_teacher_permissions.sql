-- 恢复教师端缺失的权限
-- 生成时间：2026-03-19
-- 说明：为不同教师角色补充必要的权限，确保教师端页面正常显示

-- ========================================
-- 1. 为基础教师(ROLE_TEACHER)补充权限
-- ========================================

-- 基础教师应该有的权限：
-- - 查看公告 (已有)
-- - 查看资源 (已有)
-- - 查看统计 (已有)
-- - 查看学生 (已有)
-- - 查看班级 (缺失)
-- - 查看院系 (缺失)
-- - 查看专业 (缺失)
-- - 查看企业 (缺失)
-- - 查看岗位类别 (缺失)
-- - 查看招聘信息 (缺失)

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
('ROLE_TEACHER', 'class:view', NOW()),
('ROLE_TEACHER', 'department:view', NOW()),
('ROLE_TEACHER', 'major:view', NOW()),
('ROLE_TEACHER', 'user:company:view', NOW()),
('ROLE_TEACHER', 'position-category:view', NOW()),
('ROLE_TEACHER', 'recruitment:view', NOW());

-- ========================================
-- 2. 为学院教师(ROLE_TEACHER_COLLEGE)补充权限
-- ========================================

-- 学院教师应该有的权限：
-- - 公告管理 (已有)
-- - 班级管理 (已有)
-- - 院系专业管理 (已有)
-- - 查看资源 (已有)
-- - 查看统计 (已有)
-- - 学生管理 (已有)
-- - 查看教师 (已有)
-- - 查看企业 (缺失)
-- - 查看岗位类别 (缺失)
-- - 查看招聘信息 (缺失)
-- - 查看撤回申请 (缺失)
-- - 实习确认表查看 (缺失)

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
('ROLE_TEACHER_COLLEGE', 'user:company:view', NOW()),
('ROLE_TEACHER_COLLEGE', 'user:company:audit', NOW()),
('ROLE_TEACHER_COLLEGE', 'position-category:view', NOW()),
('ROLE_TEACHER_COLLEGE', 'recruitment:view', NOW()),
('ROLE_TEACHER_COLLEGE', 'company:recall:view', NOW()),
('ROLE_TEACHER_COLLEGE', 'company:recall:approve', NOW()),
('ROLE_TEACHER_COLLEGE', 'internship:recall:view', NOW()),
('ROLE_TEACHER_COLLEGE', 'internship:recall:approve', NOW()),
('ROLE_TEACHER_COLLEGE', 'internship:confirm:view', NOW());

-- ========================================
-- 3. 为系室教师(ROLE_TEACHER_DEPARTMENT)补充权限
-- ========================================

-- 系室教师应该有的权限：
-- - 公告管理 (已有)
-- - 班级管理 (已有)
-- - 专业管理 (已有)
-- - 查看资源 (已有)
-- - 查看统计 (已有)
-- - 企业审核 (已有)
-- - 学生管理 (已有)
-- - 查看教师 (已有)
-- - 查看院系 (缺失)
-- - 查看企业 (缺失)
-- - 查看岗位类别 (缺失)
-- - 查看招聘信息 (缺失)
-- - 查看撤回申请 (缺失)
-- - 实习确认表查看 (缺失)

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
('ROLE_TEACHER_DEPARTMENT', 'department:view', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'user:company:view', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'position-category:view', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'recruitment:view', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'company:recall:view', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'company:recall:approve', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'internship:recall:view', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'internship:recall:approve', NOW()),
('ROLE_TEACHER_DEPARTMENT', 'internship:confirm:view', NOW());

-- ========================================
-- 4. 为辅导员(ROLE_TEACHER_COUNSELOR)补充权限
-- ========================================

-- 辅导员应该有的权限：
-- - 公告管理 (已有)
-- - 查看资源 (已有)
-- - 查看统计 (已有)
-- - 学生管理 (已有)
-- - 查看班级 (缺失)
-- - 查看院系 (缺失)
-- - 查看专业 (缺失)
-- - 查看企业 (缺失)
-- - 查看岗位类别 (缺失)
-- - 查看招聘信息 (缺失)
-- - 查看撤回申请 (缺失)
-- - 实习确认表查看 (缺失)

INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES 
('ROLE_TEACHER_COUNSELOR', 'class:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'department:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'major:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'user:company:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'position-category:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'recruitment:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'company:recall:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'company:recall:approve', NOW()),
('ROLE_TEACHER_COUNSELOR', 'internship:recall:view', NOW()),
('ROLE_TEACHER_COUNSELOR', 'internship:recall:approve', NOW()),
('ROLE_TEACHER_COUNSELOR', 'internship:confirm:view', NOW());

-- ========================================
-- 验证权限恢复结果
-- ========================================

-- 查看基础教师的权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER'
ORDER BY p.module, p.permission_code;

-- 查看学院教师的权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_COLLEGE'
ORDER BY p.module, p.permission_code;

-- 查看系室教师的权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_DEPARTMENT'
ORDER BY p.module, p.permission_code;

-- 查看辅导员的权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_COUNSELOR'
ORDER BY p.module, p.permission_code;