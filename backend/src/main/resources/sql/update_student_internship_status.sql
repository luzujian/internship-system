-- 更新student_internship_status表，添加异常状态字段
-- 执行时间：2026-01-15

USE internship_management;

-- 添加异常状态字段
ALTER TABLE student_internship_status 
ADD COLUMN has_complaint TINYINT(1) DEFAULT 0 COMMENT '是否有投诉（0否，1是）' AFTER position_id;

ALTER TABLE student_internship_status 
ADD COLUMN is_delayed TINYINT(1) DEFAULT 0 COMMENT '是否延期（0否，1是）' AFTER has_complaint;

ALTER TABLE student_internship_status 
ADD COLUMN is_interrupted TINYINT(1) DEFAULT 0 COMMENT '是否中断（0否，1是）' AFTER is_delayed;

-- 添加索引以提高查询性能
CREATE INDEX idx_has_complaint ON student_internship_status(has_complaint);
CREATE INDEX idx_is_delayed ON student_internship_status(is_delayed);
CREATE INDEX idx_is_interrupted ON student_internship_status(is_interrupted);

-- 更新status字段的注释
ALTER TABLE student_internship_status 
MODIFY COLUMN status INT DEFAULT 0 COMMENT '实习状态（0未找到，1有offer未确定，2已确定实习，3实习结束）';

COMMIT;
