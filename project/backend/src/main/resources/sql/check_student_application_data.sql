-- 查看申请岗位的学生信息
-- 这个查询会显示哪些学生申请了岗位，以及他们的ID

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
LIMIT 10;

-- 查看我们添加了档案的学生
SELECT 
    sa.id AS archive_id,
    sa.student_id,
    su.name AS student_name,
    sa.file_name,
    sa.file_type,
    COUNT(*) AS file_count
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
GROUP BY sa.student_id, su.name
ORDER BY sa.student_id;
