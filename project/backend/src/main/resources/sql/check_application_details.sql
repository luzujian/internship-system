-- 检查 internship_application 表中的 skills、experience、self_evaluation 字段
SELECT 
    id,
    student_id,
    position_id,
    skills,
    experience,
    self_evaluation
FROM internship_application
ORDER BY id
LIMIT 10;

-- 统计这些字段为空的记录数量
SELECT 
    COUNT(*) AS total_count,
    SUM(CASE WHEN skills IS NULL OR skills = '' THEN 1 ELSE 0 END) AS empty_skills,
    SUM(CASE WHEN experience IS NULL OR experience = '' THEN 1 ELSE 0 END) AS empty_experience,
    SUM(CASE WHEN self_evaluation IS NULL OR self_evaluation = '' THEN 1 ELSE 0 END) AS empty_self_evaluation
FROM internship_application;
