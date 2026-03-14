-- Encoding troubleshooting script
-- Database: internship
-- Purpose: Check character set settings and data encoding

-- ========================================
-- Part 1: Check database character set
-- ========================================

SELECT '========== Part 1: Check character set settings ==========' as status;

-- Show all character set variables
SHOW VARIABLES LIKE 'character_set%';

-- Show collation settings
SHOW VARIABLES LIKE 'collation%';

-- ========================================
-- Part 2: Check table character sets
-- ========================================

SELECT '========== Part 2: Check table character sets ==========' as status;

-- Check student_users table
SHOW CREATE TABLE student_users;

-- Check internship_application table
SHOW CREATE TABLE internship_application;

-- ========================================
-- Part 3: Check data encoding
-- ========================================

SELECT '========== Part 3: Check data encoding ==========' as status;

-- Check student names with hex encoding
SELECT 
    'Check student name encoding' as check_info,
    id,
    name,
    student_user_id,
    HEX(name) as name_hex,
    LENGTH(name) as name_byte_length,
    CHAR_LENGTH(name) as name_char_length
FROM student_users
WHERE id BETWEEN 80 AND 90
ORDER BY id;

-- Check first 5 internship applications
SELECT 
    'Check application data' as check_info,
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
-- Part 4: Summary
-- ========================================

SELECT '========== Troubleshooting completed ==========' as status;
SELECT 
    'Please share the results with me' as next_step,
    'I will analyze and provide a fix' as info;
