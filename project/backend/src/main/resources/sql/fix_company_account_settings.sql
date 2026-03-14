-- 修复company_users表，添加email字段并更新字节跳动数据

-- 1. 检查email字段是否存在
SELECT COUNT(*) as field_exists 
FROM information_schema.columns 
WHERE table_schema = 'internship' 
AND table_name = 'company_users' 
AND column_name = 'email';

-- 2. 如果不存在，添加email字段
ALTER TABLE company_users 
ADD COLUMN email VARCHAR(100) COMMENT '账号邮箱' AFTER phone;

-- 3. 验证email字段已添加
SHOW COLUMNS FROM company_users LIKE 'email';

-- 4. 更新字节跳动科技有限公司的数据
UPDATE company_users 
SET 
    username = 'bytedance',
    phone = '13800138002',
    email = 'admin@bytedance.com',
    industry = '互联网/IT',
    scale = '10000人以上'
WHERE company_name = '字节跳动科技有限公司';

-- 5. 验证更新结果
SELECT id, company_name, username, phone, email, industry, scale 
FROM company_users 
WHERE company_name = '字节跳动科技有限公司';
