-- 检查ID为3的企业（字节跳动）的photos和videos字段
USE internship;

SELECT 
    id,
    company_name,
    logo,
    photos,
    videos,
    LENGTH(photos) AS photos_length,
    LENGTH(videos) AS videos_length,
    introduction
FROM company_users
WHERE id = 3;
