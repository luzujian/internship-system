-- 创建岗位类别表
CREATE TABLE IF NOT EXISTS `position_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '类别ID',
  `name` VARCHAR(50) NOT NULL COMMENT '类别名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '类别描述',
  `position_count` INT DEFAULT 0 COMMENT '岗位数量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位类别表';

-- 为position表添加category_id字段
ALTER TABLE `position` ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '岗位类别ID' AFTER `company_id`;

-- 添加外键约束
ALTER TABLE `position` ADD CONSTRAINT `fk_position_category` FOREIGN KEY (`category_id`) REFERENCES `position_category` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- 创建索引
CREATE INDEX `idx_position_category_id` ON `position`(`category_id`);

-- 插入初始岗位类别数据
INSERT INTO `position_category` (`name`, `description`, `position_count`) VALUES
('技术研发类', '包括软件开发、测试、算法、数据分析等技术相关岗位', 0),
('产品与设计类', '包括产品经理、UI/UX设计师、交互设计师等岗位', 0),
('市场营销类', '包括市场专员、销售代表、品牌策划等岗位', 0),
('运营管理类', '包括运营专员、项目经理、人力资源等岗位', 0),
('财务金融类', '包括会计、财务分析、风险控制等岗位', 0),
('供应链与物流类', '包括采购、供应链管理、物流专员等岗位', 0),
('生产制造类', '包括生产技术、质量检验、工艺工程师等岗位', 0),
('客户服务类', '包括客服专员、技术支持、售后服务等岗位', 0),
('法律与合规类', '包括法务专员、合规专员、知识产权专员等岗位', 0),
('其他类', '其他未分类岗位', 0);
