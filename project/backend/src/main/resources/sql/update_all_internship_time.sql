-- 为所有实习确认记录设置实习时间（包括已拒绝的）
-- 执行时间：2026-02-22

USE internship;

-- 为所有没有实习时间的记录设置默认值
-- 根据不同的确认状态设置不同的实习时间

-- 已确认的记录（company_confirm_status = 1）：从15天前开始，实习4个月
UPDATE student_internship_status 
SET 
    internship_start_time = DATE_SUB(CURDATE(), INTERVAL 15 DAY),
    internship_end_time = DATE_ADD(CURDATE(), INTERVAL 105 DAY),
    internship_duration = 120
WHERE company_confirm_status = 1 
  AND internship_start_time IS NULL;

-- 待确认的记录（company_confirm_status = 0）：从一个月后开始，实习3个月
UPDATE student_internship_status 
SET 
    internship_start_time = DATE_ADD(CURDATE(), INTERVAL 30 DAY),
    internship_end_time = DATE_ADD(CURDATE(), INTERVAL 120 DAY),
    internship_duration = 90
WHERE company_confirm_status = 0 
  AND internship_start_time IS NULL;

-- 已拒绝的记录（company_confirm_status = 2）：设置一个假设的实习时间（如果学生被接受的话）
UPDATE student_internship_status 
SET 
    internship_start_time = DATE_SUB(CURDATE(), INTERVAL 30 DAY),
    internship_end_time = DATE_ADD(CURDATE(), INTERVAL 90 DAY),
    internship_duration = 120
WHERE company_confirm_status = 2 
  AND internship_start_time IS NULL;

-- 验证更新结果
SELECT 
    id,
    student_id,
    company_id,
    position_id,
    company_confirm_status,
    CASE 
        WHEN company_confirm_status = 0 THEN '待确认'
        WHEN company_confirm_status = 1 THEN '已确认'
        WHEN company_confirm_status = 2 THEN '已拒绝'
        ELSE '未知'
    END AS status_text,
    internship_start_time,
    internship_end_time,
    internship_duration
FROM student_internship_status
ORDER BY id;

-- 统计各状态的记录数
SELECT 
    company_confirm_status,
    CASE 
        WHEN company_confirm_status = 0 THEN '待确认'
        WHEN company_confirm_status = 1 THEN '已确认'
        WHEN company_confirm_status = 2 THEN '已拒绝'
        ELSE '未知'
    END AS status_text,
    COUNT(*) AS count,
    SUM(CASE WHEN internship_start_time IS NOT NULL THEN 1 ELSE 0 END) AS has_time_count
FROM student_internship_status
GROUP BY company_confirm_status;

COMMIT;
