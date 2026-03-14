-- 查找数据库中已有的文件URL
-- 这个脚本会帮你找到真实的OSS文件URL

-- 查看所有包含文件URL的表
SELECT '========== 1. 查看company_users表中的文件 ==========' as info;
SELECT 
    id,
    company_name,
    logo_url,
    photos_url,
    videos_url
FROM company_users
WHERE logo_url IS NOT NULL 
   OR photos_url IS NOT NULL 
   OR videos_url IS NOT NULL
LIMIT 5;

SELECT '========== 2. 查看student_archives表中的文件 ==========' as info;
SELECT 
    id,
    student_id,
    file_name,
    file_type,
    file_url,
    upload_time
FROM student_archives
WHERE file_url IS NOT NULL
ORDER BY upload_time DESC
LIMIT 10;

SELECT '========== 3. 查看resource_documents表中的文件 ==========' as info;
SELECT 
    id,
    document_name,
    file_url,
    upload_time
FROM resource_documents
WHERE file_url IS NOT NULL
ORDER BY upload_time DESC
LIMIT 10;

SELECT '========== 4. 查找所有包含oss的URL ==========' as info;
SELECT 
    'company_users' as table_name,
    'logo_url' as column_name,
    logo_url as file_url,
    company_name as related_info
FROM company_users
WHERE logo_url LIKE '%oss%' OR logo_url LIKE '%aliyun%'
UNION ALL
SELECT 
    'company_users' as table_name,
    'photos_url' as column_name,
    photos_url as file_url,
    company_name as related_info
FROM company_users
WHERE photos_url LIKE '%oss%' OR photos_url LIKE '%aliyun%'
UNION ALL
SELECT 
    'company_users' as table_name,
    'videos_url' as column_name,
    videos_url as file_url,
    company_name as related_info
FROM company_users
WHERE videos_url LIKE '%oss%' OR videos_url LIKE '%aliyun%'
UNION ALL
SELECT 
    'student_archives' as table_name,
    'file_url' as column_name,
    file_url,
    file_name as related_info
FROM student_archives
WHERE file_url LIKE '%oss%' OR file_url LIKE '%aliyun%'
LIMIT 20;
