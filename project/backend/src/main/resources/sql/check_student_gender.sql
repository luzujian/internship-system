-- 检查 student_users 表中 gender 字段的数据
SELECT 
    id,
    name,
    student_user_id,
    gender
FROM student_users
ORDER BY id
LIMIT 20;

-- 统计 gender 字段的分布
SELECT 
    gender,
    COUNT(*) AS count
FROM student_users
GROUP BY gender;
