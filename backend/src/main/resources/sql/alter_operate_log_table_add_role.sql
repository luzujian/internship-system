-- 添加operator_role字段到operate_log表
ALTER TABLE operate_log ADD COLUMN operator_role VARCHAR(20) COMMENT '操作人角色' AFTER operator_username;

-- 添加索引以提高查询性能
CREATE INDEX idx_operator_role ON operate_log(operator_role);