-- 检查字节跳动科技有限公司的ID
SELECT id, company_name, username, phone, email FROM company_users WHERE company_name = '字节跳动科技有限公司';

-- 检查email字段是否存在
SHOW COLUMNS FROM company_users LIKE 'email';
