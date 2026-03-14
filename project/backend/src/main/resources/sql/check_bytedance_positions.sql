-- 检查字节跳动（company_id=3）的岗位
SELECT '========== Part 1: 检查字节跳动的岗位 ==========' as status;

SELECT 
    id,
    position_name,
    company_id,
    salary_min,
    salary_max,
    status
FROM position
WHERE company_id = 3
ORDER BY id;

-- 检查当前申请记录的 position_id
SELECT '========== Part 2: 检查当前申请记录的 position_id ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.position_id,
    p.position_name,
    p.company_id AS position_company_id,
    ia.company_id AS application_company_id,
    su.name AS student_name
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
ORDER BY ia.id
LIMIT 10;
