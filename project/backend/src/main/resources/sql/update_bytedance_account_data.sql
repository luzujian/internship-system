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
