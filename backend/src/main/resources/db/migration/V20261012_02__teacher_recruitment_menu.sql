-- Application menu configuration only: use the business account; no DDL or database grants.
INSERT INTO teacher_permission
    (permission_name, permission_code, permission_type, parent_id, path, icon, sort_order, create_time, update_time, deleted)
SELECT '招聘详情', 'menu:recruitment', 'MENU', 0, '/teacher/recruitment', 'Document',
       COALESCE((SELECT sort_order + 1 FROM teacher_permission
                 WHERE permission_code = 'menu:company-list' AND deleted = 0 LIMIT 1), 12), NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM teacher_permission WHERE permission_code = 'menu:recruitment');

INSERT INTO teacher_role_permission (role_id, permission_id, sort_order, create_time, deleted)
SELECT r.id, p.id,
       COALESCE((SELECT MAX(existing.sort_order) + 1 FROM teacher_role_permission existing
                 JOIN teacher_permission menu ON menu.id = existing.permission_id
                 WHERE existing.role_id = r.id AND existing.deleted = 0
                   AND menu.permission_code = 'menu:company-list' AND menu.deleted = 0), p.sort_order),
       NOW(), 0
FROM teacher_role r
JOIN teacher_permission p ON p.permission_code = 'menu:recruitment' AND p.deleted = 0
WHERE r.role_code IN ('COLLEGE', 'DEPARTMENT', 'COUNSELOR') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM teacher_role_permission existing
                  WHERE existing.role_id = r.id AND existing.permission_id = p.id AND existing.deleted = 0);
