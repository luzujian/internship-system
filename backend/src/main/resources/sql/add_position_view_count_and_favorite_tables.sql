-- ========================================
-- 添加职位收藏表
-- ========================================

-- 职位收藏表
CREATE TABLE IF NOT EXISTS position_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    position_id BIGINT NOT NULL COMMENT '职位ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_position_student (position_id, student_id),
    INDEX idx_student_id (student_id),
    INDEX idx_position_id (position_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职位收藏表';

-- 说明：
-- position_favorite 表记录学生的收藏记录
-- 获取收藏状态时查询 position_favorite 表
