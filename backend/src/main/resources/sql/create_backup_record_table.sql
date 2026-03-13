-- 创建备份记录表
CREATE TABLE IF NOT EXISTS backup_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    backup_name VARCHAR(255) NOT NULL COMMENT '备份名称',
    backup_path VARCHAR(500) NOT NULL COMMENT '备份文件路径',
    backup_size BIGINT DEFAULT 0 COMMENT '备份文件大小（字节）',
    backup_type VARCHAR(50) DEFAULT 'FULL' COMMENT '备份类型：FULL-全量，INCREMENTAL-增量',
    backup_time DATETIME NOT NULL COMMENT '备份时间',
    status INT DEFAULT 1 COMMENT '状态：0-失败，1-成功，2-进行中',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份记录表';

-- 创建索引
CREATE INDEX idx_backup_time ON backup_record(backup_time);
CREATE INDEX idx_backup_type ON backup_record(backup_type);
CREATE INDEX idx_status ON backup_record(status);
