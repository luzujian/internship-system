-- 修改company_users表的max_backup_students字段类型为BIGINT以支持更大的数值范围
-- 原字段类型为INT，最大值为2147483647，改为BIGINT后可支持更大的数值

ALTER TABLE company_users MODIFY COLUMN max_backup_students BIGINT DEFAULT 0 COMMENT '能接受兜底的最多学生数量（最多1000名）';
