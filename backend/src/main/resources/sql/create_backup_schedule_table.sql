-- 创建自动备份配置表
CREATE TABLE IF NOT EXISTS backup_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    enabled TINYINT(1) DEFAULT 0 COMMENT '是否启用自动备份：0-禁用，1-启用',
    frequency VARCHAR(20) DEFAULT 'daily' COMMENT '备份频率：daily-每天，weekly-每周，monthly-每月',
    backup_time VARCHAR(10) DEFAULT '02:00' COMMENT '备份时间（HH:mm格式）',
    retention_days INT DEFAULT 30 COMMENT '备份保留天数',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动备份配置表';

-- 插入默认配置
INSERT INTO backup_schedule (enabled, frequency, backup_time, retention_days, remark) 
VALUES (0, 'daily', '02:00', 30, '默认自动备份配置');