-- 为企业用户添加密码重置权限、企业编辑权限和企业撤回申请审核权限

-- 1. 检查并插入企业密码重置权限（如果不存在）
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES ('user:company:reset', '企业密码重置', '重置企业用户密码', '用户管理 - 企业管理', NOW(), NOW());

-- 2. 检查并插入企业编辑权限（如果不存在）
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES ('user:company:edit', '企业编辑', '编辑企业基本信息', '用户管理 - 企业管理', NOW(), NOW());

-- 3. 检查并插入企业撤回申请审核权限（如果不存在）
INSERT IGNORE INTO permissions (permission_code, permission_name, permission_desc, module, create_time, update_time)
VALUES ('company:recall:audit', '企业撤回申请审核', '审核企业撤回申请', '撤回申请审核 - 企业撤回申请审核', NOW(), NOW());

-- 4. 为管理员角色添加企业密码重置权限
INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES ('ROLE_ADMIN', 'user:company:reset', NOW());

-- 5. 为管理员角色添加企业编辑权限
INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES ('ROLE_ADMIN', 'user:company:edit', NOW());

-- 6. 为管理员角色添加企业撤回申请审核权限
INSERT IGNORE INTO role_permissions (role_code, permission_code, create_time)
VALUES ('ROLE_ADMIN', 'company:recall:audit', NOW());

-- 7. 验证权限是否添加成功
SELECT
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.permission_desc,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('user:company:reset', 'user:company:edit', 'company:recall:audit');

-- 8. 查看管理员的所有企业相关权限
SELECT
  rp.role_code,
  p.permission_code,
  p.permission_name,
  p.module
FROM role_permissions rp
JOIN permissions p ON rp.permission_code = p.permission_code
WHERE rp.role_code = 'ROLE_ADMIN'
  AND p.module LIKE '%企业%'
ORDER BY p.module, p.permission_name;
