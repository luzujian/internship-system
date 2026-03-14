-- 检查现有数据并准备添加测试数据
-- 目标：为实习确认页面和岗位申请查看页面增加测试数据
-- 创建时间：2026-02-13

-- ========================================
-- 第一步：检查现有数据
-- ========================================

-- 检查1：查看student_users表中可用的学生ID
SELECT 
    '检查1: 可用的学生ID（前20个）' as check_info,
    id,
    name,
    student_user_id,
    gender,
    grade
FROM student_users
ORDER BY id
LIMIT 20;

-- 检查2：查看position表中company_id=3的岗位
SELECT 
    '检查2: company_id=3的岗位' as check_info,
    id,
    position_name,
    planned_recruit,
    recruited_count,
    remaining_quota,
    status
FROM position 
WHERE company_id = 3
ORDER BY id;

-- 检查3：查看当前实习确认页面的数据统计
SELECT 
    '检查3: 实习确认页面数据统计（company_id=3）' as check_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN company_confirm_status = 0 THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN company_confirm_status = 1 THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN company_confirm_status = 2 THEN 1 ELSE 0 END) as rejected_count
FROM student_internship_status
WHERE company_id = 3;

-- 检查4：查看当前岗位申请查看页面的数据统计
SELECT 
    '检查4: 岗位申请查看页面数据统计（company_id=3）' as check_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN viewed = 0 THEN 1 ELSE 0 END) as unviewed_count,
    SUM(CASE WHEN viewed = 1 THEN 1 ELSE 0 END) as viewed_count,
    SUM(CASE WHEN apply_status = 'pending' THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN apply_status = 'confirmed' THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN apply_status = 'rejected' THEN 1 ELSE 0 END) as rejected_count
FROM internship_application
WHERE company_id = 3;

-- 检查5：查看student_internship_status表中已使用的student_id
SELECT 
    '检查5: 已在实习确认表中的student_id（company_id=3）' as check_info,
    DISTINCT student_id,
    COUNT(*) as count
FROM student_internship_status
WHERE company_id = 3
GROUP BY student_id
ORDER BY student_id;

-- 检查6：查看internship_application表中已使用的student_id
SELECT 
    '检查6: 已在岗位申请表中的student_id（company_id=3）' as check_info,
    DISTINCT student_id,
    COUNT(*) as count
FROM internship_application
WHERE company_id = 3
GROUP BY student_id
ORDER BY student_id;
