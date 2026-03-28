-- 为system_settings表添加实习相关字段
-- 用于支持智慧评分页面的时间阶段选择功能

ALTER TABLE system_settings 
ADD COLUMN internship_start_date DATE COMMENT '实习开始日期' AFTER dual_selection_end_date;

ALTER TABLE system_settings 
ADD COLUMN internship_end_date DATE COMMENT '实习结束日期' AFTER internship_start_date;

ALTER TABLE system_settings 
ADD COLUMN report_submission_cycle INT DEFAULT 7 COMMENT '实习心得提交周期（天）' AFTER internship_end_date;

-- 更新默认数据
UPDATE system_settings 
SET internship_start_date = DATE_FORMAT(NOW(), '%Y-%m-01'),
    internship_end_date = DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 6 MONTH), '%Y-%m-%d'),
    report_submission_cycle = 7
WHERE internship_start_date IS NULL;

-- 验证字段添加
DESCRIBE system_settings;

-- 查看数据
SELECT id, internship_start_date, internship_end_date, report_submission_cycle FROM system_settings;
