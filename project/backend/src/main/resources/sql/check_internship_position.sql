-- 检查 student_internship_status 表中 position_id 为 NULL 的记录
SELECT '========== Part 1: 检查 position_id 为 NULL 的记录 ==========' as status;

SELECT 
    sis.id,
    su.name AS student_name,
    su.student_user_id,
    sis.position_id,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
WHERE sis.position_id IS NULL;

-- 检查 student_internship_status 表中 position_id 不为 NULL 的记录
SELECT '========== Part 2: 检查 position_id 不为 NULL 的记录 ==========' as status;

SELECT 
    sis.id,
    su.name AS student_name,
    su.student_user_id,
    sis.position_id,
    p.position_name,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.position_id IS NOT NULL
LIMIT 10;

-- 统计 position_id 为 NULL 的记录数量
SELECT '========== Part 3: 统计 position_id 为 NULL 的记录数量 ==========' as status;

SELECT 
    COUNT(*) AS null_position_count
FROM student_internship_status
WHERE position_id IS NULL;

-- 检查 internship_application 表中是否有对应的 position_id
SELECT '========== Part 4: 检查 internship_application 表中的数据 ==========' as status;

SELECT 
    ia.id,
    su.name AS student_name,
    su.student_user_id,
    ia.position_id,
    p.position_name,
    ia.status
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
ORDER BY ia.id
LIMIT 20;
