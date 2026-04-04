-- 资源文档权限SQL
INSERT INTO `permissions` (`permission_code`, `permission_name`, `permission_desc`, `module`) VALUES
('resource:view', '查看资源文档', '查看资源文档信息', '资源文档管理'),
('resource:add', '添加资源文档', '添加资源文档信息', '资源文档管理'),
('resource:edit', '编辑资源文档', '编辑资源文档信息', '资源文档管理'),
('resource:delete', '删除资源文档', '删除资源文档信息', '资源文档管理');

-- 为管理员角色分配资源文档权限
INSERT INTO `role_permissions` (`role_code`, `permission_code`) VALUES
('ROLE_ADMIN', 'resource:view'),
('ROLE_ADMIN', 'resource:add'),
('ROLE_ADMIN', 'resource:edit'),
('ROLE_ADMIN', 'resource:delete');

-- 为教师角色分配查看资源文档权限
INSERT INTO `role_permissions` (`role_code`, `permission_code`) VALUES
('ROLE_TEACHER', 'resource:view'),
('ROLE_TEACHER_COLLEGE', 'resource:view'),
('ROLE_TEACHER_DEPARTMENT', 'resource:view'),
('ROLE_TEACHER_COUNSELOR', 'resource:view');
