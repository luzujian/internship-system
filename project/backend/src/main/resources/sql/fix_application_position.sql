-- 修复 internship_application 表中 position_id 为 4 的记录
-- 将其更新为有效的岗位 id（13: 前端开发工程师）

SELECT '========== Part 1: 修复前的数据 ==========' as status;

SELECT 
    ia.id,
    su.name AS student_name,
    su.student_user_id,
    ia.position_id AS old_position_id,
    p.position_name AS old_position_name,
    ia.status
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.position_id = 4;

-- 更新 internship_application 表中的 position_id
UPDATE internship_application
SET position_id = 13
WHERE position_id = 4;

SELECT '========== Part 2: 修复后的数据 ==========' as status;

SELECT 
    ia.id,
    su.name AS student_name,
    su.student_user_id,
    ia.position_id AS new_position_id,
    p.position_name AS new_position_name,
    ia.status
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.id IN (3, 10, 17);

-- 更新 student_internship_status 表中的 position_id
UPDATE student_internship_status
SET position_id = 13
WHERE position_id = 4;

SELECT '========== Part 3: 验证 student_internship_status 表的修复结果 ==========' as status;

SELECT 
    sis.id,
    su.name AS student_name,
    su.student_user_id,
    sis.position_id AS new_position_id,
    p.position_name AS new_position_name,
    sis.status,
    sis.company_confirm_status
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.id IN (3, 10, 17);

SELECT '========== Part 4: 修复完成 ==========' as status;
SELECT '已将 position_id=4 的记录更新为 position_id=13（前端开发工程师）' AS message;
