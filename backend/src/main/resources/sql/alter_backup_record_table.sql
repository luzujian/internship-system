-- 更新备份记录表，添加新字段
ALTER TABLE backup_record 
ADD COLUMN backup_format VARCHAR(20) DEFAULT 'SQL' COMMENT '备份格式：SQL-SQL格式，CSV-CSV格式',
ADD COLUMN is_compressed TINYINT(1) DEFAULT 0 COMMENT '是否压缩：0-未压缩，1-已压缩',
ADD COLUMN checksum VARCHAR(64) COMMENT '文件校验和（SHA-256）',
ADD COLUMN parent_backup_id BIGINT COMMENT '父备份ID（用于增量备份）';

-- 添加索引
CREATE INDEX idx_parent_backup_id ON backup_record(parent_backup_id);