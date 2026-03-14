-- 修复企业LOGO无法显示的问题
-- 问题：数据库中存储的logo文件路径对应的文件不存在
-- 解决方案：将不存在的logo路径清空，让用户重新上传

USE internship;

-- 查看当前所有企业的logo、photos、videos数据
SELECT 
    id,
    company_name,
    logo,
    photos,
    videos
FROM company_users
WHERE logo IS NOT NULL AND logo != ''
ORDER BY id;

-- 检查ID为3的企业（字节跳动）的logo路径
SELECT 
    id,
    company_name,
    logo,
    photos,
    videos
FROM company_users
WHERE id = 3;

-- 清空不存在的logo路径（根据实际情况执行）
-- 如果确认文件不存在，可以执行以下语句清空logo字段
UPDATE company_users 
SET logo = NULL 
WHERE logo LIKE '%/api/uploads/2026/02/22/%';

-- 验证更新结果
SELECT 
    id,
    company_name,
    logo,
    photos,
    videos,
    CASE 
        WHEN logo IS NULL OR logo = '' THEN '无'
        ELSE '有'
    END AS has_logo,
    CASE 
        WHEN photos IS NULL OR photos = '' THEN '无'
        ELSE '有'
    END AS has_photos,
    CASE 
        WHEN videos IS NULL OR videos = '' THEN '无'
        ELSE '有'
    END AS has_videos
FROM company_users
ORDER BY id;
