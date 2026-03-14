-- 检查 internship_application 表中 company_id 与 position.company_id 不一致的记录
SELECT '========== Part 1: 检查 company_id 不一致的记录 ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.company_id AS application_company_id,
    ia.position_id,
    p.company_id AS position_company_id,
    p.position_name,
    su.name AS student_name,
    su.student_user_id,
    ia.status AS application_status
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
WHERE ia.company_id != p.company_id;

-- 统计不一致的记录数量
SELECT '========== Part 2: 统计不一致的记录数量 ==========' as status;

SELECT 
    COUNT(*) AS inconsistent_count
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id != p.company_id;

-- 检查所有申请记录的 company_id 和 position.company_id
SELECT '========== Part 3: 检查所有申请记录的 company_id 情况 ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.company_id AS application_company_id,
    ia.position_id,
    p.company_id AS position_company_id,
    CASE 
        WHEN ia.company_id = p.company_id THEN '一致'
        ELSE '不一致'
    END AS status_check,
    p.position_name,
    su.name AS student_name
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
ORDER BY ia.id
LIMIT 20;
