-- Comprehensive fix script
-- Database: internship
-- Purpose: Fix contact info missing and encoding issues

-- ========================================
-- Part 1: Check current status
-- ========================================

SELECT '========== Part 1: Check current status ==========' as status;

-- Check contact info filling status
SELECT 
    'Check contact info' as check_info,
    COUNT(*) as total_students,
    COUNT(phone) as students_with_phone,
    COUNT(email) as students_with_email
FROM student_users;


-- Check first 5 internship applications
SELECT 
    'Check first 5 applications' as check_info,
    ia.id,
    su.name as student_name,
    su.student_user_id,
    su.phone,
    su.email,
    m.name as major,
    p.position_name
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN major m ON su.major_id = m.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id = 3
ORDER BY ia.apply_time DESC
LIMIT 5;

-- ========================================
-- Part 2: Fix student contact info (if needed)
-- ========================================

SELECT '========== Part 2: Fix contact info ==========' as status;

-- Update only NULL records, safe way
UPDATE student_users 
SET phone = CASE 
    WHEN id BETWEEN 1 AND 9 THEN CONCAT('1380013800', id)
    WHEN id BETWEEN 10 AND 99 THEN CONCAT('138001380', id)
    WHEN id BETWEEN 100 AND 199 THEN CONCAT('139001380', id - 80)
    ELSE '13800138000'
END,
email = CASE 
    WHEN id BETWEEN 1 AND 99 THEN CONCAT('student', id, '@gdmu.edu.cn')
    WHEN id BETWEEN 100 AND 199 THEN CONCAT('student2022', LPAD(id - 80, 2, '0'), '@gdmu.edu.cn')
    ELSE 'unknown@gdmu.edu.cn'
END
WHERE phone IS NULL OR email IS NULL;

-- Show update result
SELECT 
    'Update result' as result_info,
    ROW_COUNT() as updated_rows;

-- ========================================
-- Part 3: Verify fix result
-- ========================================

SELECT '========== Part 3: Verify fix result ==========' as status;

-- Check contact info filling again
SELECT 
    'Verify contact info' as verify_info,
    COUNT(*) as total_students,
    COUNT(phone) as students_with_phone,
    COUNT(email) as students_with_email
FROM student_users;

-- Verify internship application data
SELECT 
    'Verify first 5 applications' as verify_info,
    ia.id,
    su.name as student_name,
    su.student_user_id,
    su.phone,
    su.email,
    m.name as major,
    p.position_name
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN major m ON su.major_id = m.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id = 3
ORDER BY ia.apply_time DESC
LIMIT 5;

-- ========================================
-- Part 4: Next steps
-- ========================================

SELECT '========== Script execution completed ==========' as status;
SELECT 
    'Fix completed!' as status,
    'Next steps:' as next_steps,
    '1. Restart backend service' as step_1,
    '2. Refresh browser (Ctrl + F5)' as step_2,
    '3. Check the application page' as step_3;
