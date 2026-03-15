-- 验证公司基础信息字段修复
-- 执行此 SQL 来验证数据库字段是否正常工作

-- 设置 UTF-8 编码
SET NAMES utf8mb4;

-- 更新测试数据（企业 ID=3）
UPDATE company_users SET
    industry = 'internet',
    scale = '1000+',
    province = '广东省',
    city = '深圳市',
    district = '南山区',
    detail_address = '科技园南区 123 号',
    website = 'https://example.com',
    cooperation_mode = 'mutual_choice',
    logo = 'https://example.com/logo.png',
    photos = '["https://example.com/photo1.jpg","https://example.com/photo2.jpg"]',
    videos = '[]',
    introduction = '这是一家知名的互联网公司，专注于人工智能和云计算领域。'
WHERE id = 3;

-- 验证更新结果
SELECT
    id,
    company_name AS 企业名称，
    industry AS 行业，
    scale AS 规模，
    province AS 省份，
    city AS 城市，
    district AS 区县，
    detail_address AS 详细地址，
    website AS 网站，
    cooperation_mode AS 合作模式，
    logo AS Logo,
    photos AS 照片，
    videos AS 视频，
    introduction AS 企业简介
FROM company_users
WHERE id = 3;

-- 检查字段映射是否正确
SHOW COLUMNS FROM company_users LIKE 'industry';
SHOW COLUMNS FROM company_users LIKE 'scale';
SHOW COLUMNS FROM company_users LIKE 'province';
SHOW COLUMNS FROM company_users LIKE 'city';
SHOW COLUMNS FROM company_users LIKE 'district';
SHOW COLUMNS FROM company_users LIKE 'detail_address';
SHOW COLUMNS FROM company_users LIKE 'website';
SHOW COLUMNS FROM company_users LIKE 'cooperation_mode';
SHOW COLUMNS FROM company_users LIKE 'logo';
SHOW COLUMNS FROM company_users LIKE 'photos';
SHOW COLUMNS FROM company_users LIKE 'videos';
