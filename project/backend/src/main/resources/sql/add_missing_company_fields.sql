-- 添加缺失的字段到 company_users 表
-- 使用存储过程安全地添加字段，避免重复添加错误

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    -- 添加 plaque_photo 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'plaque_photo'
    ) THEN
        ALTER TABLE company_users ADD COLUMN plaque_photo VARCHAR(500) COMMENT '牌匾照片' AFTER legal_id_card;
    END IF;
    
    -- 添加 has_received_interns 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'has_received_interns'
    ) THEN
        ALTER TABLE company_users ADD COLUMN has_received_interns INT DEFAULT 0 COMMENT '是否接收过实习生' AFTER is_internship_base;
    END IF;
    
    -- 添加 current_employees_count 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'current_employees_count'
    ) THEN
        ALTER TABLE company_users ADD COLUMN current_employees_count INT DEFAULT 0 COMMENT '当前员工数量' AFTER has_received_interns;
    END IF;
    
    -- 添加 accept_backup 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'accept_backup'
    ) THEN
        ALTER TABLE company_users ADD COLUMN accept_backup INT DEFAULT 0 COMMENT '是否接受兜底' AFTER current_employees_count;
    END IF;
    
    -- 添加 max_backup_students 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'max_backup_students'
    ) THEN
        ALTER TABLE company_users ADD COLUMN max_backup_students INT DEFAULT 0 COMMENT '最大兜底学生数' AFTER accept_backup;
    END IF;
    
    -- 添加 company_tag 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'company_tag'
    ) THEN
        ALTER TABLE company_users ADD COLUMN company_tag VARCHAR(255) COMMENT '企业标签' AFTER max_backup_students;
    END IF;
    
    -- 添加 register_time 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'register_time'
    ) THEN
        ALTER TABLE company_users ADD COLUMN register_time DATETIME COMMENT '注册时间' AFTER company_tag;
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL add_column_if_not_exists();

-- 删除存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
