-- 检查company_users表中的logo、photos、videos字段数据
-- 执行时间：2026-02-22

USE internship;

-- 检查所有企业的logo、photos、videos字段
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

-- 统计有多少企业有logo、photos、videos
SELECT 
    COUNT(*) AS total_companies,
    SUM(CASE WHEN logo IS NOT NULL AND logo != '' THEN 1 ELSE 0 END) AS companies_with_logo,
    SUM(CASE WHEN photos IS NOT NULL AND photos != '' THEN 1 ELSE 0 END) AS companies_with_photos,
    SUM(CASE WHEN videos IS NOT NULL AND videos != '' THEN 1 ELSE 0 END) AS companies_with_videos
FROM company_users;

-- 查看ID为3的企业（字节跳动）的详细数据
SELECT 
    id,
    company_name,
    logo,
    photos,
    videos,
    introduction
FROM company_users
WHERE id = 3;
