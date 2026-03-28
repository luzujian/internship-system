-- 为公司用户表添加基础信息字段
-- 执行前请先备份数据库：mysqldump -u root -p internship > backup_company_$(date +%Y%m%d_%H%M%S).sql

-- 使用存储过程来检查并列是否已存在，避免重复添加
DROP PROCEDURE IF EXISTS add_company_info_columns;

DELIMITER $$

CREATE PROCEDURE add_company_info_columns()
BEGIN
    -- 检查并添加 industry 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'industry') THEN
        ALTER TABLE company_users ADD COLUMN industry VARCHAR(50) COMMENT '所属行业：internet/finance/education/medical/manufacturing/service/realestate/energy/other' AFTER status;
    END IF;

    -- 检查并添加 scale 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'scale') THEN
        ALTER TABLE company_users ADD COLUMN scale VARCHAR(20) COMMENT '企业规模：1-50/51-100/101-500/501-1000/1000+' AFTER industry;
    END IF;

    -- 检查并添加 province 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'province') THEN
        ALTER TABLE company_users ADD COLUMN province VARCHAR(50) COMMENT '省份' AFTER scale;
    END IF;

    -- 检查并添加 city 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'city') THEN
        ALTER TABLE company_users ADD COLUMN city VARCHAR(50) COMMENT '城市' AFTER province;
    END IF;

    -- 检查并添加 district 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'district') THEN
        ALTER TABLE company_users ADD COLUMN district VARCHAR(50) COMMENT '区县' AFTER city;
    END IF;

    -- 检查并添加 detail_address 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'detail_address') THEN
        ALTER TABLE company_users ADD COLUMN detail_address VARCHAR(255) COMMENT '详细地址' AFTER district;
    END IF;

    -- 检查并添加 website 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'website') THEN
        ALTER TABLE company_users ADD COLUMN website VARCHAR(255) COMMENT '企业网站' AFTER detail_address;
    END IF;

    -- 检查并添加 cooperation_mode 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'cooperation_mode') THEN
        ALTER TABLE company_users ADD COLUMN cooperation_mode VARCHAR(50) COMMENT '合作模式：accept_fallback/student_contact/mutual_choice' AFTER website;
    END IF;

    -- 检查并添加 logo 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'logo') THEN
        ALTER TABLE company_users ADD COLUMN logo VARCHAR(500) COMMENT '企业 Logo URL' AFTER cooperation_mode;
    END IF;

    -- 检查并添加 photos 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'photos') THEN
        ALTER TABLE company_users ADD COLUMN photos TEXT COMMENT '企业照片 URL 数组（JSON 格式）' AFTER logo;
    END IF;

    -- 检查并添加 videos 字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'videos') THEN
        ALTER TABLE company_users ADD COLUMN videos TEXT COMMENT '企业视频 URL 数组（JSON 格式）' AFTER photos;
    END IF;

END$$

DELIMITER ;

-- 执行存储过程
CALL add_company_info_columns();

-- 删除存储过程
DROP PROCEDURE IF EXISTS add_company_info_columns;

-- 查看添加后的表结构
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'internship'
AND TABLE_NAME = 'company_users'
ORDER BY ORDINAL_POSITION;
