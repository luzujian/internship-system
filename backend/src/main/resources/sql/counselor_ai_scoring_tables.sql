-- ========================================
-- 辅导员AI评分功能相关表
-- 创建时间：2026-03-18
-- ========================================

-- 1. 辅导员AI评分设置表
CREATE TABLE IF NOT EXISTS `counselor_ai_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID（teacher_user表id）',
  `enable_ai_scoring` int DEFAULT '0' COMMENT '是否启用AI评分：0-否，1-是',
  `ai_model_code` varchar(100) DEFAULT 'deepseek-chat' COMMENT 'AI模型编码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counselor_id` (`counselor_id`),
  KEY `idx_enable_ai_scoring` (`enable_ai_scoring`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员AI评分设置表';

-- 2. 辅导员评分规则表（每个辅导员独立的评分规则）
CREATE TABLE IF NOT EXISTS `counselor_scoring_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `rule_name` varchar(255) NOT NULL COMMENT '规则名称',
  `rule_code` varchar(100) NOT NULL COMMENT '规则编码（用于AI分析）',
  `weight` int DEFAULT '1' COMMENT '权重',
  `min_score` int DEFAULT '0' COMMENT '最低分',
  `max_score` int DEFAULT '100' COMMENT '最高分',
  `description` text COMMENT '规则描述',
  `evaluation_criteria` text COMMENT '评估标准',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_rule_code` (`rule_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员评分规则表';

-- 3. 学生实习心得AI分析结果表（增强版）
CREATE TABLE IF NOT EXISTS `student_reflection_ai_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reflection_id` bigint NOT NULL COMMENT '实习心得ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `overall_analysis` text COMMENT '整体分析结果',
  `keywords` text COMMENT '关键词列表（JSON格式）',
  `sentiment_positive` int DEFAULT '0' COMMENT '情感正面度（0-100）',
  `sentiment_neutral` int DEFAULT '0' COMMENT '情感中性度（0-100）',
  `sentiment_negative` int DEFAULT '0' COMMENT '情感负面度（0-100）',
  `score_details` text COMMENT '各项评分详情（JSON格式）',
  `total_score` decimal(5,2) DEFAULT '0.00' COMMENT '总分',
  `grade` varchar(20) DEFAULT NULL COMMENT '等级：优秀、良好、中等、及格、不及格',
  `analysis_report` text COMMENT '完整分析报告（Markdown格式）',
  `ai_model_code` varchar(100) DEFAULT NULL COMMENT '使用的AI模型编码',
  `analysis_time` datetime DEFAULT NULL COMMENT '分析时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reflection_id` (`reflection_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_analysis_time` (`analysis_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习心得AI分析结果表';

-- 4. 学生实习评分表（增强版）
CREATE TABLE IF NOT EXISTS `student_reflection_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reflection_id` bigint NOT NULL COMMENT '实习心得ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `ai_analysis_id` bigint DEFAULT NULL COMMENT 'AI分析结果ID',
  `score_details` text COMMENT '各项评分详情（JSON格式，包含AI评分和教师修改后的评分）',
  `total_score` decimal(5,2) DEFAULT '0.00' COMMENT '总分',
  `grade` varchar(20) DEFAULT NULL COMMENT '等级',
  `teacher_comment` text COMMENT '教师评语',
  `is_ai_score_modified` int DEFAULT '0' COMMENT 'AI评分是否被修改：0-否，1-是',
  `evaluate_time` datetime DEFAULT NULL COMMENT '评分时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reflection_id` (`reflection_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_evaluate_time` (`evaluate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习评分表';

-- 5. 班级辅导员关联表（一个班级可能有多个辅导员，一个辅导员管理多个班级）
CREATE TABLE IF NOT EXISTS `class_counselor_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级名称',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `counselor_name` varchar(100) DEFAULT NULL COMMENT '辅导员姓名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_counselor` (`class_id`, `counselor_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_counselor_id` (`counselor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级辅导员关联表';

-- ========================================
-- 初始化数据
-- ========================================

-- 插入默认的辅导员评分规则模板
-- 注意：实际使用时，每个辅导员需要根据自己的需求配置

-- ========================================
-- SQL脚本结束
-- ========================================
