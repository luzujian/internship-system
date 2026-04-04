-- AI审核记录表
CREATE TABLE IF NOT EXISTS `ai_audit_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `audit_type` varchar(50) NOT NULL COMMENT '审核类型（RECALL_APPLY-撤回申请）',
  `target_id` bigint NOT NULL COMMENT '审核目标ID',
  `target_type` varchar(50) NOT NULL COMMENT '目标类型（COMPANY-企业/STUDENT-学生）',
  `recall_reason` text COMMENT '撤回原因',
  `audit_decision` varchar(20) NOT NULL COMMENT '审核决策（APPROVED-批准/REJECTED-拒绝/MANUAL-转人工）',
  `audit_reason` text COMMENT '审核理由',
  `risk_level` varchar(20) COMMENT '风险等级（LOW-低/MEDIUM-中/HIGH-高）',
  `ai_remark` text COMMENT 'AI审核备注',
  `model_used` varchar(100) COMMENT '使用的AI模型',
  `audit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
  `audit_duration` int COMMENT '审核耗时（毫秒）',
  `status` tinyint DEFAULT '1' COMMENT '状态（1-成功/0-失败）',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_id`, `target_type`),
  KEY `idx_audit_type` (`audit_type`),
  KEY `idx_audit_time` (`audit_time`),
  KEY `idx_audit_decision` (`audit_decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI审核记录表';

-- 插入AI审核系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `config_type`, `description`, `create_time`, `update_time`) VALUES
('ai_recall_audit_enabled', 'true', 'BOOLEAN', '是否启用AI自动审核撤回申请', NOW(), NOW()),
('ai_recall_audit_model', 'deepseek-reasoner', 'STRING', 'AI审核使用的模型', NOW(), NOW()),
('ai_recall_audit_threshold', '0.7', 'DECIMAL', 'AI审核批准阈值', NOW(), NOW()),
('ai_recall_audit_timeout', '30', 'INTEGER', 'AI审核超时时间（秒）', NOW(), NOW()),
('ai_recall_audit_max_retry', '3', 'INTEGER', 'AI审核最大重试次数', NOW(), NOW())
ON DUPLICATE KEY UPDATE `update_time` = NOW();
