-- 仅在管理员「辣子鸡」确认后执行；业务账号 user 不得执行 DDL。
-- 邮件发送记录独立保留，反馈表原有回复字段继续作为最近一次回复摘要。
CREATE TABLE problem_feedback_email_reply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback_id BIGINT NOT NULL,
    request_id CHAR(36) NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    reply_content TEXT NOT NULL,
    mail_body TEXT NOT NULL,
    admin_id BIGINT NOT NULL,
    admin_name VARCHAR(100) NOT NULL,
    feedback_status VARCHAR(20) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    delivery_detail VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_reply_request (request_id),
    KEY idx_feedback_reply_history (feedback_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问题反馈邮件回复发送记录';
