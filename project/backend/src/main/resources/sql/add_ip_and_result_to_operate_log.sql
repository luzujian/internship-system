-- 为operate_log表添加IP地址和操作结果字段
ALTER TABLE operate_log ADD COLUMN ip_address VARCHAR(50) DEFAULT NULL COMMENT '操作IP地址' AFTER operator_role;
ALTER TABLE operate_log ADD COLUMN operation_result VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '操作结果（SUCCESS/FAILURE）' AFTER description;

-- 为ip_address和operation_result字段添加索引以提高查询性能
CREATE INDEX idx_ip_address ON operate_log(ip_address);
CREATE INDEX idx_operation_result ON operate_log(operation_result);