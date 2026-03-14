-- 检查并修复company_users表结构和数据

-- 1. 检查当前表结构
SHOW COLUMNS FROM company_users;

-- 2. 检查字节跳动科技有限公司的数据
SELECT id, company_name, username, phone, email, industry, scale 
FROM company_users 
WHERE company_name = '字节跳动科技有限公司';

-- 3. 如果email字段不存在，添加它
-- 注意：这个语句会检查字段是否存在，如果不存在才添加
ALTER TABLE company_users 
ADD COLUMN IF NOT EXISTS email VARCHAR(100) COMMENT '账号邮箱' AFTER phone;

-- 4. 验证email字段已添加
SHOW COLUMNS FROM company_users LIKE 'email';

-- 5. 更新字节跳动科技有限公司的数据
UPDATE company_users 
SET 
    username = 'bytedance',
    phone = '13800138002',
    email = 'admin@bytedance.com',
    industry = '互联网/IT',
    scale = '10000人以上'
WHERE company_name = '字节跳动科技有限公司';

-- 6. 再次验证数据
SELECT id, company_name, username, phone, email, industry, scale 
FROM company_users 
WHERE company_name = '字节跳动科技有限公司';

-- 7. 检查所有企业的ID和名称
SELECT id, company_name, username, status 
FROM company_users 
ORDER BY id;
