-- Check internship application position data
-- Database: internship
-- Purpose: Check missing position names in application list

-- ========================================
-- Part 1: Check application data
-- ========================================

SELECT '========== Part 1: Check application data ==========' as status;

-- Check all applications with company_id=3
SELECT 
    'Check application position data' as check_info,
    ia.id,
    ia.student_id,
    su.name as student_name,
    ia.position_id,
    p.position_name,
    p.id as position_table_id
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id = 3
ORDER BY ia.apply_time DESC;

-- ========================================
-- Part 2: Check applications with missing position
-- ========================================

SELECT '========== Part 2: Check applications with missing position ==========' as status;

-- Check applications where position_id is NULL
SELECT 
    'Applications with position_id = NULL' as check_info,
    ia.id,
    ia.student_id,
    su.name as student_name,
    ia.position_id
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
WHERE ia.company_id = 3
  AND ia.position_id IS NULL
ORDER BY ia.apply_time DESC;

-- Check applications where position_id exists but not in position table
SELECT 
    'Applications with invalid position_id' as check_info,
    ia.id,
    ia.student_id,
    su.name as student_name,
    ia.position_id
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
WHERE ia.company_id = 3
  AND ia.position_id IS NOT NULL
  AND ia.position_id NOT IN (SELECT id FROM position)
ORDER BY ia.apply_time DESC;

-- ========================================
-- Part 3: Show available positions
-- ========================================

SELECT '========== Part 3: Available positions ==========' as status;

SELECT 
    id,
    position_name,
    company_id,
    department
FROM position
WHERE company_id = 3
ORDER BY id;

-- ========================================
-- Part 4: Summary
-- ========================================

SELECT '========== Summary ==========' as status;
SELECT 
    'Please share the results with me' as next_step;
