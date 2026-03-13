-- 创建撤回申请记录表

CREATE TABLE IF NOT EXISTS `application_withdrawal_records` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录 ID',
  `status_id` BIGINT NOT NULL COMMENT '实习状态 ID',
  `applicant_id` BIGINT NOT NULL COMMENT '申请人 ID',
  `applicant_role` VARCHAR(50) NOT NULL COMMENT '申请人身份（ROLE_STUDENT 或 ROLE_COMPANY）',
  `withdrawal_reason` VARCHAR(500) NOT NULL COMMENT '撤回原因',
  `withdrawal_time` DATETIME NOT NULL COMMENT '撤回时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  INDEX `idx_status_id` (`status_id`),
  INDEX `idx_applicant_id` (`applicant_id`),
  INDEX `idx_applicant_role` (`applicant_role`),
  INDEX `idx_withdrawal_time` (`withdrawal_time`),
  FOREIGN KEY (`status_id`) REFERENCES `student_internship_status`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='撤回申请记录表';

-- 查看表结构
DESC `application_withdrawal_records`;
