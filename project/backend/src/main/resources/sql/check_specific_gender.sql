-- Check specific student's gender data
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
WHERE ia.id = 14;
