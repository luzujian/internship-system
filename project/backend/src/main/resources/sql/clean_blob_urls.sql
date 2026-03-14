-- 清理企业信息中的 blob URL 数据
UPDATE company_users 
SET logo = NULL 
WHERE logo LIKE 'blob:%';

UPDATE company_users 
SET photos = NULL 
WHERE photos LIKE '%blob:%';

UPDATE company_users 
SET videos = NULL 
WHERE videos LIKE '%blob:%';

-- 查看清理结果
SELECT id, company_name, logo, photos, videos 
FROM company_users 
WHERE logo IS NOT NULL OR photos IS NOT NULL OR videos IS NOT NULL;
