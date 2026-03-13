-- 创建问题反馈表
CREATE TABLE IF NOT EXISTS problem_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    user_type VARCHAR(20) NOT NULL COMMENT '用户类型：student-学生，teacher-教师，company-企业',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_name VARCHAR(100) NOT NULL COMMENT '用户姓名',
    user_account VARCHAR(100) NOT NULL COMMENT '用户账号',
    title VARCHAR(255) NOT NULL COMMENT '反馈标题',
    content TEXT NOT NULL COMMENT '反馈内容',
    feedback_type VARCHAR(50) DEFAULT 'other' COMMENT '反馈类型：system-系统问题，feature-功能建议，bug-程序错误，data-数据问题，other-其他',
    status VARCHAR(20) DEFAULT 'resolved' COMMENT '处理状态：resolved-已解决，closed-感谢您的反馈',
    priority VARCHAR(20) DEFAULT 'normal' COMMENT '优先级：low-低，normal-中，high-高',
    attachment_url VARCHAR(500) DEFAULT NULL COMMENT '附件URL',
    admin_reply TEXT DEFAULT NULL COMMENT '管理员回复',
    admin_id BIGINT DEFAULT NULL COMMENT '处理管理员ID',
    admin_name VARCHAR(100) DEFAULT NULL COMMENT '处理管理员姓名',
    reply_time DATETIME DEFAULT NULL COMMENT '回复时间',
    resolve_time DATETIME DEFAULT NULL COMMENT '解决时间',
    deleted INT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题反馈表';

-- 创建索引
CREATE INDEX idx_feedback_user_type ON problem_feedback(user_type);
CREATE INDEX idx_feedback_status ON problem_feedback(status);
CREATE INDEX idx_feedback_priority ON problem_feedback(priority);
CREATE INDEX idx_feedback_type ON problem_feedback(feedback_type);
CREATE INDEX idx_feedback_user_id ON problem_feedback(user_id);
CREATE INDEX idx_feedback_create_time ON problem_feedback(create_time);
