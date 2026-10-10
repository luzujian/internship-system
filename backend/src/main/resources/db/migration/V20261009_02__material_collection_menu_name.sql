-- 使用业务账号执行，仅更改显示名称，保留菜单路径、权限码与所有角色授权。
UPDATE teacher_permission SET permission_name = '实习材料收集'
WHERE permission_code = 'menu:appraisal-collection' AND deleted = 0;
