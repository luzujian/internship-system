-- 检查 student_internship_status 表中 company_id 与 position.company_id 不一致的记录
SELECT '========== Part 1: 检查 student_internship_status 表 ==========' as status;

SELECT 
    sis.id AS status_id,
    sis.company_id AS status_company_id,
    sis.position_id,
    p.company_id AS position_company_id,
    p.position_name,
    su.name AS student_name,
    su.student_user_id,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
ORDER BY sis.id;

-- 统计不一致的记录数量
SELECT '========== Part 2: 统计不一致的记录数量 ==========' as status;

SELECT 
    COUNT(*) AS inconsistent_count
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.company_id != p.company_id;
