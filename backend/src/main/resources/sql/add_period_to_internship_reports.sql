-- 添加period字段到internship_reports表
-- 用于标识实习心得所属的周期

ALTER TABLE internship_reports 
ADD COLUMN period INT DEFAULT 1 COMMENT '实习周期：1-第一周期，2-第二周期，3-第三周期，4-第四周期，5-第五周期' 
AFTER status;

-- 更新现有数据，设置默认周期
UPDATE internship_reports SET period = 1 WHERE period IS NULL;

-- 验证字段添加
DESCRIBE internship_reports;

-- 查看数据
SELECT id, student_id, submit_time, status, period FROM internship_reports LIMIT 10;
