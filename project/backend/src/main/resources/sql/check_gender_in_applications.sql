-- 检查 internship_application 表中 gender 字段的数据
SELECT 
    ia.id,
    ia.student_id,
    su.name AS student_name,
    su.gender,
    ia.position_id,
    p.position_name
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id = 3
ORDER BY ia.id
LIMIT 10;
