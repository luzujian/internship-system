-- 修复实习确认页面的乱码和岗位缺失问题

-- 数据库: internship
-- 创建时间: 2026-02-14

-- ========================================
-- 第一部分：检查数据库和表的字符集
-- ========================================

-- 检查数据库字符集
SHOW CREATE DATABASE internship;

-- 检查关键表的字符集
SHOW CREATE TABLE student_users;
SHOW CREATE TABLE student_internship_status;
SHOW CREATE TABLE position;
SHOW CREATE TABLE internship_application;

-- ========================================
-- 第二部分：检查position_id为NULL的记录
-- ========================================

-- 检查student_internship_status表中position_id为NULL的记录
SELECT 
    '检查position_id为NULL的记录' as check_info,
    sis.id,
    sis.student_id,
    su.name as student_name,
    su.student_user_id,
    sis.company_id,
    sis.position_id,
    sis.company_confirm_status,
    sis.create_time
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
WHERE sis.position_id IS NULL
ORDER BY sis.id;

-- 检查student_internship_status表中position_id存在但position表中没有对应记录的情况
SELECT 
    '检查position_id不存在于position表' as check_info,
    sis.id,
    sis.student_id,
    su.name as student_name,
    su.student_user_id,
    sis.company_id,
    sis.position_id,
    sis.company_confirm_status,
    sis.create_time
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.position_id IS NOT NULL AND p.id IS NULL
ORDER BY sis.id;

-- ========================================
-- 第三部分：查看现有的position记录
-- ========================================

-- 查看company_id=3的所有position记录
SELECT 
    id,
    position_name,
    department,
    planned_recruit,
    recruited_count,
    remaining_quota,
    status
FROM position
WHERE company_id = 3
ORDER BY id;

-- ========================================
-- 第四部分：修复position_id为NULL的记录
-- ========================================

-- 为position_id为NULL的记录分配一个默认的position_id
-- 假设使用company_id=3的第一个position（通常是前端开发相关的岗位）
UPDATE student_internship_status 
SET position_id = (
    SELECT id FROM position 
    WHERE company_id = 3 
    ORDER BY id 
    LIMIT 1
)
WHERE company_id = 3 
  AND position_id IS NULL;

-- ========================================
-- 第五部分：修复position_id不存在于position表的记录
-- ========================================

-- 将无效的position_id更新为有效的position_id
UPDATE student_internship_status sis
SET sis.position_id = (
    SELECT p.id FROM position p 
    WHERE p.company_id = sis.company_id 
    ORDER BY p.id 
    LIMIT 1
)
WHERE sis.position_id IS NOT NULL 
  AND NOT EXISTS (
      SELECT 1 FROM position p 
      WHERE p.id = sis.position_id
  );

-- ========================================
-- 第六部分：验证修复结果
-- ========================================

-- 验证：检查是否还有position_id为NULL的记录
SELECT 
    '验证：检查position_id为NULL的记录' as verification_info,
    COUNT(*) as null_count
FROM student_internship_status
WHERE position_id IS NULL;

-- 验证：检查实习确认页面的数据（按查看状态分组，限制显示10条）
SELECT 
    '验证：实习确认页面数据详情' as verification_info,
    sis.id,
    su.name as student_name,
    su.student_user_id,
    p.position_name,
    sis.company_confirm_status,
    CASE 
        WHEN sis.company_confirm_status = 0 THEN '待确认'
        WHEN sis.company_confirm_status = 1 THEN '已确认'
        WHEN sis.company_confirm_status = 2 THEN '已拒绝'
        ELSE '未知'
    END as status_text,
    sis.internship_start_time,
    sis.internship_end_time,
    sis.internship_duration,
    sis.remark
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.company_id = 3
ORDER BY sis.company_confirm_status, sis.create_time
LIMIT 10;

-- ========================================
-- 第七部分：可选 - 修改数据库和表的字符集为UTF-8
-- ========================================

-- 如果需要修改数据库字符集，取消以下注释并执行
-- ALTER DATABASE internship CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 如果需要修改表字符集，取消以下注释并执行
-- ALTER TABLE student_users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ALTER TABLE student_internship_status CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ALTER TABLE position CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ALTER TABLE internship_application CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ========================================
-- 执行说明
-- ========================================
-- 1. 执行本脚本前，请先备份重要数据
-- 2. 建议先执行第一部分检查字符集，确认是否需要修改
-- 3. 执行第二部分检查问题数据
-- 4. 执行第四和第五部分修复数据
-- 5. 执行第六部分验证修复结果
-- 6. 如果需要，执行第七部分修改字符集
-- 7. 修复完成后，重启后端服务
