-- 查看实际申请岗位的学生
-- 这个查询会显示真实的学生信息

SELECT 
    ia.id AS application_id,
    ia.student_id,
    su.name AS student_name,
    su.student_user_id,
    p.position_name,
    ia.apply_time
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
ORDER BY ia.apply_time DESC
LIMIT 20;
