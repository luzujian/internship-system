-- 检查学生联系方式数据
-- 数据库: internship
-- 目标: 检查student_users表中的phone和email字段是否已正确填充

-- ========================================
-- 第一部分：检查student_users表的数据
-- ========================================

-- 查看前10条学生记录的联系方式
SELECT 
    '检查前10条学生记录' as check_info,
    id,
    name,
    student_user_id,
    phone,
    email
FROM student_users
WHERE id BETWEEN 1 AND 10
ORDER BY id;

-- 查看ID 80-90的学生记录（2022级）
SELECT 
    '检查ID 80-90的学生记录' as check_info,
    id,
    name,
    student_user_id,
    phone,
    email
FROM student_users
WHERE id BETWEEN 80 AND 90
ORDER BY id;

-- ========================================
-- 第二部分：统计联系方式填充情况
-- ========================================

-- 统计有联系方式的学生数量
SELECT 
    '统计联系方式填充情况' as check_info,
    COUNT(*) as total_students,
    COUNT(phone) as students_with_phone,
    COUNT(email) as students_with_email,
    CASE 
        WHEN COUNT(phone) = COUNT(*) THEN '所有学生都有电话'
        ELSE CONCAT('有 ', COUNT(*) - COUNT(phone), ' 个学生没有电话')
    END as phone_status,
    CASE 
        WHEN COUNT(email) = COUNT(*) THEN '所有学生都有邮箱'
        ELSE CONCAT('有 ', COUNT(*) - COUNT(email), ' 个学生没有邮箱')
    END as email_status
FROM student_users;

-- ========================================
-- 第三部分：检查实习申请数据
-- ========================================

-- 查看实习申请数据，确认关联查询是否正常
SELECT 
    '检查实习申请数据（前5条）' as check_info,
    ia.id,
    su.name as student_name,
    su.student_user_id,
    su.phone,
    su.email,
    m.name as major,
    p.position_name,
    ia.apply_time
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN major m ON su.major_id = m.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id = 3
ORDER BY ia.apply_time DESC
LIMIT 5;

-- ========================================
-- 第四部分：检查中文乱码问题
-- ========================================

-- 查看student_users表中是否有中文乱码
SELECT 
    '检查学生姓名编码' as check_info,
    id,
    name,
    student_user_id,
    HEX(name) as name_hex,
    LENGTH(name) as name_length,
    CHAR_LENGTH(name) as name_char_length
FROM student_users
WHERE id BETWEEN 80 AND 90
ORDER BY id;

-- 检查数据库连接字符集
SHOW VARIABLES LIKE 'character_set%';
SHOW VARIABLES LIKE 'collation%';
