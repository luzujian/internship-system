-- 查询赵强学生的ID
SELECT 
    id,
    name,
    student_user_id
FROM student_users
WHERE name = '赵强' OR student_user_id = '20200007';

-- 查看赵强的申请记录
SELECT 
    ia.id AS application_id,
    ia.student_id,
    su.name AS student_name,
    su.student_user_id,
    p.position_name
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
WHERE su.name = '赵强' OR su.student_user_id = '20200007';

-- 查看赵强的档案记录
SELECT 
    sa.id,
    sa.student_id,
    su.name AS student_name,
    sa.file_name,
    sa.file_type,
    sa.file_url
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
WHERE su.name = '赵强' OR su.student_user_id = '20200007';
