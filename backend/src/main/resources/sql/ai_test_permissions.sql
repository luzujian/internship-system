-- AI测试权限SQL
INSERT INTO `permissions` (`permission_code`, `permission_name`, `permission_desc`, `module`) VALUES
('ai:test:view', '查看AI测试', '查看AI测试页面', 'AI配置');

-- 为管理员角色分配AI测试权限
INSERT INTO `role_permissions` (`role_code`, `permission_code`) VALUES
('ROLE_ADMIN', 'ai:test:view');
