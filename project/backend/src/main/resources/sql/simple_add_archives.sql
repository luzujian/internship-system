-- ========================================
-- 添加学生简历和证书测试数据
-- ========================================
-- 使用在线测试文件，可以立即预览和下载
-- ========================================

SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;

START TRANSACTION;

-- 为学生ID为1的学生添加个人简历
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_个人简历.pdf', '简历', 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf', NOW(), NOW(), NOW());

-- 为学生ID为1的学生添加获奖证书
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_英语六级证书.jpg', '证书', 'https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800', NOW(), NOW(), NOW()),
(1, '张三_计算机二级证书.jpg', '证书', 'https://images.unsplash.com/photo-1557683316-973673baf926?w=800', NOW(), NOW(), NOW());

-- 为学生ID为2的学生添加个人简历
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(2, '李四_个人简历.pdf', '简历', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), NOW(), NOW());

-- 为学生ID为2的学生添加获奖证书
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(2, '李四_奖学金证书.jpg', '证书', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800', NOW(), NOW(), NOW()),
(2, '李四_优秀学生干部证书.jpg', '证书', 'https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=800', NOW(), NOW(), NOW());

-- 为学生ID为3的学生添加个人简历
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(3, '王五_个人简历.pdf', '简历', 'https://file-examples.com/storage/feebd4599c666d9a9e6b9c/1/file-sample_150kB.pdf', NOW(), NOW(), NOW());

-- 为学生ID为3的学生添加获奖证书
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(3, '王五_数学建模竞赛证书.jpg', '证书', 'https://images.unsplash.com/photo-1557682257-93bd80d0f78d?w=800', NOW(), NOW(), NOW());

COMMIT;

-- 验证插入的数据
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

SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;

-- ========================================
-- 执行完成！
-- ========================================
