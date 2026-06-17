-- ============================================
-- DeepIntern 实习管理系统 - 数据库初始化脚本
-- 导出日期: 2026-06-11
-- 数据库: MySQL 8.0+
-- 说明: 全新部署时执行此脚本即可
-- ============================================

CREATE DATABASE IF NOT EXISTS internship DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE internship;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `admin_level` int NOT NULL DEFAULT '1' COMMENT '管理员级别',
  `department` varchar(100) DEFAULT NULL COMMENT '所属部门',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) NOT NULL DEFAULT 'ROLE_ADMIN' COMMENT '角色',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态（0:禁用 1:启用）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_analysis_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `creator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updater` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_admin_id` bigint DEFAULT NULL,
  `update_admin_id` bigint DEFAULT NULL,
  `deleted` int DEFAULT '0',
  `enable_keyword_library` int DEFAULT '1',
  `enable_scoring_rules` int DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI分析设置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_audit_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `audit_type` varchar(50) NOT NULL COMMENT '审核类型（RECALL_APPLY-撤回申请）',
  `target_id` bigint NOT NULL COMMENT '审核目标ID',
  `target_type` varchar(50) NOT NULL COMMENT '目标类型（COMPANY-公司/STUDENT-学生）',
  `recall_reason` text COMMENT '撤回原因',
  `audit_decision` varchar(20) NOT NULL COMMENT '审核决策（APPROVED-批准/REJECTED-拒绝/MANUAL-转人工）',
  `audit_reason` text COMMENT '审核理由',
  `risk_level` varchar(20) DEFAULT NULL COMMENT '风险等级（LOW-低/MEDIUM-中/HIGH-高）',
  `ai_remark` text COMMENT 'AI审核备注',
  `model_used` varchar(100) DEFAULT NULL COMMENT '使用的AI模型',
  `audit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
  `audit_duration` int DEFAULT NULL COMMENT '审核耗时（毫秒）',
  `status` tinyint DEFAULT '1' COMMENT '状态（1-成功/0-失败）',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_id`,`target_type`),
  KEY `idx_audit_type` (`audit_type`),
  KEY `idx_audit_time` (`audit_time`),
  KEY `idx_audit_decision` (`audit_decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI审核记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `model_code` varchar(50) NOT NULL COMMENT '模型代码（唯一标识）',
  `provider` varchar(50) NOT NULL COMMENT '提供商（deepseek/openai等）',
  `api_endpoint` varchar(255) DEFAULT NULL COMMENT 'API端点',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `max_tokens` int DEFAULT '4096' COMMENT '最大token数',
  `temperature` decimal(3,2) DEFAULT '0.70' COMMENT '温度参数',
  `description` text COMMENT '模型描述',
  `status` tinyint DEFAULT '1' COMMENT '状态（1启用/0禁用）',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认模型（1是/0否）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(50) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记（0未删除/1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `model_code` (`model_code`),
  KEY `idx_model_code` (`model_code`),
  KEY `idx_status` (`status`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `announcement_read_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `announcement_id` bigint NOT NULL COMMENT '公告ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `user_type` enum('STUDENT','TEACHER','ENTERPRISE','ADMIN') NOT NULL COMMENT '用户类型',
  `read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`,`user_id`,`user_type`),
  KEY `idx_read_record_announcement` (`announcement_id`),
  CONSTRAINT `announcement_read_records_ibfk_1` FOREIGN KEY (`announcement_id`) REFERENCES `announcements` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告阅读记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `announcements` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `title` varchar(255) NOT NULL COMMENT '公告标题',
  `content` longtext NOT NULL COMMENT '公告内容（富文本）',
  `publisher` varchar(50) NOT NULL COMMENT '发布者',
  `publisher_role` varchar(50) DEFAULT NULL COMMENT '发布人身份：ADMIN-管理员，COLLEGE-学院教师，DEPARTMENT-系室教师，COUNSELOR-辅导员',
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `valid_from` datetime DEFAULT NULL COMMENT '生效开始时间',
  `valid_to` datetime DEFAULT NULL COMMENT '生效结束时间',
  `target_type` varchar(255) DEFAULT NULL COMMENT '目标类型：JSON 数组，如 ["ALL","STUDENT"] 或单个值如 "ALL"',
  `target_value` varchar(100) DEFAULT NULL COMMENT '目标值：当target_type为DEPARTMENT时存部门ID，为MAJOR时存专业ID',
  `priority` enum('normal','important') DEFAULT 'normal' COMMENT '优先级：normal-普通，important-重要（重要公告置顶显示）',
  `attachments` json DEFAULT NULL COMMENT '附件列表，JSON格式',
  `status` enum('DRAFT','PUBLISHED','EXPIRED') NOT NULL DEFAULT 'DRAFT' COMMENT '公告状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_target_type` (`target_type`),
  KEY `idx_announcement_status_publish` (`status`,`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `backup_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型：BACKUP-备份，RESTORE-恢复，DOWNLOAD-下载，DELETE-删除，AUTO_BACKUP-自动备份，CLEAN_EXPIRED-清理过期',
  `operation_detail` varchar(500) DEFAULT NULL COMMENT '操作详情',
  `operator` varchar(100) DEFAULT NULL COMMENT '操作人',
  `operator_ip` varchar(50) DEFAULT NULL COMMENT '操作人IP',
  `status` int DEFAULT '0' COMMENT '状态：0-进行中，1-成功，2-失败',
  `error_message` text COMMENT '错误信息',
  `operation_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `duration` bigint DEFAULT '0' COMMENT '耗时（毫秒）',
  PRIMARY KEY (`id`),
  KEY `idx_operation_time` (`operation_time`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份操作审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `backup_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `backup_name` varchar(255) NOT NULL COMMENT '备份名称',
  `backup_path` varchar(500) NOT NULL COMMENT '备份文件路径',
  `backup_size` bigint DEFAULT '0' COMMENT '备份文件大小（字节）',
  `backup_type` varchar(50) DEFAULT 'FULL' COMMENT '备份类型：FULL-全量，INCREMENTAL-增量',
  `backup_time` datetime NOT NULL COMMENT '备份时间',
  `status` int DEFAULT '1' COMMENT '状态：0-失败，1-成功，2-进行中',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `backup_format` varchar(20) DEFAULT 'SQL' COMMENT '备份格式：SQL-SQL格式，CSV-CSV格式',
  `is_compressed` tinyint(1) DEFAULT '0' COMMENT '是否压缩：0-未压缩，1-已压缩',
  `checksum` varchar(64) DEFAULT NULL COMMENT '文件校验和（SHA-256）',
  `parent_backup_id` bigint DEFAULT NULL COMMENT '父备份ID（用于增量备份）',
  `table_list` text COMMENT '备份文件中包含的表列表',
  `table_count` int DEFAULT '0' COMMENT '备份文件中包含的表数量',
  PRIMARY KEY (`id`),
  KEY `idx_backup_time` (`backup_time`),
  KEY `idx_backup_type` (`backup_type`),
  KEY `idx_status` (`status`),
  KEY `idx_parent_backup_id` (`parent_backup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `backup_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `enabled` tinyint(1) DEFAULT '0' COMMENT '是否启用自动备份：0-禁用，1-启用',
  `frequency` varchar(20) DEFAULT 'daily' COMMENT '备份频率：daily-每天，weekly-每周，monthly-每月',
  `backup_time` varchar(10) DEFAULT '02:00' COMMENT '备份时间（HH:mm格式）',
  `retention_days` int DEFAULT '30' COMMENT '备份保留天数',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_execution_date` date DEFAULT NULL COMMENT '最后一次执行自动备份的日期',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动备份配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_weight` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_code` varchar(100) NOT NULL COMMENT '类别代码',
  `category_name` varchar(100) NOT NULL COMMENT '类别名称',
  `weight` int NOT NULL DEFAULT '0' COMMENT '权重值',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(100) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(100) DEFAULT NULL COMMENT '更新人',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='类别权重表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `name` varchar(100) DEFAULT '' COMMENT '会话名称（群聊使用，单聊自动生成）',
  `type` tinyint NOT NULL COMMENT '会话类型：1-单聊，2-群聊',
  `avatar` varchar(255) DEFAULT '' COMMENT '群聊头像URL',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `creator_type` tinyint NOT NULL COMMENT '创建者类型：1-学生，2-教师，3-管理员',
  `last_message_id` bigint DEFAULT '0' COMMENT '最后一条消息ID',
  `last_message_content` text COMMENT '最后一条消息内容',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后一条消息时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-正常，1-解散',
  PRIMARY KEY (`id`),
  KEY `idx_chat_creator` (`creator_id`,`creator_type`),
  KEY `idx_chat_type` (`type`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天会话表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `chat_id` bigint NOT NULL COMMENT '所属会话ID',
  `member_id` bigint NOT NULL COMMENT '成员ID',
  `member_type` tinyint NOT NULL COMMENT '成员类型：1-学生，2-教师，3-管理员',
  `member_name` varchar(50) NOT NULL COMMENT '成员名称',
  `avatar` varchar(255) DEFAULT '' COMMENT '成员头像URL',
  `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `role` tinyint NOT NULL DEFAULT '1' COMMENT '角色：1-普通成员，2-管理员，3-创建者',
  `last_read_message_id` bigint DEFAULT '0' COMMENT '最后读取消息ID',
  `unread_count` int NOT NULL DEFAULT '0' COMMENT '未读消息数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-正常，1-已退出',
  PRIMARY KEY (`id`),
  KEY `idx_chat_member_chat` (`chat_id`),
  KEY `idx_chat_member_member` (`member_id`,`member_type`),
  KEY `idx_chat_member_chat_member` (`chat_id`,`member_id`,`member_type`),
  KEY `idx_member_id` (`member_id`),
  CONSTRAINT `chat_member_ibfk_1` FOREIGN KEY (`chat_id`) REFERENCES `chat` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天成员表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `chat_id` bigint NOT NULL COMMENT '所属会话ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `sender_type` tinyint NOT NULL COMMENT '发送者类型：1-学生，2-教师，3-管理员',
  `sender_name` varchar(50) NOT NULL COMMENT '发送者名称',
  `type` tinyint NOT NULL COMMENT '消息类型：1-文本，2-图片，3-文件',
  `content` text NOT NULL COMMENT '消息内容',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '消息状态：1-发送成功，2-发送失败，3-已读',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_time` datetime DEFAULT NULL COMMENT '读取时间',
  PRIMARY KEY (`id`),
  KEY `idx_chat_message_chat` (`chat_id`),
  KEY `idx_chat_message_sender` (`sender_id`,`sender_type`),
  KEY `idx_sender_id` (`sender_id`),
  CONSTRAINT `chat_message_ibfk_1` FOREIGN KEY (`chat_id`) REFERENCES `chat` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '班级名称',
  `major_id` bigint DEFAULT NULL COMMENT '所属专业ID',
  `teacher_user_id` varchar(50) DEFAULT NULL COMMENT '负责老师ID',
  `grade` int DEFAULT NULL COMMENT '年级',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `student_count` int DEFAULT '0' COMMENT '学生人数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_name` (`name`),
  KEY `fk_class_major` (`major_id`),
  CONSTRAINT `fk_class_major` FOREIGN KEY (`major_id`) REFERENCES `major` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班级表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_counselor_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `class_name` varchar(100) DEFAULT NULL,
  `counselor_id` bigint NOT NULL,
  `counselor_name` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_counselor` (`class_id`,`counselor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `company_id` bigint NOT NULL COMMENT '企业 ID',
  `notification_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '通知内容',
  `priority` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '优先级：high, normal, low',
  `position_id` bigint DEFAULT NULL COMMENT '关联岗位 ID',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读：0-未读，1-已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业通知表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `company_name` varchar(255) NOT NULL COMMENT '公司名称',
  `contact_person` varchar(50) NOT NULL COMMENT '联系人',
  `contact_phone` varchar(20) NOT NULL COMMENT '联系电话',
  `contact_email` varchar(100) NOT NULL COMMENT '联系邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '个人邮箱',
  `username` varchar(255) DEFAULT NULL COMMENT '企业账号',
  `password` varchar(255) DEFAULT NULL COMMENT '密码',
  `role` varchar(50) DEFAULT 'ROLE_COMPANY' COMMENT '角色',
  `address` varchar(255) NOT NULL COMMENT '公司地址',
  `introduction` text COMMENT '公司简介',
  `business_license` varchar(500) DEFAULT NULL COMMENT '营业执照图片路径',
  `legal_id_card` varchar(1000) DEFAULT NULL COMMENT '法人身份证图片路径（正面，反面）',
  `is_internship_base` int DEFAULT '0' COMMENT '是否是挂牌实习基地：0-否，1-国家级，2-省级',
  `plaque_photo` varchar(500) DEFAULT NULL COMMENT '挂牌实习基地牌匾照片路径',
  `has_received_interns` int DEFAULT '0' COMMENT '是否曾经接收过我们学院的实习生：0-否，1-是',
  `current_employees_count` int DEFAULT '0' COMMENT '目前招收我院毕业生的在职员工数量',
  `accept_backup` int DEFAULT '0' COMMENT '是否接受兜底：0-否，1-是',
  `max_backup_students` int DEFAULT '0' COMMENT '能接受兜底的最多学生数量',
  `company_tag` varchar(50) DEFAULT NULL COMMENT '公司标签：双向选择/学生自主找的/接受兜底',
  `register_time` datetime DEFAULT NULL COMMENT '注册时间（用于时间线判断）',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_status` int DEFAULT '0' COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝',
  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审核备注（拒绝原因）',
  `recall_status` int DEFAULT '0' COMMENT '撤回申请状态：0-未申请，1-待确认撤回，2-撤回已批准，3-撤回已拒绝',
  `recall_reason` varchar(500) DEFAULT NULL COMMENT '撤回申请原因',
  `recall_apply_time` datetime DEFAULT NULL COMMENT '撤回申请时间',
  `recall_audit_time` datetime DEFAULT NULL COMMENT '撤回审核时间',
  `recall_reviewer_id` bigint DEFAULT NULL COMMENT '撤回审核人ID',
  `recall_audit_remark` varchar(500) DEFAULT NULL COMMENT '撤回审核备注',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `status` int DEFAULT '0' COMMENT '状态：0-待审核，1-正常，2-已禁用，3-已拒绝',
  `industry` varchar(50) DEFAULT NULL COMMENT '所属行业',
  `scale` varchar(20) DEFAULT NULL COMMENT '企业规模',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `website` varchar(255) DEFAULT NULL COMMENT '企业网站',
  `cooperation_mode` varchar(50) DEFAULT NULL COMMENT '合作模式',
  `logo` varchar(500) DEFAULT NULL COMMENT '企业 Logo URL',
  `photos` text COMMENT '企业照片 URL 数组（JSON 格式）',
  `videos` text COMMENT '企业视频 URL 数组（JSON 格式）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_company_status` (`status`),
  KEY `idx_company_recall` (`recall_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_ai_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID（teacher_user表id）',
  `enable_ai_scoring` int DEFAULT '0' COMMENT '是否启用AI评分：0-否，1-是',
  `enable_auto_trigger` int DEFAULT '0' COMMENT '是否自动触发AI分析：0-否，1-是',
  `ai_model_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'deepseek-chat' COMMENT 'AI模型编码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counselor_id` (`counselor_id`),
  KEY `idx_enable_ai_scoring` (`enable_ai_scoring`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员AI评分设置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_category_weight` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `category_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `category_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `weight` int DEFAULT '1' COMMENT '权重',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_category_code` (`category_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员分类权重表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_keyword` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `keyword` varchar(100) NOT NULL COMMENT '关键词',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='辅导员关键词库表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_scoring_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `rule_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `rule_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码（用于AI分析）',
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类',
  `weight` int DEFAULT '1' COMMENT '权重',
  `min_score` int DEFAULT '0' COMMENT '最低分',
  `max_score` int DEFAULT '100' COMMENT '最高分',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '规则描述',
  `evaluation_criteria` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '评估标准',
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '院系名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `teacher_count` int DEFAULT NULL COMMENT '教师数量',
  `student_count` int DEFAULT NULL COMMENT '学生数量',
  `confirmed_count` int DEFAULT '0' COMMENT '已确定实习的学生数',
  `not_found_count` int DEFAULT '0' COMMENT '未找到实习的学生数',
  `has_offer_count` int DEFAULT '0' COMMENT '有Offer未确定的学生数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院系表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `division` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系名称',
  `department_id` bigint NOT NULL COMMENT '所属学院ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `teacher_count` int DEFAULT '0',
  `student_count` int DEFAULT '0',
  `confirmed_count` int DEFAULT '0',
  `not_found_count` int DEFAULT '0',
  `has_offer_count` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_department_id` (`department_id`),
  CONSTRAINT `fk_division_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(255) NOT NULL COMMENT '文件路径',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(50) NOT NULL COMMENT '文件类型',
  `uploader_id` bigint NOT NULL COMMENT '上传人ID',
  `uploader_type` varchar(20) NOT NULL COMMENT '上传人类型（student/teacher/company）',
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型（实习申请/家长同意书等）',
  PRIMARY KEY (`id`),
  KEY `idx_uploader` (`uploader_id`,`uploader_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件上传记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_ai_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `overall_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `keywords` json DEFAULT NULL,
  `sentiment_positive` int DEFAULT NULL,
  `sentiment_neutral` int DEFAULT NULL,
  `sentiment_negative` int DEFAULT NULL,
  `suggested_attitude_score` int DEFAULT NULL,
  `suggested_performance_score` int DEFAULT NULL,
  `suggested_report_score` int DEFAULT NULL,
  `analysis_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习AI分析结果表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `student_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `student_user_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `position_id` bigint NOT NULL,
  `position_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `company_id` bigint NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `viewed` tinyint(1) DEFAULT '0',
  `view_time` datetime DEFAULT NULL COMMENT '查看时间',
  `viewed_time` datetime DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `school` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `education` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `major` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `skills` json DEFAULT NULL,
  `experience` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `self_evaluation` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `apply_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_position_id` (`position_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`),
  KEY `idx_student_status` (`student_id`,`status`),
  KEY `idx_apply_time` (`apply_time`),
  CONSTRAINT `fk_intern_app_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀹炰範鐢宠?琛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_confirmation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `student_name` varchar(100) DEFAULT NULL COMMENT '学生姓名',
  `student_user_id` varchar(50) DEFAULT NULL COMMENT '学号',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  `grade` varchar(50) DEFAULT NULL COMMENT '年级',
  `major` varchar(100) DEFAULT NULL COMMENT '专业',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `company_id` bigint DEFAULT NULL COMMENT '企业ID',
  `company_name` varchar(200) DEFAULT NULL COMMENT '企业名称',
  `company_address` varchar(500) DEFAULT NULL COMMENT '企业地址',
  `company_phone` varchar(50) DEFAULT NULL COMMENT '企业电话',
  `position_id` bigint DEFAULT NULL COMMENT '岗位ID',
  `position_name` varchar(200) DEFAULT NULL COMMENT '岗位名称',
  `internship_start_time` datetime DEFAULT NULL COMMENT '实习开始时间',
  `internship_end_time` datetime DEFAULT NULL COMMENT '实习结束时间',
  `internship_duration` int DEFAULT NULL COMMENT '实习时长（天）',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `status` int DEFAULT '0' COMMENT '状态：0=待企业确认，1=企业已确认，2=企业已拒绝',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `recall_status` int DEFAULT '0' COMMENT '撤回状态：0-未撤回，1-撤回申请中，2-已撤回',
  `recall_reason` varchar(500) DEFAULT NULL COMMENT '撤回原因',
  `recall_apply_time` datetime DEFAULT NULL COMMENT '撤回申请时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实习确认记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `attitude_score` int DEFAULT NULL,
  `performance_score` int DEFAULT NULL,
  `report_score` int DEFAULT NULL,
  `company_evaluation_score` int DEFAULT NULL,
  `total_score` int DEFAULT NULL,
  `grade` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `evaluator_id` bigint DEFAULT NULL,
  `evaluate_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `grade_published` tinyint(1) DEFAULT '0' COMMENT '成绩是否已发布 0-未发布 1-已发布',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_evaluator_id` (`evaluator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习评分表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_evaluation_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `evaluation_id` bigint NOT NULL COMMENT '实习评价 ID',
  `student_id` bigint NOT NULL COMMENT '学生 ID',
  `category_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别代码',
  `category_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别名称',
  `score` int NOT NULL COMMENT '类别得分',
  `weight` int NOT NULL COMMENT '类别权重',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_category` (`evaluation_id`,`category_code`),
  KEY `idx_evaluation_id` (`evaluation_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习评价类别评分详情表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_progress_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `event_type` varchar(50) NOT NULL COMMENT '事件类型',
  `event_title` varchar(200) NOT NULL COMMENT '事件标题',
  `description` varchar(500) DEFAULT NULL COMMENT '关联信息',
  `status` varchar(50) DEFAULT NULL COMMENT '状态',
  `related_id` bigint DEFAULT NULL COMMENT '关联原表ID',
  `event_time` datetime NOT NULL COMMENT '事件发生时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_reflection` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `student_name` varchar(100) NOT NULL COMMENT '学生姓名',
  `student_user_id` varchar(50) NOT NULL COMMENT '学号',
  `content` text NOT NULL COMMENT '实习心得内容',
  `remark` text COMMENT '备注信息',
  `internship_status_id` bigint DEFAULT NULL COMMENT '关联的实习状态ID',
  `period_number` int DEFAULT NULL COMMENT '第几期',
  `task_type` varchar(20) DEFAULT NULL COMMENT '任务类型: auto自动生成/manual手动',
  `deadline` datetime DEFAULT NULL COMMENT '提交截止日期',
  `counselor_id` bigint DEFAULT NULL COMMENT '辅导员ID',
  `submit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `ai_keywords` text COMMENT 'AI提取的关键词（JSON格式）',
  `ai_analysis` text COMMENT 'AI分析结果（JSON格式）',
  `ai_score` decimal(5,2) DEFAULT NULL COMMENT 'AI评分（0-100）',
  `ai_analysis_time` datetime DEFAULT NULL COMMENT 'AI分析时间',
  `status` tinyint DEFAULT '0' COMMENT '状态（0-待分析 1-已分析 2-分析失败）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记（0未删除/1已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_student_user_id` (`student_user_id`),
  KEY `idx_submit_time` (`submit_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生实习心得表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_time_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `creator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updater` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_admin_id` bigint DEFAULT NULL,
  `update_admin_id` bigint DEFAULT NULL,
  `deleted` int DEFAULT '0',
  `application_start_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `application_end_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_confirmation_deadline` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `delay_application_deadline` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_cycle` int DEFAULT NULL,
  `start_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `end_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_deadline` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evaluation_deadline` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approval_time_limit` int DEFAULT NULL COMMENT '审核时限（天）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习时间设置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `interview_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `company_id` bigint DEFAULT NULL,
  `position_id` bigint DEFAULT NULL,
  `position_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `interview_time` datetime DEFAULT NULL,
  `interview_location` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `interview_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_person` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闈㈣瘯閭??琛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `keyword_library` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(100) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(100) DEFAULT NULL COMMENT '更新人',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  `keyword` varchar(255) NOT NULL COMMENT '关键词',
  `category` varchar(100) DEFAULT NULL COMMENT '分类',
  `description` text COMMENT '描述',
  `weight` int DEFAULT '0' COMMENT '权重',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `usage_type` varchar(50) DEFAULT 'general' COMMENT '使用类型：general-通用，internship-实习相关，academic-学术相关',
  `related_tags` varchar(500) DEFAULT NULL COMMENT '相关标签',
  PRIMARY KEY (`id`),
  KEY `idx_keyword_category` (`category`),
  KEY `idx_keyword_status` (`status`),
  KEY `idx_keyword_usage_type` (`usage_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键词库表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(500) DEFAULT NULL COMMENT '资源标题',
  `description` text COMMENT '资源描述',
  `file_url` varchar(1000) DEFAULT NULL COMMENT '文件URL',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传者ID',
  `uploader_role` enum('STUDENT','TEACHER','ADMIN') DEFAULT NULL COMMENT '上传者角色',
  `status` enum('PENDING','APPROVED','REJECTED') DEFAULT NULL COMMENT '审核状态',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `ai_summary` text COMMENT 'AI摘要',
  `embedding_vector` longtext COMMENT '嵌入向量',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  `download_count` int DEFAULT '0' COMMENT '下载次数',
  PRIMARY KEY (`id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_status` (`status`),
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_uploader_role` (`uploader_role`),
  KEY `idx_status_lr` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习资源表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `log_clean_config` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `operator_role` varchar(20) NOT NULL COMMENT '操作人角色（STUDENT/TEACHER/ADMIN/COMPANY）',
  `clean_threshold` int NOT NULL DEFAULT '1000' COMMENT '清理阈值（日志数量达到此值时触发清理）',
  `keep_count` int NOT NULL DEFAULT '200' COMMENT '保留数量（清理后保留的最新日志数量）',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用（1-启用，0-禁用）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operator_role` (`operator_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日志清理配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(255) DEFAULT NULL COMMENT '用户ID',
  `user_type` varchar(50) DEFAULT NULL COMMENT '用户类型',
  `user_name` varchar(255) DEFAULT NULL COMMENT '用户名',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `device_info` text COMMENT '设备信息',
  `login_status` varchar(20) DEFAULT NULL COMMENT '登录状态',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_user` (`user_type`,`login_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '专业名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属院系ID',
  `division_id` bigint NOT NULL COMMENT '所属系ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `student_count` int DEFAULT NULL COMMENT '学生数量',
  `teacher_count` int DEFAULT NULL COMMENT '教师数量',
  `confirmed_count` int DEFAULT '0' COMMENT '已确定实习的学生数',
  `not_found_count` int DEFAULT '0' COMMENT '未找到实习的学生数',
  `has_offer_count` int DEFAULT '0' COMMENT '有Offer未确定的学生数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_name` (`name`),
  KEY `idx_division_id` (`division_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operate_log` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `operate_admin_id` bigint DEFAULT NULL COMMENT '操作管理员ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `operation_type` varchar(50) DEFAULT NULL COMMENT '操作类型（如LOGIN, LOGOUT, ADD等）',
  `module` varchar(100) DEFAULT NULL COMMENT '操作模块',
  `description` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `operation_result` varchar(20) DEFAULT 'SUCCESS' COMMENT '操作结果（SUCCESS/FAILURE）',
  `operate_time` datetime DEFAULT NULL COMMENT '操作时间',
  `operator_username` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `operator_role` varchar(20) DEFAULT NULL COMMENT '操作人角色',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '操作IP地址',
  PRIMARY KEY (`id`),
  KEY `idx_ip_address` (`ip_address`),
  KEY `idx_operation_result` (`operation_result`),
  KEY `idx_operate_time` (`operate_time`),
  KEY `idx_operator_role` (`operator_role`),
  KEY `idx_role_time` (`operator_role`,`operate_time` DESC),
  KEY `idx_type_time` (`operation_type`,`operate_time` DESC),
  KEY `idx_module_time` (`module`,`operate_time` DESC),
  KEY `idx_result_time` (`operation_result`,`operate_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(100) NOT NULL COMMENT '权限代码',
  `permission_name` varchar(100) NOT NULL COMMENT '权限名称',
  `permission_desc` varchar(255) DEFAULT NULL COMMENT '权限说明',
  `module` varchar(50) DEFAULT NULL COMMENT '所属模块',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `category_id` bigint DEFAULT NULL COMMENT '岗位类别ID',
  `position_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `department` varchar(100) DEFAULT NULL COMMENT '部门',
  `position_type` varchar(50) DEFAULT NULL COMMENT '岗位类型',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区/县',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `salary_min` int DEFAULT NULL COMMENT '最低薪资',
  `salary_max` int DEFAULT NULL COMMENT '最高薪资',
  `description` text COMMENT '岗位描述',
  `requirements` text COMMENT '岗位需求',
  `planned_recruit` int NOT NULL DEFAULT '0' COMMENT '计划招聘人数',
  `recruited_count` int NOT NULL DEFAULT '0' COMMENT '已招人数',
  `remaining_quota` int NOT NULL DEFAULT '0' COMMENT '剩余缺口',
  `status` varchar(20) DEFAULT 'open' COMMENT '状态',
  `publish_date` date DEFAULT NULL COMMENT '发布日期',
  `internship_start_date` date DEFAULT NULL COMMENT '实习开始日期',
  `internship_end_date` date DEFAULT NULL COMMENT '实习结束日期',
  `interview_time` datetime DEFAULT NULL,
  `interview_location` varchar(255) DEFAULT NULL,
  `interview_method` varchar(50) DEFAULT NULL,
  `interview_remark` text,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  PRIMARY KEY (`id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_position_category_id` (`category_id`),
  CONSTRAINT `fk_position_category` FOREIGN KEY (`category_id`) REFERENCES `position_category` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `position_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '类别ID',
  `name` varchar(50) NOT NULL COMMENT '类别名称',
  `description` varchar(200) DEFAULT NULL COMMENT '类别描述',
  `position_count` int DEFAULT '0' COMMENT '岗位数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位类别表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `position_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `position_id` bigint NOT NULL COMMENT '岗位ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_student` (`position_id`,`student_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职位收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `position_view_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `position_id` bigint NOT NULL COMMENT '岗位ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `view_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_student` (`position_id`,`student_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_position_id` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职位浏览记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_type` varchar(20) NOT NULL COMMENT '用户类型：student-学生，teacher-教师，company-企业',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `user_name` varchar(100) NOT NULL COMMENT '用户姓名',
  `user_account` varchar(100) NOT NULL COMMENT '用户账号',
  `title` varchar(255) NOT NULL COMMENT '反馈标题',
  `content` text NOT NULL COMMENT '反馈内容',
  `feedback_type` varchar(50) DEFAULT 'other' COMMENT '反馈类型：system-系统问题，feature-功能建议，bug-程序错误，data-数据问题，other-其他',
  `status` varchar(20) DEFAULT 'processing' COMMENT '处理状态：processing-处理中，resolved-已解决，closed-感谢您的反馈',
  `priority` varchar(20) DEFAULT 'normal' COMMENT '优先级：low-低，normal-中，high-高',
  `attachment_url` varchar(500) DEFAULT NULL COMMENT '附件URL',
  `admin_reply` text COMMENT '管理员回复',
  `admin_id` bigint DEFAULT NULL COMMENT '处理管理员ID',
  `admin_name` varchar(100) DEFAULT NULL COMMENT '处理管理员姓名',
  `reply_time` datetime DEFAULT NULL COMMENT '回复时间',
  `resolve_time` datetime DEFAULT NULL COMMENT '解决时间',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_feedback_user_type` (`user_type`),
  KEY `idx_feedback_status` (`status`),
  KEY `idx_feedback_priority` (`priority`),
  KEY `idx_feedback_type` (`feedback_type`),
  KEY `idx_feedback_user_id` (`user_id`),
  KEY `idx_feedback_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问题反馈表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resource_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(255) NOT NULL COMMENT '文档标题',
  `description` text COMMENT '文档描述',
  `file_url` varchar(1000) DEFAULT NULL COMMENT '文件URL',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名',
  `file_type` varchar(150) DEFAULT NULL COMMENT '文件类型（MIME类型）',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `publisher_id` bigint DEFAULT NULL COMMENT '发布人ID',
  `publisher` varchar(50) DEFAULT NULL COMMENT '发布人姓名',
  `publisher_role` varchar(50) DEFAULT NULL COMMENT '发布人身份：ADMIN-管理员，COLLEGE-学院教师，DEPARTMENT-系室教师，COUNSELOR-辅导员，TEACHER-教师',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `target_type` varchar(50) DEFAULT 'ALL' COMMENT '目标类型：ALL-全体师生，STUDENT-全体学生，TEACHER-全体教师',
  `target_value` varchar(255) DEFAULT NULL COMMENT '目标值',
  `status` enum('DRAFT','PUBLISHED','ARCHIVED') DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿，PUBLISHED-已发布，ARCHIVED-已归档',
  `download_count` int DEFAULT '0' COMMENT '下载次数',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_publisher_id` (`publisher_id`),
  KEY `idx_publisher_role` (`publisher_role`),
  KEY `idx_status` (`status`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_target_type` (`target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源文档表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resource_view_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `user_id` varchar(50) NOT NULL,
  `user_type` varchar(30) NOT NULL,
  `action_type` varchar(20) NOT NULL DEFAULT 'view' COMMENT 'view/download',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_resource_user` (`resource_id`,`user_id`,`user_type`,`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源查看/下载记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(50) NOT NULL COMMENT '角色代码',
  `permission_code` varchar(100) NOT NULL COMMENT '权限代码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_code`,`permission_code`),
  KEY `permission_code` (`permission_code`),
  CONSTRAINT `role_permissions_ibfk_1` FOREIGN KEY (`permission_code`) REFERENCES `permissions` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `role_desc` varchar(255) DEFAULT NULL COMMENT '角色描述',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scoring_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(100) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(100) DEFAULT NULL COMMENT '更新人',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  `rule_name` varchar(255) NOT NULL COMMENT '规则名称',
  `rule_code` varchar(100) DEFAULT NULL COMMENT '规则编码（用于AI分析）',
  `rule_type` varchar(100) DEFAULT NULL COMMENT '规则类型',
  `category` varchar(100) DEFAULT NULL COMMENT '分类',
  `min_score` int DEFAULT '0' COMMENT '最低分',
  `max_score` int DEFAULT '100' COMMENT '最高分',
  `description` text COMMENT '描述',
  `evaluation_criteria` text COMMENT '评估标准',
  `weight` int DEFAULT '1' COMMENT '权重',
  `sort_order` int DEFAULT '0' COMMENT '排序字段，用于同一category内的排序',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `applicable_scenarios` varchar(500) DEFAULT NULL COMMENT '适用场景',
  PRIMARY KEY (`id`),
  KEY `idx_scoring_rule_category` (`category`),
  KEY `idx_scoring_rule_status` (`status`),
  KEY `idx_scoring_rule_type` (`rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评分规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `student_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_user_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `class_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `application_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `company` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_company` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `new_company` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `materials` json DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reject_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `apply_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`),
  KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生申请表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `file_type` varchar(50) DEFAULT NULL COMMENT '材料类型',
  `file_name` varchar(200) DEFAULT NULL COMMENT '文件名',
  `file_url` varchar(500) NOT NULL COMMENT '文件地址',
  `upload_time` datetime DEFAULT NULL COMMENT '上传时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint DEFAULT '0' COMMENT '状态:0待审核,1已通过,2已拒绝',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_student_id_sa` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生档案表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_internship_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `student_name` varchar(100) DEFAULT NULL COMMENT '瀛︾敓濮撳悕',
  `gender` varchar(10) DEFAULT NULL COMMENT '鎬у埆',
  `school` varchar(200) DEFAULT NULL COMMENT '瀛︽牎',
  `grade` varchar(50) DEFAULT NULL COMMENT '骞寸骇',
  `education` varchar(50) DEFAULT NULL COMMENT '瀛﹀巻',
  `college` varchar(200) DEFAULT NULL COMMENT '瀛﹂櫌',
  `major_name` varchar(100) DEFAULT NULL COMMENT '涓撲笟鍚嶇О',
  `class_name` varchar(100) DEFAULT NULL COMMENT '鐝?骇',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '鑱旂郴鐢佃瘽',
  `email` varchar(100) DEFAULT NULL COMMENT '鐢靛瓙閭??',
  `status` int NOT NULL COMMENT '实习状态（0未找到，1已有offer，2已确定）',
  `company_confirm_status` int DEFAULT '0' COMMENT '企业确认状态（0:未确认 1:已确认 2:已拒绝）',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `company_name` varchar(200) DEFAULT NULL COMMENT '浼佷笟鍚嶇О',
  `position_name` varchar(100) DEFAULT NULL COMMENT '宀椾綅鍚嶇О',
  `company_address` varchar(500) DEFAULT NULL COMMENT '浼佷笟鍦板潃',
  `company_phone` varchar(20) DEFAULT NULL COMMENT '浼佷笟鑱旂郴鐢佃瘽',
  `graduate_school` varchar(200) DEFAULT NULL COMMENT '鑰冪爺闄㈡牎',
  `graduate_major` varchar(100) DEFAULT NULL COMMENT '鑰冪爺涓撲笟',
  `other_reason` text COMMENT '鍏朵粬鍘熷洜璇存槑',
  `position_id` bigint DEFAULT NULL COMMENT '岗位ID',
  `has_complaint` tinyint(1) DEFAULT '0' COMMENT '是否有投诉（0否，1是）',
  `is_delayed` tinyint(1) DEFAULT '0' COMMENT '是否延期（0否，1是）',
  `is_interrupted` tinyint(1) DEFAULT '0' COMMENT '是否中断（0否，1是）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `internship_start_time` datetime DEFAULT NULL COMMENT '实习开始时间',
  `internship_end_time` datetime DEFAULT NULL COMMENT '实习结束时间',
  `internship_duration` int DEFAULT NULL COMMENT '实习时长（天）',
  `feedback` text COMMENT '实习反馈',
  `remark` text COMMENT '备注',
  `recall_status` int DEFAULT '0' COMMENT '撤回申请状态：0-未申请，1-待审核撤回，2-撤回已批准，3-撤回已拒绝',
  `recall_reason` varchar(500) DEFAULT NULL COMMENT '撤回申请原因',
  `recall_apply_time` datetime DEFAULT NULL COMMENT '撤回申请时间',
  `recall_audit_time` datetime DEFAULT NULL COMMENT '撤回审核时间',
  `recall_reviewer_id` bigint DEFAULT NULL COMMENT '撤回审核人ID',
  `recall_audit_remark` varchar(500) DEFAULT NULL COMMENT '撤回审核备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_student_id` (`student_id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_position_id` (`position_id`),
  KEY `idx_status_sis` (`status`),
  KEY `idx_recall_status` (`recall_status`),
  KEY `idx_has_complaint` (`has_complaint`),
  KEY `idx_is_delayed` (`is_delayed`),
  KEY `idx_student_name` (`student_name`),
  KEY `idx_grade` (`grade`),
  KEY `idx_education` (`education`),
  CONSTRAINT `fk_status_company` FOREIGN KEY (`company_id`) REFERENCES `company_users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_status_position` FOREIGN KEY (`position_id`) REFERENCES `position` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_status_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生实习状态表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_job_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `position_id` bigint DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `position_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `salary` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cover_letter` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `resume_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `major` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `self_introduction` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `apply_date` date DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_job_app_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀛︾敓姹傝亴鐢宠?琛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_reflection_ai_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reflection_id` bigint NOT NULL COMMENT '实习心得ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `overall_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '整体分析结果',
  `keywords` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '关键词列表（JSON格式）',
  `sentiment_positive` int DEFAULT '0' COMMENT '情感正面度（0-100）',
  `sentiment_neutral` int DEFAULT '0' COMMENT '情感中性度（0-100）',
  `sentiment_negative` int DEFAULT '0' COMMENT '情感负面度（0-100）',
  `score_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '各项评分详情（JSON格式）',
  `total_score` decimal(5,2) DEFAULT '0.00' COMMENT '总分',
  `grade` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '等级：优秀、良好、中等、及格、不及格',
  `analysis_report` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '完整分析报告（Markdown格式）',
  `ai_model_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的AI模型编码',
  `analysis_time` datetime DEFAULT NULL COMMENT '分析时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reflection_id` (`reflection_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_analysis_time` (`analysis_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习心得AI分析结果表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_reflection_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reflection_id` bigint NOT NULL COMMENT '实习心得ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `ai_analysis_id` bigint DEFAULT NULL COMMENT 'AI分析结果ID',
  `score_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '各项评分详情（JSON格式，包含AI评分和教师修改后的评分）',
  `total_score` decimal(5,2) DEFAULT '0.00' COMMENT '总分',
  `grade` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '等级',
  `teacher_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '教师评语',
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `teacher_id` bigint NOT NULL COMMENT '教师ID',
  `content` varchar(500) NOT NULL COMMENT '提醒内容',
  `is_confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '是否已确认(0否,1是)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_is_confirmed` (`is_confirmed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生提醒表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` tinyint DEFAULT '1' COMMENT '性别：1-男，2-女',
  `student_user_id` varchar(50) NOT NULL COMMENT '学号',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID',
  `grade` int DEFAULT NULL COMMENT '年级',
  `class_id` bigint DEFAULT NULL COMMENT '班级ID',
  `role` varchar(20) NOT NULL DEFAULT 'ROLE_STUDENT' COMMENT '角色',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态（1启用，0禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `phone` varchar(20) DEFAULT NULL COMMENT '电话',
  `school` varchar(100) DEFAULT NULL COMMENT '学院',
  `department` varchar(100) DEFAULT NULL COMMENT '系/部门',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `classes` varchar(50) DEFAULT NULL,
  `avatar` varchar(512) DEFAULT NULL COMMENT '头像URL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_student_id` (`student_user_id`),
  KEY `idx_grade` (`grade`),
  KEY `idx_student_major` (`major_id`),
  KEY `idx_student_class` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `config_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'string' COMMENT '配置类型：string-字符串，number-数字，boolean-布尔值',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置描述',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`),
  KEY `idx_config_key` (`config_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `creator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `updater` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标记 0-未删除 1-已删除',
  `system_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '学生实习管理系统' COMMENT '系统名称',
  `system_description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '专业的学生实习管理平台' COMMENT '系统描述',
  `page_size` int DEFAULT '10' COMMENT '每页显示条数',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系统Logo URL',
  `system_status` tinyint(1) DEFAULT '1' COMMENT '系统状态 0-禁用 1-启用',
  `min_password_length` int DEFAULT '6' COMMENT '密码最小长度',
  `password_complexity` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'lowercase,number' COMMENT '密码复杂度要求',
  `password_expire_days` int DEFAULT '90' COMMENT '密码过期天数',
  `max_login_attempts` int DEFAULT '5' COMMENT '登录失败锁定次数',
  `lock_time` int DEFAULT '30' COMMENT '锁定时间(分钟)',
  `session_timeout` int DEFAULT '120' COMMENT '会话超时时间(分钟)',
  `dual_selection_start_date` date DEFAULT NULL COMMENT '双向选择阶段开始日期',
  `dual_selection_end_date` date DEFAULT NULL COMMENT '双向选择阶段结束日期',
  `internship_start_date` date DEFAULT NULL COMMENT '实习开始日期',
  `internship_end_date` date DEFAULT NULL COMMENT '实习结束日期',
  `report_submission_cycle` int DEFAULT '7' COMMENT '实习心得提交周期（天）',
  `enable_two_factor` tinyint(1) DEFAULT '0' COMMENT '启用双因素认证 0-禁用 1-启用',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_update_time` (`update_time`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统设置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `permission_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `permission_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限类型：MENU/BUTTON/API',
  `parent_id` bigint DEFAULT '0' COMMENT '父权限ID',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由路径',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '菜单图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称：学院/各系室/辅导员',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码：COLLEGE/DEPARTMENT/COUNSELOR',
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` tinyint DEFAULT '1' COMMENT '性别：1-男，2-女',
  `teacher_user_id` varchar(50) NOT NULL COMMENT '教师工号',
  `department_id` varchar(100) DEFAULT NULL COMMENT '所属部门',
  `division_id` bigint DEFAULT NULL COMMENT '所属系ID',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `role` varchar(50) NOT NULL DEFAULT 'ROLE_TEACHER',
  `teacher_type` varchar(20) DEFAULT NULL COMMENT '教师类型：COLLEGE-学院，DEPARTMENT-系室，COUNSELOR-辅导员',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态（1启用，0禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_teacher_id` (`teacher_user_id`),
  KEY `idx_department_id` (`department_id`),
  KEY `idx_teacher_division_id` (`division_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_file` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '模板名称',
  `category` varchar(50) DEFAULT NULL COMMENT '分类:实习申请/自主联系/延迟下点/考研',
  `file_url` varchar(500) NOT NULL COMMENT '文件地址',
  `description` varchar(500) DEFAULT NULL COMMENT '说明',
  `download_count` int DEFAULT '0' COMMENT '下载次数',
  `status` tinyint DEFAULT '1' COMMENT '状态:0禁用,1启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
