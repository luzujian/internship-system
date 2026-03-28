-- ========================================
-- 实习评价类别评分详情表
-- 用于存储每个学生在各个评分类别的得分
-- ========================================

CREATE TABLE IF NOT EXISTS `internship_evaluation_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `evaluation_id` bigint NOT NULL COMMENT '实习评价 ID',
  `student_id` bigint NOT NULL COMMENT '学生 ID',
  `category_code` varchar(100) NOT NULL COMMENT '类别代码',
  `category_name` varchar(255) NOT NULL COMMENT '类别名称',
  `score` int NOT NULL COMMENT '类别得分',
  `weight` int NOT NULL COMMENT '类别权重',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_id` (`evaluation_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_category_code` (`category_code`),
  UNIQUE KEY `uk_evaluation_category` (`evaluation_id`, `category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习评价类别评分详情表';
