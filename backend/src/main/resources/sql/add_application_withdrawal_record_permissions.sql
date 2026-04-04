-- 添加撤回申请记录模块的权限

-- 首先，将旧的"撤回申请审核"模块权限更新为"撤回申请记录"模块
UPDATE permissions SET module = '撤回申请记录-撤回申请记录管理' WHERE permission_code LIKE 'application:withdrawal:%';

-- 如果不存在对应的权限记录，则插入
INSERT INTO permissions (module, permission_code, permission_name)
SELECT '撤回申请记录-撤回申请记录管理', 'application:withdrawal:view', '查看撤回申请记录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code = 'application:withdrawal:view');

INSERT INTO permissions (module, permission_code, permission_name)
SELECT '撤回申请记录-撤回申请记录管理', 'application:withdrawal:delete', '删除撤回申请记录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code = 'application:withdrawal:delete');

-- 为管理员角色分配新的权限（如果尚未分配）
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ROLE_ADMIN', 'application:withdrawal:view'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_code = 'ROLE_ADMIN' AND permission_code = 'application:withdrawal:view');

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ROLE_ADMIN', 'application:withdrawal:delete'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_code = 'ROLE_ADMIN' AND permission_code = 'application:withdrawal:delete');

-- 查看更新后的权限结构
SELECT module, GROUP_CONCAT(permission_code ORDER BY permission_code) as permissions
FROM permissions
GROUP BY module
ORDER BY
  CASE
    WHEN module LIKE '用户管理%' THEN 1
    WHEN module LIKE '撤回申请记录%' THEN 2
    WHEN module LIKE '撤回申请审核%' THEN 3
    WHEN module LIKE '基础数据管理%' THEN 4
    WHEN module = '公告管理' THEN 5
    WHEN module = '资源文档管理' THEN 6
    WHEN module = '问题反馈管理' THEN 7
    WHEN module = '数据统计' THEN 8
    WHEN module LIKE 'AI 配置%' THEN 9
    WHEN module LIKE '系统管理%' THEN 10
    ELSE 11
  END,
  module;
