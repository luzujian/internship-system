-- 添加账号设置所需的email字段到company_users表
-- 使用存储过程安全地添加字段，避免重复添加错误

DELIMITER $$

DROP PROCEDURE IF EXISTS add_company_account_fields$$

CREATE PROCEDURE add_company_account_fields()
BEGIN
    -- 添加 email 字段（用于账号设置，与contact_email区分）
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'internship' 
        AND table_name = 'company_users' 
        AND column_name = 'email'
    ) THEN
        ALTER TABLE company_users ADD COLUMN email VARCHAR(100) COMMENT '账号邮箱' AFTER phone;
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL add_company_account_fields();

-- 删除存储过程
DROP PROCEDURE IF EXISTS add_company_account_fields;

-- 验证字段是否添加成功
SELECT 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    IS_NULLABLE, 
    COLUMN_DEFAULT, 
    COLUMN_COMMENT 
FROM information_schema.columns 
WHERE table_schema = 'internship' 
AND table_name = 'company_users' 
AND column_name IN ('username', 'phone', 'email');
