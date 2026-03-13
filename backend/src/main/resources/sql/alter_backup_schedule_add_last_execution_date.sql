-- 添加最后执行日期字段到自动备份配置表
ALTER TABLE backup_schedule 
ADD COLUMN last_execution_date DATE DEFAULT NULL COMMENT '最后一次执行自动备份的日期';
