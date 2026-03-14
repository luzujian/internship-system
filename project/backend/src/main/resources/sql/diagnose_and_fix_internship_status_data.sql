-- 诊断和修复实习确认页面数据问题
-- 问题：实习确认页面出现了不属于company_id=3的"Java开发工程师"岗位数据
-- 创建时间：2026-02-13

-- ========================================
-- 第一步：诊断当前数据问题
-- ========================================

-- 诊断1：查看company_id=3的所有实习状态记录及其关联的岗位
SELECT 
    '诊断1: company_id=3的实习状态记录' as diagnostic_info,
    sis.id,
    sis.student_id,
    sis.company_id as status_company_id,
    sis.position_id,
    p.id as position_id,
    p.position_name,
    p.company_id as position_company_id,
    CASE 
        WHEN p.company_id != sis.company_id THEN '数据不一致：岗位所属公司与实习状态所属公司不匹配'
        ELSE '数据正常'
    END as data_status
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.company_id = 3;

-- 诊断2：查看所有包含"Java开发工程师"的实习状态记录
SELECT 
    '诊断2: 包含Java开发工程师的实习状态记录' as diagnostic_info,
    sis.id,
    sis.student_id,
    sis.company_id,
    p.position_name,
    p.company_id as position_company_id,
    su.name as student_name,
    su.student_user_id
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
WHERE p.position_name LIKE '%Java开发工程师%';

-- 诊断3：查看company_id=3的所有岗位
SELECT 
    '诊断3: company_id=3的所有岗位' as diagnostic_info,
    id,
    company_id,
    position_name,
    planned_recruit,
    recruited_count,
    remaining_quota,
    status
FROM position 
WHERE company_id = 3;

-- 诊断4：统计各company_id的实习状态记录数量
SELECT 
    '诊断4: 各company_id的实习状态记录统计' as diagnostic_info,
    company_id,
    COUNT(*) as count
FROM student_internship_status
GROUP BY company_id
ORDER BY company_id;

-- ========================================
-- 第二步：修复数据问题
-- ========================================

-- 修复方案1：将company_id不正确的实习状态记录的company_id修正为岗位所属的company_id
-- 注意：这会修改student_internship_status表中的company_id字段

-- 首先备份需要修改的记录
CREATE TEMPORARY TABLE temp_backup_incorrect_company_id AS
SELECT 
    sis.*,
    p.company_id as correct_company_id,
    '原company_id=' + CAST(sis.company_id AS CHAR) + ', 应为=' + CAST(p.company_id AS CHAR) as fix_reason
FROM student_internship_status sis
INNER JOIN position p ON sis.position_id = p.id
WHERE sis.company_id != p.company_id;

-- 查看需要修复的记录
SELECT 
    '需要修复的记录（备份表）' as fix_info,
    * 
FROM temp_backup_incorrect_company_id;

-- 修复方案2：删除company_id不正确的实习状态记录（如果不需要保留这些记录）
-- 注意：这会删除student_internship_status表中的记录

-- 查看将被删除的记录
SELECT 
    '将被删除的记录' as delete_info,
    sis.id,
    sis.student_id,
    sis.company_id,
    p.position_name,
    p.company_id as position_company_id,
    su.name as student_name
FROM student_internship_status sis
INNER JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
WHERE sis.company_id != p.company_id;

-- ========================================
-- 第三步：执行修复（请根据实际情况选择修复方案）
-- ========================================

-- 修复方案A：修正company_id（推荐，保留数据但修正关联关系）
-- 执行前请先确认temp_backup_incorrect_company_id中的数据是否正确
-- UPDATE student_internship_status sis
-- INNER JOIN position p ON sis.position_id = p.id
-- SET sis.company_id = p.company_id
-- WHERE sis.company_id != p.company_id;

-- 修复方案B：删除不一致的记录（如果确定这些记录是错误的）
-- 执行前请先确认上面的SELECT查询结果
-- DELETE sis
-- FROM student_internship_status sis
-- INNER JOIN position p ON sis.position_id = p.id
-- WHERE sis.company_id != p.company_id;

-- ========================================
-- 第四步：验证修复结果
-- ========================================

-- 验证1：检查是否还存在company_id不一致的记录
SELECT 
    '验证1: 检查company_id是否一致' as verification_info,
    COUNT(*) as inconsistent_count
FROM student_internship_status sis
INNER JOIN position p ON sis.position_id = p.id
WHERE sis.company_id != p.company_id;

-- 验证2：查看修复后的company_id=3的实习状态记录
SELECT 
    '验证2: 修复后company_id=3的实习状态记录' as verification_info,
    sis.id,
    sis.student_id,
    sis.company_id,
    p.position_name,
    p.company_id as position_company_id,
    su.name as student_name
FROM student_internship_status sis
LEFT JOIN position p ON sis.position_id = p.id
LEFT JOIN student_users su ON sis.student_id = su.id
WHERE sis.company_id = 3;

-- 验证3：统计修复后的数据
SELECT 
    '验证3: 修复后各company_id的实习状态记录统计' as verification_info,
    company_id,
    COUNT(*) as count
FROM student_internship_status
GROUP BY company_id
ORDER BY company_id;

-- ========================================
-- 清理临时表（如果不再需要）
-- ========================================

-- DROP TEMPORARY TABLE IF EXISTS temp_backup_incorrect_company_id;
