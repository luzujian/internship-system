CREATE TABLE IF NOT EXISTS `system_settings` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `creator` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    `updater` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
    `deleted` INT DEFAULT 0 COMMENT '删除标记(0-未删除,1-已删除)',
    
    `system_name` VARCHAR(100) DEFAULT '学生实习管理系统' COMMENT '系统名称',
    `system_description` VARCHAR(500) DEFAULT '专业的学生实习管理平台' COMMENT '系统描述',
    `page_size` INT DEFAULT 10 COMMENT '默认分页大小',
    `logo` VARCHAR(255) DEFAULT NULL COMMENT '系统Logo',
    `system_status` INT DEFAULT 1 COMMENT '系统状态(0-维护模式,1-正常运行)',
    
    `min_password_length` INT DEFAULT 6 COMMENT '密码最小长度',
    `password_complexity` VARCHAR(50) DEFAULT 'lowercase,number' COMMENT '密码复杂度要求(逗号分隔:lowercase,uppercase,number,special)',
    `password_expire_days` INT DEFAULT 90 COMMENT '密码过期天数(0表示永不过期)',
    `max_login_attempts` INT DEFAULT 5 COMMENT '最大登录失败次数',
    `lock_time` INT DEFAULT 30 COMMENT '账号锁定时间(分钟)',
    `session_timeout` INT DEFAULT 120 COMMENT '会话超时时间(分钟)',
    `enable_two_factor` INT DEFAULT 0 COMMENT '启用双因素认证(0-否,1-是)',
    
    `enable_email_notification` INT DEFAULT 0 COMMENT '启用邮件通知(0-否,1-是)',
    `smtp_host` VARCHAR(100) DEFAULT NULL COMMENT 'SMTP服务器地址',
    `smtp_port` INT DEFAULT 465 COMMENT 'SMTP端口',
    `email_from` VARCHAR(100) DEFAULT NULL COMMENT '发件人邮箱',
    `email_password` VARCHAR(255) DEFAULT NULL COMMENT '邮箱密码',
    `enable_system_notification` INT DEFAULT 1 COMMENT '启用系统通知(0-否,1-是)',
    `notification_types` VARCHAR(100) DEFAULT 'login,system,internship' COMMENT '通知类型(逗号分隔)',
    
    `max_file_size` INT DEFAULT 10 COMMENT '最大文件大小(MB)',
    `allowed_file_types` VARCHAR(100) DEFAULT 'image,document,archive' COMMENT '允许的文件类型(逗号分隔)',
    `storage_path` VARCHAR(255) DEFAULT '/uploads' COMMENT '文件存储路径',
    `auto_clean_expired_files` INT DEFAULT 0 COMMENT '自动清理过期文件(0-否,1-是)',
    `file_retention_days` INT DEFAULT 30 COMMENT '文件保留天数',
    `enable_compression` INT DEFAULT 0 COMMENT '启用文件压缩(0-否,1-是)',
    
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统设置表';

INSERT INTO `system_settings` (
    `system_name`, `system_description`, `page_size`, `logo`, `system_status`,
    `min_password_length`, `password_complexity`, `password_expire_days`, `max_login_attempts`,
    `lock_time`, `session_timeout`, `enable_two_factor`, `enable_email_notification`,
    `smtp_host`, `smtp_port`, `email_from`, `email_password`, `enable_system_notification`,
    `notification_types`, `max_file_size`, `allowed_file_types`, `storage_path`,
    `auto_clean_expired_files`, `file_retention_days`, `enable_compression`,
    `create_time`, `update_time`, `creator`, `updater`, `deleted`
) VALUES (
    '学生实习管理系统', '专业的学生实习管理平台', 10, '', 1,
    6, 'lowercase,number', 90, 5,
    30, 120, 0, 0,
    '', 465, '', '', 1,
    'login,system,internship', 10, 'image,document,archive', '/uploads',
    0, 30, 0,
    NOW(), NOW(), 'SYSTEM', 'SYSTEM', 0
);
