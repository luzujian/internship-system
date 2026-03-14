-- 检查 company_users 表中的所有企业用户
SELECT 
    id,
    username,
    company_id,
    contact_person,
    phone,
    status
FROM company_users
ORDER BY id;
