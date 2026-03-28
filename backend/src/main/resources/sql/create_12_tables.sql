-- ========================================
-- 12 张核心表的 SQL 创建脚本
-- 生成时间：2026-03-17
-- ========================================

-- ========================================
-- 一、用户体系表（5 张）
-- ========================================

-- 1. user - 基础用户表（统一认证）
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码（加密）',
  `role` varchar(50) NOT NULL COMMENT '角色：ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN, ROLE_COMPANY',
  `name` varchar(100) NOT NULL COMMENT '姓名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础用户表（统一认证）';

-- 2. admin_user - 管理员表
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码（加密）',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `role` varchar(50) DEFAULT 'ROLE_ADMIN' COMMENT '角色（固定为 ROLE_ADMIN）',
  `status` int DEFAULT '1' COMMENT '用户状态：1-启用，0-禁用',
  `admin_level` int DEFAULT '1' COMMENT '管理员级别',
  `department` varchar(100) DEFAULT NULL COMMENT '所属部门',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- 3. student_user - 学生表
CREATE TABLE IF NOT EXISTS `student_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) DEFAULT NULL COMMENT '密码（加密）',
  `role` varchar(50) DEFAULT 'ROLE_STUDENT' COMMENT '角色（固定为 ROLE_STUDENT）',
  `name` varchar(100) NOT NULL COMMENT '姓名',
  `gender` int DEFAULT NULL COMMENT '性别',
  `status` int DEFAULT '1' COMMENT '用户状态：1-启用，0-禁用',
  `student_user_id` varchar(50) DEFAULT NULL COMMENT '学号',
  `major_id` bigint DEFAULT NULL COMMENT '专业 ID',
  `grade` int DEFAULT NULL COMMENT '年级',
  `class_id` bigint DEFAULT NULL COMMENT '班级 ID',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_user_id` (`student_user_id`),
  KEY `idx_major_id` (`major_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_grade` (`grade`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生表';

-- 4. teacher_user - 教师表（含类型字段）
CREATE TABLE IF NOT EXISTS `teacher_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) DEFAULT NULL COMMENT '密码（加密）',
  `role` varchar(50) DEFAULT 'ROLE_TEACHER' COMMENT '角色（固定为 ROLE_TEACHER）',
  `name` varchar(100) NOT NULL COMMENT '姓名',
  `gender` int DEFAULT NULL COMMENT '性别',
  `status` int DEFAULT '1' COMMENT '用户状态：1-启用，0-禁用',
  `teacher_user_id` varchar(50) DEFAULT NULL COMMENT '教师工号',
  `department_id` varchar(50) DEFAULT NULL COMMENT '部门 ID',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `teacher_type` varchar(50) DEFAULT NULL COMMENT '教师类型：COLLEGE-学院，DEPARTMENT-系室，COUNSELOR-辅导员',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_user_id` (`teacher_user_id`),
  KEY `idx_department_id` (`department_id`),
  KEY `idx_teacher_type` (`teacher_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师表（含类型字段）';

-- 5. company_user - 企业表
CREATE TABLE IF NOT EXISTS `company_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `company_name` varchar(255) NOT NULL COMMENT '公司名称',
  `contact_person` varchar(50) NOT NULL COMMENT '联系人',
  `contact_phone` varchar(20) NOT NULL COMMENT '联系电话',
  `contact_email` varchar(100) NOT NULL COMMENT '联系邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) DEFAULT NULL COMMENT '密码（加密）',
  `role` varchar(50) DEFAULT 'ROLE_COMPANY' COMMENT '角色（固定为 ROLE_COMPANY）',
  `address` varchar(255) NOT NULL COMMENT '公司地址',
  `introduction` text COMMENT '企业简介',
  `business_license` varchar(500) DEFAULT NULL COMMENT '营业执照',
  `legal_id_card` varchar(100) DEFAULT NULL COMMENT '法人身份证',
  `is_internship_base` int DEFAULT '0' COMMENT '是否实习基地：0-否，1-是',
  `plaque_photo` varchar(500) DEFAULT NULL COMMENT '牌匾照片',
  `has_received_interns` int DEFAULT '0' COMMENT '是否接收过实习生：0-否，1-是',
  `current_employees_count` int DEFAULT '0' COMMENT '当前员工数',
  `accept_backup` int DEFAULT '0' COMMENT '是否接受调剂：0-否，1-是',
  `max_backup_students` bigint DEFAULT '0' COMMENT '最大调剂学生数',
  `company_tag` varchar(100) DEFAULT NULL COMMENT '企业标签',
  `industry` varchar(100) DEFAULT NULL COMMENT '行业',
  `scale` varchar(50) DEFAULT NULL COMMENT '公司规模',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `website` varchar(255) DEFAULT NULL COMMENT '公司网站',
  `description` text COMMENT '企业描述',
  `cooperation_mode` varchar(50) DEFAULT NULL COMMENT '合作模式',
  `logo` varchar(500) DEFAULT NULL COMMENT '公司 Logo',
  `photos` text COMMENT '企业照片',
  `videos` text COMMENT '企业视频',
  `register_time` datetime DEFAULT NULL COMMENT '注册时间',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_status` int DEFAULT '0' COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝',
  `audit_remark` text COMMENT '审核备注',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人 ID',
  `status` int DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `recall_status` int DEFAULT '0' COMMENT '撤回状态：0-未撤回，1-已撤回（待确认）,2-已确认撤回',
  `recall_reason` text COMMENT '撤回原因',
  `recall_apply_time` datetime DEFAULT NULL COMMENT '撤回申请时间',
  `recall_audit_time` datetime DEFAULT NULL COMMENT '撤回审核时间',
  `recall_reviewer_id` bigint DEFAULT NULL COMMENT '撤回审核人 ID',
  `recall_audit_remark` text COMMENT '撤回审核备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_audit_status` (`audit_status`),
  KEY `idx_status` (`status`),
  KEY `idx_recall_status` (`recall_status`),
  KEY `idx_is_internship_base` (`is_internship_base`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业表';

-- ========================================
-- 二、AI 模块表（4 张）
-- ========================================

-- 6. ai_model - AI 模型配置表
CREATE TABLE IF NOT EXISTS `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `model_code` varchar(100) NOT NULL COMMENT '模型编码',
  `provider` varchar(100) DEFAULT NULL COMMENT '提供商',
  `api_endpoint` varchar(500) DEFAULT NULL COMMENT 'API 端点',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API 密钥',
  `max_tokens` int DEFAULT NULL COMMENT '最大 token 数',
  `temperature` decimal(5,2) DEFAULT NULL COMMENT '温度参数（0-2）',
  `description` text COMMENT '模型描述',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `is_default` int DEFAULT '0' COMMENT '是否默认模型：0-否，1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(100) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(100) DEFAULT NULL COMMENT '更新人',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`),
  KEY `idx_status` (`status`),
  KEY `idx_is_default` (`is_default`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置表（temperature、maxTokens）';

-- 7. keyword_library - 关键词库表
CREATE TABLE IF NOT EXISTS `keyword_library` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
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
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_usage_type` (`usage_type`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键词库表';

-- 8. scoring_rule - 评分规则表（五阶段评分标准）
CREATE TABLE IF NOT EXISTS `scoring_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(100) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(100) DEFAULT NULL COMMENT '更新人',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  `rule_name` varchar(255) NOT NULL COMMENT '规则名称',
  `rule_type` varchar(100) DEFAULT NULL COMMENT '规则类型',
  `category` varchar(100) DEFAULT NULL COMMENT '分类',
  `min_score` int DEFAULT '0' COMMENT '最低分',
  `max_score` int DEFAULT '100' COMMENT '最高分',
  `description` text COMMENT '描述',
  `evaluation_criteria` text COMMENT '评估标准',
  `weight` int DEFAULT '1' COMMENT '权重',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `applicable_scenarios` varchar(500) DEFAULT NULL COMMENT '适用场景',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_rule_type` (`rule_type`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分规则表（五阶段评分标准）';

-- 9. internship_ai_analysis - AI 分析结果表
CREATE TABLE IF NOT EXISTS `internship_ai_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `student_id` bigint NOT NULL COMMENT '学生 ID',
  `overall_analysis` text COMMENT '整体分析结果',
  `keywords` text COMMENT '关键词列表（JSON 格式）',
  `sentiment_positive` int DEFAULT '0' COMMENT '情感正面度（0-100）',
  `sentiment_neutral` int DEFAULT '0' COMMENT '情感中性度（0-100）',
  `sentiment_negative` int DEFAULT '0' COMMENT '情感负面度（0-100）',
  `suggested_attitude_score` int DEFAULT '0' COMMENT '建议态度分（0-100）',
  `suggested_performance_score` int DEFAULT '0' COMMENT '建议表现分（0-100）',
  `suggested_report_score` int DEFAULT '0' COMMENT '建议报告分（0-100）',
  `analysis_time` datetime DEFAULT NULL COMMENT '分析时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_analysis_time` (`analysis_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 分析结果表';

-- ========================================
-- 三、实习业务表（3 张）
-- ========================================

-- 10. student_internship_status - 学生实习状态表
CREATE TABLE IF NOT EXISTS `student_internship_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `student_id` bigint NOT NULL COMMENT '学生 ID',
  `status` int DEFAULT '0' COMMENT '实习状态：0-未找到，1-已有 offer，2-已确定，3-已结束',
  `company_id` bigint DEFAULT NULL COMMENT '企业 ID',
  `position_id` bigint DEFAULT NULL COMMENT '岗位 ID',
  `has_complaint` tinyint(1) DEFAULT '0' COMMENT '是否有投诉：0-否，1-是',
  `is_delayed` tinyint(1) DEFAULT '0' COMMENT '是否延期：0-否，1-是',
  `is_interrupted` tinyint(1) DEFAULT '0' COMMENT '是否中断：0-否，1-是',
  `recall_status` int DEFAULT '0' COMMENT '撤回状态：0-未撤回，1-已撤回（待确认）,2-已确认撤回',
  `recall_reason` text COMMENT '撤回原因',
  `recall_apply_time` datetime DEFAULT NULL COMMENT '撤回申请时间',
  `recall_audit_time` datetime DEFAULT NULL COMMENT '撤回审核时间',
  `recall_reviewer_id` bigint DEFAULT NULL COMMENT '撤回审核人 ID',
  `recall_audit_remark` text COMMENT '撤回审核备注',
  `company_confirm_status` int DEFAULT '0' COMMENT '企业确认状态',
  `internship_start_time` datetime DEFAULT NULL COMMENT '实习开始时间',
  `internship_end_time` datetime DEFAULT NULL COMMENT '实习结束时间',
  `internship_duration` int DEFAULT '0' COMMENT '实习时长（天）',
  `feedback` text COMMENT '企业反馈',
  `remark` text COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_position_id` (`position_id`),
  KEY `idx_status` (`status`),
  KEY `idx_recall_status` (`recall_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习状态表';

-- 11. position - 岗位信息表
CREATE TABLE IF NOT EXISTS `position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `company_id` bigint NOT NULL COMMENT '企业 ID',
  `category_id` bigint DEFAULT NULL COMMENT '岗位类别 ID',
  `position_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `department` varchar(100) DEFAULT NULL COMMENT '部门',
  `position_type` varchar(50) DEFAULT NULL COMMENT '岗位类型',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `salary_min` int DEFAULT '0' COMMENT '最低薪资',
  `salary_max` int DEFAULT '0' COMMENT '最高薪资',
  `description` text COMMENT '岗位描述',
  `requirements` text COMMENT '岗位要求',
  `planned_recruit` int DEFAULT '0' COMMENT '计划招聘人数',
  `recruited_count` int DEFAULT '0' COMMENT '已招人数',
  `remaining_quota` int DEFAULT '0' COMMENT '剩余缺口',
  `status` varchar(20) DEFAULT 'active' COMMENT '状态：active-主动，paused-暂停',
  `publish_date` datetime DEFAULT NULL COMMENT '发布日期',
  `internship_start_date` date DEFAULT NULL COMMENT '实习开始日期',
  `internship_end_date` date DEFAULT NULL COMMENT '实习结束日期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_publish_date` (`publish_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位信息表';

-- 12. announcement - 公告表
CREATE TABLE IF NOT EXISTS `announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `title` varchar(255) NOT NULL COMMENT '公告标题',
  `content` text NOT NULL COMMENT '公告内容',
  `publisher` varchar(50) NOT NULL COMMENT '发布人',
  `publisher_role` varchar(50) DEFAULT NULL COMMENT '发布人身份：ADMIN-管理员，COLLEGE-学院教师，DEPARTMENT-系室教师，COUNSELOR-辅导员',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `valid_from` datetime DEFAULT NULL COMMENT '有效期开始',
  `valid_to` datetime DEFAULT NULL COMMENT '有效期结束',
  `target_type` varchar(500) DEFAULT NULL COMMENT '目标类型：JSON 数组，如 ["STUDENT","TEACHER"]',
  `target_value` text COMMENT '目标值：JSON 对象，如 {"teacherTypes":["COLLEGE","DEPARTMENT"],"majorIds":["1","2"]}',
  `priority` varchar(20) DEFAULT 'normal' COMMENT '优先级：normal-普通，important-重要',
  `attachments` text COMMENT '附件列表（JSON 格式）',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '公告状态：DRAFT-草稿，PUBLISHED-已发布，EXPIRED-已过期',
  `read_count` int DEFAULT '0' COMMENT '阅读次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_publisher_role` (`publisher_role`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- ========================================
-- SQL 脚本结束
-- ========================================
