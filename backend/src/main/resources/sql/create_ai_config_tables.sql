-- 创建关键词库表
CREATE TABLE IF NOT EXISTS keyword_library (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(100) DEFAULT NULL COMMENT '创建人',
    updater VARCHAR(100) DEFAULT NULL COMMENT '更新人',
    deleted INT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    keyword VARCHAR(255) NOT NULL COMMENT '关键词',
    category VARCHAR(100) DEFAULT NULL COMMENT '分类',
    description TEXT DEFAULT NULL COMMENT '描述',
    weight INT DEFAULT 0 COMMENT '权重',
    status INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    usage_type VARCHAR(50) DEFAULT 'general' COMMENT '使用类型：general-通用，internship-实习相关，academic-学术相关',
    related_tags VARCHAR(500) DEFAULT NULL COMMENT '相关标签'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键词库表';

-- 创建评分规则表
CREATE TABLE IF NOT EXISTS scoring_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(100) DEFAULT NULL COMMENT '创建人',
    updater VARCHAR(100) DEFAULT NULL COMMENT '更新人',
    deleted INT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    rule_name VARCHAR(255) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(100) DEFAULT NULL COMMENT '规则类型',
    category VARCHAR(100) DEFAULT NULL COMMENT '分类',
    min_score INT DEFAULT 0 COMMENT '最低分',
    max_score INT DEFAULT 100 COMMENT '最高分',
    description TEXT DEFAULT NULL COMMENT '描述',
    evaluation_criteria TEXT DEFAULT NULL COMMENT '评估标准',
    weight INT DEFAULT 1 COMMENT '权重',
    status INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    applicable_scenarios VARCHAR(500) DEFAULT NULL COMMENT '适用场景'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分规则表';

-- 创建索引
CREATE INDEX idx_keyword_category ON keyword_library(category);
CREATE INDEX idx_keyword_status ON keyword_library(status);
CREATE INDEX idx_keyword_usage_type ON keyword_library(usage_type);
CREATE INDEX idx_scoring_rule_category ON scoring_rule(category);
CREATE INDEX idx_scoring_rule_status ON scoring_rule(status);
CREATE INDEX idx_scoring_rule_type ON scoring_rule(rule_type);
