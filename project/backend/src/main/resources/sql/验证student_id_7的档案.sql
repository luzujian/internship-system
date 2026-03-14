-- 验证student_id为7的学生档案
SELECT 
    id,
    student_id,
    file_name,
    file_type,
    file_url,
    upload_time
FROM student_archives
WHERE student_id = 7
ORDER BY id;

-- 统计student_id为7的学生档案数量
SELECT 
    student_id,
    COUNT(*) AS total_files,
    SUM(CASE WHEN file_type = '简历' THEN 1 ELSE 0 END) AS resume_count,
    SUM(CASE WHEN file_type = '证书' THEN 1 ELSE 0 END) AS certificate_count
FROM student_archives
WHERE student_id = 7
GROUP BY student_id;
