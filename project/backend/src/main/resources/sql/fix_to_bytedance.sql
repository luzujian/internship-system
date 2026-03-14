-- 修复申请记录，将腾讯的岗位更新为字节跳动的岗位
-- 腾讯 position_id=13 (前端开发工程师) -> 字节跳动 position_id=18 (前端开发工程师)
-- 腾讯 position_id=14 (产品经理实习生) -> 字节跳动 position_id=60 (产品经理)

SELECT '========== Part 1: 修复前的数据 ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.company_id AS old_company_id,
    ia.position_id AS old_position_id,
    p.position_name AS old_position_name,
    p.company_id AS old_position_company_id,
    su.name AS student_name,
    su.student_user_id,
    ia.status
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
ORDER BY ia.id
LIMIT 10;

-- 更新 internship_application 表
-- 将 position_id=13 更新为 18，company_id 更新为 3
UPDATE internship_application
SET position_id = 18, company_id = 3
WHERE position_id = 13;

-- 将 position_id=14 更新为 60，company_id 更新为 3
UPDATE internship_application
SET position_id = 60, company_id = 3
WHERE position_id = 14;

SELECT '========== Part 2: 修复后的数据 ==========' as status;

SELECT 
    ia.id AS application_id,
    ia.company_id AS new_company_id,
    ia.position_id AS new_position_id,
    p.position_name AS new_position_name,
    p.company_id AS new_position_company_id,
    CASE 
        WHEN ia.company_id = p.company_id THEN '一致'
        ELSE '不一致'
    END AS status_check,
    su.name AS student_name,
    su.student_user_id,
    ia.status
FROM internship_application ia
LEFT JOIN position p ON ia.position_id = p.id
LEFT JOIN student_users su ON ia.student_id = su.id
ORDER BY ia.id
LIMIT 10;

-- 更新 student_internship_status 表
UPDATE student_internship_status
SET position_id = 18, company_id = 3
WHERE position_id = 13;

UPDATE student_internship_status
SET position_id = 60, company_id = 3
WHERE position_id = 14;

SELECT '========== Part 3: 验证 student_internship_status 表的修复结果 ==========' as status;

SELECT 
    sis.id AS status_id,
    sis.company_id AS new_company_id,
    sis.position_id AS new_position_id,
    p.position_name AS new_position_name,
    p.company_id AS new_position_company_id,
    CASE 
        WHEN sis.company_id = p.company_id THEN '一致'
        ELSE '不一致'
    END AS status_check,
    su.name AS student_name,
    su.student_user_id,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
ORDER BY sis.id
LIMIT 10;

SELECT '========== Part 4: 修复完成 ==========' as status;
SELECT '已将所有申请记录更新为字节跳动的岗位' AS message;
