-- 验证所有79个学生是否都有简历和证书
SELECT 
    COUNT(DISTINCT sa.student_id) AS students_with_archives,
    (SELECT COUNT(DISTINCT student_id) FROM internship_application) AS total_students_in_applications,
    (SELECT COUNT(DISTINCT student_id) FROM internship_application) - COUNT(DISTINCT sa.student_id) AS students_missing_archives
FROM student_archives sa
WHERE sa.student_id IN (SELECT DISTINCT student_id FROM internship_application);

-- 查看哪些学生缺少档案
SELECT DISTINCT a.student_id
FROM internship_application a
WHERE a.student_id NOT IN (SELECT DISTINCT student_id FROM student_archives);

-- 统计每个学生的档案数量
SELECT 
    sa.student_id,
    COUNT(*) AS archive_count,
    SUM(CASE WHEN sa.file_type = 'Resume' THEN 1 ELSE 0 END) AS resume_count,
    SUM(CASE WHEN sa.file_type = 'Certificate' THEN 1 ELSE 0 END) AS certificate_count
FROM student_archives sa
WHERE sa.student_id IN (SELECT DISTINCT student_id FROM internship_application)
GROUP BY sa.student_id
ORDER BY sa.student_id;