-- 为company_users表添加实习相关字段

-- 添加是否是挂牌实习基地字段（0-否，1-国家级，2-省级）
ALTER TABLE company_users ADD COLUMN is_internship_base INT DEFAULT 0 COMMENT '是否是挂牌实习基地：0-否，1-国家级，2-省级' AFTER legal_id_card;

-- 添加牌匾照片路径字段
ALTER TABLE company_users ADD COLUMN plaque_photo VARCHAR(500) COMMENT '挂牌实习基地牌匾照片路径' AFTER is_internship_base;

-- 添加是否曾经接收过我们学院的实习生字段
ALTER TABLE company_users ADD COLUMN has_received_interns INT DEFAULT 0 COMMENT '是否曾经接收过我们学院的实习生：0-否，1-是' AFTER plaque_photo;

-- 添加目前招收我院毕业生的在职员工数量字段
ALTER TABLE company_users ADD COLUMN current_employees_count INT DEFAULT 0 COMMENT '目前招收我院毕业生的在职员工数量' AFTER has_received_interns;

-- 添加是否接受兜底字段
ALTER TABLE company_users ADD COLUMN accept_backup INT DEFAULT 0 COMMENT '是否接受兜底：0-否，1-是' AFTER current_employees_count;

-- 添加能接受兜底的最多学生数量字段
ALTER TABLE company_users ADD COLUMN max_backup_students INT DEFAULT 0 COMMENT '能接受兜底的最多学生数量' AFTER accept_backup;

-- 添加企业标签字段（系统自动标识：双向选择/学生自主找的/接受兜底）
ALTER TABLE company_users ADD COLUMN company_tag VARCHAR(50) COMMENT '企业标签：双向选择/学生自主找的/接受兜底' AFTER max_backup_students;

-- 添加注册时间字段（用于时间线判断）
ALTER TABLE company_users ADD COLUMN register_time DATETIME COMMENT '注册时间（用于时间线判断）' AFTER company_tag;
