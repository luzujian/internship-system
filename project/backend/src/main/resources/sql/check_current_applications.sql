-- 检查当前学生的申请情况
SELECT '========== Part 1: 检查当前学生的申请情况 ==========' as status;

SELECT 
    su.id AS student_id,
    su.name AS student_name,
    su.student_user_id,
    COUNT(ia.id) AS application_count
FROM student_users su
LEFT JOIN internship_application ia ON su.id = ia.student_id
GROUP BY su.id, su.name, su.student_user_id
ORDER BY su.id;

-- 检查当前每个岗位的申请数量
SELECT '========== Part 2: 检查当前每个岗位的申请数量 ==========' as status;

SELECT 
    p.id AS position_id,
    p.position_name,
    p.company_id,
    COUNT(ia.id) AS application_count
FROM position p
LEFT JOIN internship_application ia ON p.id = ia.position_id
WHERE p.company_id = 3
GROUP BY p.id, p.position_name, p.company_id
ORDER BY p.id;

-- 统计总申请数量
SELECT '========== Part 3: 统计总申请数量 ==========' as status;

SELECT 
    COUNT(*) AS total_applications
FROM internship_application
WHERE company_id = 3;
