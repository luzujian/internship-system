-- 为operate_log表添加operator_role字段
ALTER TABLE operate_log ADD COLUMN operator_role VARCHAR(20) DEFAULT NULL COMMENT '操作人角色（STUDENT/TEACHER/ADMIN/COMPANY）' AFTER operator_username;

-- 为operator_role字段添加索引以提高查询性能
CREATE INDEX idx_operator_role ON operate_log(operator_role);