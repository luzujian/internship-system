-- 为 position 表添加岗位管理相关字段
-- 执行时间：2026-03-14

-- 添加部门字段
ALTER TABLE `position` ADD COLUMN `department` VARCHAR(100) DEFAULT NULL COMMENT '所属部门' AFTER `position_name`;

-- 添加岗位类型字段
ALTER TABLE `position` ADD COLUMN `position_type` VARCHAR(50) DEFAULT NULL COMMENT '岗位类型（全职/兼职/实习）' AFTER `department`;

-- 添加工作地点字段
ALTER TABLE `position` ADD COLUMN `province` VARCHAR(50) DEFAULT NULL COMMENT '省份' AFTER `position_type`;
ALTER TABLE `position` ADD COLUMN `city` VARCHAR(50) DEFAULT NULL COMMENT '城市' AFTER `province`;
ALTER TABLE `position` ADD COLUMN `district` VARCHAR(50) DEFAULT NULL COMMENT '区县' AFTER `city`;
ALTER TABLE `position` ADD COLUMN `detail_address` VARCHAR(255) DEFAULT NULL COMMENT '详细地址' AFTER `district`;

-- 添加薪资字段
ALTER TABLE `position` ADD COLUMN `salary_min` INT DEFAULT NULL COMMENT '最低薪资 (K)' AFTER `detail_address`;
ALTER TABLE `position` ADD COLUMN `salary_max` INT DEFAULT NULL COMMENT '最高薪资 (K)' AFTER `salary_min`;

-- 添加岗位描述字段
ALTER TABLE `position` ADD COLUMN `description` TEXT DEFAULT NULL COMMENT '岗位描述' AFTER `salary_max`;

-- 添加状态字段
ALTER TABLE `position` ADD COLUMN `status` VARCHAR(20) DEFAULT 'active' COMMENT '状态（active 招聘中/paused 已暂停）' AFTER `remaining_quota`;

-- 添加发布日期字段
ALTER TABLE `position` ADD COLUMN `publish_date` DATETIME DEFAULT NULL COMMENT '发布日期' AFTER `status`;

-- 添加实习时间段字段
ALTER TABLE `position` ADD COLUMN `internship_start_date` DATETIME DEFAULT NULL COMMENT '实习开始日期' AFTER `publish_date`;
ALTER TABLE `position` ADD COLUMN `internship_end_date` DATETIME DEFAULT NULL COMMENT '实习结束日期' AFTER `internship_start_date`;

-- 添加索引
CREATE INDEX `idx_position_status` ON `position`(`status`);
CREATE INDEX `idx_position_publish_date` ON `position`(`publish_date`);
