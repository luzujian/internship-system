-- 为系室教师添加企业审核权限

-- 1. 检查并插入企业审核权限（如果不存在）
INSERT IGNORE INTO permissions (permission_code, permission_name, module, description, create_time, update_time)
VALUES ('user:company:audit', '企业审核', '用户管理-企业管理', '审核企业注册申请', NOW(), NOW());

-- 2. 为系室教师角色添加企业审核权限
INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES ('ROLE_TEACHER_DEPARTMENT', 'user:company:audit', NOW());

-- 3. 验证权限是否添加成功
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_DEPARTMENT' 
  AND p.permission_code = 'user:company:audit';

-- 4. 查看系室教师的所有权限
SELECT 
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_TEACHER_DEPARTMENT'
ORDER BY p.module, p.permission_name;
