-- 更新实习确认记录的实习时间数据
-- 为现有的实习确认记录添加真实的实习开始时间、结束时间和时长
-- 执行时间：2026-02-22

USE internship;

-- 更新董芳（学号：20200032）的实习时间
UPDATE student_internship_status 
SET 
    internship_start_time = '2026-03-01',
    internship_end_time = '2026-06-30',
    internship_duration = 121
WHERE id = 1;

-- 更新其他已确认的实习记录
UPDATE student_internship_status 
SET 
    internship_start_time = '2026-03-15',
    internship_end_time = '2026-07-14',
    internship_duration = 121
WHERE id = 2 AND company_confirm_status = 1;

UPDATE student_internship_status 
SET 
    internship_start_time = '2026-04-01',
    internship_end_time = '2026-07-31',
    internship_duration = 121
WHERE id = 3 AND company_confirm_status = 1;

UPDATE student_internship_status 
SET 
    internship_start_time = '2026-03-10',
    internship_end_time = '2026-06-09',
    internship_duration = 91
WHERE id = 4 AND company_confirm_status = 1;

UPDATE student_internship_status 
SET 
    internship_start_time = '2026-03-20',
    internship_end_time = '2026-07-19',
    internship_duration = 121
WHERE id = 5 AND company_confirm_status = 1;

-- 为所有待确认的记录设置默认的实习时间（从确认后一个月开始，实习3个月）
UPDATE student_internship_status 
SET 
    internship_start_time = DATE_ADD(CURDATE(), INTERVAL 30 DAY),
    internship_end_time = DATE_ADD(CURDATE(), INTERVAL 120 DAY),
    internship_duration = 90
WHERE company_confirm_status = 0 
  AND internship_start_time IS NULL;

-- 为所有已确认但没有实习时间的记录设置默认值
UPDATE student_internship_status 
SET 
    internship_start_time = DATE_SUB(CURDATE(), INTERVAL 15 DAY),
    internship_end_time = DATE_ADD(CURDATE(), INTERVAL 105 DAY),
    internship_duration = 120
WHERE company_confirm_status = 1 
  AND internship_start_time IS NULL;

-- 验证更新结果
SELECT 
    id,
    student_id,
    company_id,
    position_id,
    company_confirm_status,
    internship_start_time,
    internship_end_time,
    internship_duration
FROM student_internship_status
ORDER BY id;

COMMIT;
