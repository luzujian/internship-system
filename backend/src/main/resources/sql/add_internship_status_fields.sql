-- 为 student_internship_status 表添加实习去向登记扩展字段
-- 执行时间：2026-03-19

ALTER TABLE `student_internship_status`
ADD COLUMN `student_name` varchar(100) DEFAULT NULL COMMENT '学生姓名' AFTER `student_id`,
ADD COLUMN `gender` varchar(10) DEFAULT NULL COMMENT '性别' AFTER `student_name`,
ADD COLUMN `school` varchar(200) DEFAULT NULL COMMENT '学校' AFTER `gender`,
ADD COLUMN `grade` varchar(50) DEFAULT NULL COMMENT '年级' AFTER `school`,
ADD COLUMN `education` varchar(50) DEFAULT NULL COMMENT '学历' AFTER `grade`,
ADD COLUMN `college` varchar(200) DEFAULT NULL COMMENT '学院' AFTER `education`,
ADD COLUMN `major_name` varchar(100) DEFAULT NULL COMMENT '专业名称' AFTER `college`,
ADD COLUMN `class_name` varchar(100) DEFAULT NULL COMMENT '班级' AFTER `major_name`,
ADD COLUMN `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话' AFTER `class_name`,
ADD COLUMN `email` varchar(100) DEFAULT NULL COMMENT '电子邮箱' AFTER `contact_phone`,
ADD COLUMN `company_name` varchar(200) DEFAULT NULL COMMENT '企业名称' AFTER `company_id`,
ADD COLUMN `position_name` varchar(100) DEFAULT NULL COMMENT '岗位名称' AFTER `company_name`,
ADD COLUMN `company_address` varchar(500) DEFAULT NULL COMMENT '企业地址' AFTER `position_name`,
ADD COLUMN `company_phone` varchar(20) DEFAULT NULL COMMENT '企业联系电话' AFTER `company_address`,
ADD COLUMN `graduate_school` varchar(200) DEFAULT NULL COMMENT '考研院校' AFTER `company_phone`,
ADD COLUMN `graduate_major` varchar(100) DEFAULT NULL COMMENT '考研专业' AFTER `graduate_school`,
ADD COLUMN `other_reason` text COMMENT '其他原因说明' AFTER `graduate_major`;

-- 添加索引以提高查询性能
CREATE INDEX `idx_student_name` ON `student_internship_status` (`student_name`);
CREATE INDEX `idx_grade` ON `student_internship_status` (`grade`);
CREATE INDEX `idx_education` ON `student_internship_status` (`education`);
