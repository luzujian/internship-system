-- 安全添加email字段到company_users表
-- 这个脚本可以安全地重复执行

-- 检查并添加email字段
SET @dbname = DATABASE();
SET @tablename = 'company_users';
SET @columnname = 'email';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_schema = @dbname)
      AND (table_name = @tablename)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(100) COMMENT ''账号邮箱'' AFTER phone')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 验证字段是否添加成功
SELECT 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    IS_NULLABLE, 
    COLUMN_DEFAULT, 
    COLUMN_COMMENT 
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
AND table_name = 'company_users' 
AND column_name = 'email';

-- 更新字节跳动科技有限公司的账号设置数据
UPDATE company_users 
SET 
    username = 'bytedance',
    phone = '13800138002',
    email = 'admin@bytedance.com',
    industry = '互联网/IT',
    scale = '10000人以上'
WHERE company_name = '字节跳动科技有限公司';

-- 验证更新结果
SELECT 
    id,
    company_name,
    username,
    phone,
    email,
    industry,
    scale
FROM company_users 
WHERE company_name = '字节跳动科技有限公司';
