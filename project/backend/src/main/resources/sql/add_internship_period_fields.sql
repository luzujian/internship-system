-- 为岗位表添加实习时长字段
-- 执行日期: 2026-03-12

USE internship;

-- 添加实习开始日期字段
ALTER TABLE `position` 
ADD COLUMN `internship_start_date` DATE NULL COMMENT '实习开始日期' AFTER `requirements`;

-- 添加实习结束日期字段
ALTER TABLE `position` 
ADD COLUMN `internship_end_date` DATE NULL COMMENT '实习结束日期' AFTER `internship_start_date`;

-- 验证字段是否添加成功
DESCRIBE `position`;
