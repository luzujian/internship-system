-- 更新学生年级数据为学号前四位
-- 执行时间：2026-03-16
-- 目的：确保学生的年级字段与学号前四位保持一致

-- 更新所有学生的年级为学号前四位
UPDATE student_users 
SET grade = LEFT(student_user_id, 4) 
WHERE grade != LEFT(student_user_id, 4) OR grade IS NULL;

-- 验证更新结果
SELECT 
    id,
    student_user_id,
    name,
    grade,
    LEFT(student_user_id, 4) as expected_grade,
    CASE 
        WHEN grade = LEFT(student_user_id, 4) THEN '一致'
        ELSE '不一致'
    END as status
FROM student_users 
ORDER BY id;

-- 统计各年级学生数量
SELECT 
    grade,
    COUNT(*) as student_count
FROM student_users 
GROUP BY grade 
ORDER BY grade;
