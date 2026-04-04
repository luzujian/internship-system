-- 创建备份操作审计日志表
CREATE TABLE IF NOT EXISTS backup_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：BACKUP-备份，RESTORE-恢复，DOWNLOAD-下载，DELETE-删除，AUTO_BACKUP-自动备份，CLEAN_EXPIRED-清理过期',
    operation_detail VARCHAR(500) COMMENT '操作详情',
    operator VARCHAR(100) COMMENT '操作人',
    operator_ip VARCHAR(50) COMMENT '操作人IP',
    status INT DEFAULT 0 COMMENT '状态：0-进行中，1-成功，2-失败',
    error_message TEXT COMMENT '错误信息',
    operation_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    duration BIGINT DEFAULT 0 COMMENT '耗时（毫秒）',
    INDEX idx_operation_time (operation_time),
    INDEX idx_operation_type (operation_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份操作审计日志表';