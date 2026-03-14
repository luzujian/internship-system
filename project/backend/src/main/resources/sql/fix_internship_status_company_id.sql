-- 修复 student_internship_status 表中的 company_id
-- 将其更新为 position.company_id 的值

SELECT '========== Part 1: 修复前的数据 ==========' as status;

SELECT 
    sis.id AS status_id,
    sis.company_id AS old_company_id,
    sis.position_id,
    p.company_id AS correct_company_id,
    p.position_name,
    su.name AS student_name,
    su.student_user_id,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
ORDER BY sis.id
LIMIT 10;

-- 更新 student_internship_status 表中的 company_id
UPDATE student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
SET sis.company_id = p.company_id
WHERE sis.company_id != p.company_id;

SELECT '========== Part 2: 修复后的数据 ==========' as status;

SELECT 
    sis.id AS status_id,
    sis.company_id AS new_company_id,
    sis.position_id,
    p.company_id AS position_company_id,
    CASE 
        WHEN sis.company_id = p.company_id THEN '一致'
        ELSE '不一致'
    END AS status_check,
    p.position_name,
    su.name AS student_name,
    su.student_user_id,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
ORDER BY sis.id
LIMIT 10;

-- 统计不一致的记录数量
SELECT '========== Part 3: 验证修复结果 ==========' as status;

SELECT 
    COUNT(*) AS inconsistent_count
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.company_id != p.company_id;

SELECT '========== Part 4: 修复完成 ==========' as status;
SELECT '已将所有实习状态记录的 company_id 更新为与 position.company_id 一致' AS message;
