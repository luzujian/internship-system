ALTER TABLE backup_record ADD COLUMN table_list TEXT COMMENT '备份文件中包含的表列表';
ALTER TABLE backup_record ADD COLUMN table_count INT DEFAULT 0 COMMENT '备份文件中包含的表数量';
