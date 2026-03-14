-- 修复 internship_application 表中的 company_id
-- 将其更新为 position.company_id 的值

SELECT '========== Part 1: 修复前的数据 ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.company_id AS old_company_id,
    ia.position_id,
    p.company_id AS correct_company_id,
    p.position_name,
    su.name AS student_name,
    su.student_user_id,
    ia.status AS application_status
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
ORDER BY ia.id
LIMIT 10;

-- 更新 internship_application 表中的 company_id
UPDATE internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
SET ia.company_id = p.company_id
WHERE ia.company_id != p.company_id;

SELECT '========== Part 2: 修复后的数据 ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.company_id AS new_company_id,
    ia.position_id,
    p.company_id AS position_company_id,
    CASE 
        WHEN ia.company_id = p.company_id THEN '一致'
        ELSE '不一致'
    END AS status_check,
    p.position_name,
    su.name AS student_name,
    su.student_user_id
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
ORDER BY ia.id
LIMIT 10;

-- 统计不一致的记录数量
SELECT '========== Part 3: 验证修复结果 ==========' as status;

SELECT 
    COUNT(*) AS inconsistent_count
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id != p.company_id;

SELECT '========== Part 4: 修复完成 ==========' as status;
SELECT '已将所有申请记录的 company_id 更新为与 position.company_id 一致' AS message;
