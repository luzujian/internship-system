-- 日志自动清理阈值配置表
-- 用于记录不同角色的日志自动清理阈值和保留数量

CREATE TABLE IF NOT EXISTS log_clean_config (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    operator_role VARCHAR(20) NOT NULL COMMENT '操作人角色（STUDENT/TEACHER/ADMIN/COMPANY）',
    clean_threshold INT NOT NULL DEFAULT 1000 COMMENT '清理阈值（日志数量达到此值时触发清理）',
    keep_count INT NOT NULL DEFAULT 200 COMMENT '保留数量（清理后保留的最新日志数量）',
    is_enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用（1-启用，0-禁用）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_operator_role (operator_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日志清理配置表';

-- 插入默认配置
INSERT INTO log_clean_config (operator_role, clean_threshold, keep_count) VALUES
('STUDENT', 500, 100),
('TEACHER', 300, 80),
('ADMIN', 200, 50),
('COMPANY', 300, 80)
ON DUPLICATE KEY UPDATE
clean_threshold = VALUES(clean_threshold),
keep_count = VALUES(keep_count);