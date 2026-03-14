-- 为 company_users 表添加 email 字段（如果不存在）
-- 执行前请先备份数据库：mysqldump -u root -p internship > backup_email_$(date +%Y%m%d_%H%M%S).sql

DROP PROCEDURE IF EXISTS add_email_column;

DELIMITER $$

CREATE PROCEDURE add_email_column()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'internship'
                   AND TABLE_NAME = 'company_users'
                   AND COLUMN_NAME = 'email') THEN
        ALTER TABLE company_users ADD COLUMN email VARCHAR(100) COMMENT '企业邮箱' AFTER phone;
    END IF;
END$$

DELIMITER ;

CALL add_email_column();

DROP PROCEDURE IF EXISTS add_email_column;

-- 查看添加后的列信息
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'internship'
AND TABLE_NAME = 'company_users'
AND COLUMN_NAME IN ('phone', 'email', 'username')
ORDER BY ORDINAL_POSITION;
