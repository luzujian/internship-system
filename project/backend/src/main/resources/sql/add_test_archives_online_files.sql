-- 使用在线测试文件为学生添加简历和证书
-- 版本：v3.0 (使用真实可访问的在线文件)
-- 说明：这个脚本使用公开的在线测试文件，可以立即预览和下载

SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;

-- 开始事务
START TRANSACTION;

-- 首先查看有哪些学生申请了岗位
SELECT '========== 查看申请岗位的学生 ==========' as info;
SELECT 
    ia.id AS application_id,
    ia.student_id,
    su.name AS student_name,
    su.student_user_id,
    p.position_name
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
ORDER BY ia.apply_time DESC
LIMIT 5;

-- 为学生ID为1的学生添加个人简历（使用在线PDF）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_个人简历.pdf', '简历', 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf', NOW(), NOW(), NOW());

-- 为学生ID为1的学生添加获奖证书（使用在线图片）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_英语六级证书.jpg', '证书', 'https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800', NOW(), NOW(), NOW()),
(1, '张三_计算机二级证书.jpg', '证书', 'https://images.unsplash.com/photo-1557683316-973673baf926?w=800', NOW(), NOW(), NOW());

-- 为学生ID为2的学生添加个人简历（使用在线PDF）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(2, '李四_个人简历.pdf', '简历', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), NOW(), NOW());

-- 为学生ID为2的学生添加获奖证书（使用在线图片）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(2, '李四_奖学金证书.jpg', '证书', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800', NOW(), NOW(), NOW()),
(2, '李四_优秀学生干部证书.jpg', '证书', 'https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=800', NOW(), NOW(), NOW());

-- 为学生ID为3的学生添加个人简历（使用在线PDF）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(3, '王五_个人简历.pdf', '简历', 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf', NOW(), NOW(), NOW());

-- 为学生ID为3的学生添加获奖证书（使用在线图片）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(3, '王五_数学建模竞赛证书.jpg', '证书', 'https://images.unsplash.com/photo-1557682257-93bd80d0f78d?w=800', NOW(), NOW(), NOW());

-- 提交事务
COMMIT;

-- 验证插入的数据
SELECT '========== 验证插入的测试数据 ==========' as info;
SELECT 
    sa.id,
    sa.student_id,
    su.name AS student_name,
    sa.file_name,
    sa.file_type,
    sa.file_url,
    sa.upload_time
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
ORDER BY sa.upload_time DESC;

-- 统计数据
SELECT '========== 数据统计 ==========' as info;
SELECT 
    COUNT(*) AS total_archives,
    COUNT(DISTINCT student_id) AS students_with_files,
    SUM(CASE WHEN file_type = '简历' THEN 1 ELSE 0 END) AS total_resumes,
    SUM(CASE WHEN file_type = '证书' THEN 1 ELSE 0 END) AS total_certificates
FROM student_archives;

SELECT '========== 测试数据插入完成 ==========' as info;
SELECT '已为3个学生添加了个人简历和获奖证书的测试数据' AS message;
SELECT '这些是真实的在线文件，可以立即预览和下载！' AS success;

SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
