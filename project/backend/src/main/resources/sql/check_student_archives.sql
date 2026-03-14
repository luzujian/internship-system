-- 检查 student_archives 表结构
DESCRIBE student_archives;

-- 查看现有的学生归档文件数据
SELECT 
    sa.id,
    sa.student_id,
    su.name AS student_name,
    su.student_user_id,
    sa.file_name,
    sa.file_type,
    sa.file_url,
    sa.upload_time
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
ORDER BY sa.upload_time DESC
LIMIT 20;

-- 查看哪些学生申请了岗位
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

-- 查看是否有学生已经上传了文件
SELECT COUNT(*) AS total_archives,
       COUNT(DISTINCT student_id) AS students_with_files
FROM student_archives;
