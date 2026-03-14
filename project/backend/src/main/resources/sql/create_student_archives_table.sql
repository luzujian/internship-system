-- 检查并创建 student_archives 表

-- 首先查看数据库中有哪些表
SHOW TABLES;

-- 检查 student_archives 表是否存在
SHOW TABLES LIKE 'student_archives';

-- 如果表不存在，创建它
CREATE TABLE IF NOT EXISTS student_archives (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_type VARCHAR(50) NOT NULL COMMENT '文件类型（简历、证书等）',
    file_url VARCHAR(500) NOT NULL COMMENT '文件URL',
    upload_time DATETIME NOT NULL COMMENT '上传时间',
    remark TEXT COMMENT '备注',
    status INT DEFAULT 0 COMMENT '状态（0待审核，1已通过，2已拒绝）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_student_id (student_id),
    INDEX idx_file_type (file_type),
    INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生材料归档表';

-- 验证表是否创建成功
DESCRIBE student_archives;

-- 显示创建成功信息
SELECT 'student_archives 表创建成功！' AS message;
