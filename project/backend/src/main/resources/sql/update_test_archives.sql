-- 清空student_archives表中的测试数据
DELETE FROM student_archives WHERE student_id IN (7, 8);

-- 插入新的测试数据（使用可访问的文件URL）
-- 赵强（student_id = 7）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(7, '赵强_个人简历.pdf', '简历', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), '个人简历', 1),
(7, '赵强_英语六级证书.jpg', '证书', 'https://via.placeholder.com/800x600/4CAF50/FFFFFF?text=英语六级证书', NOW(), '英语六级证书', 1),
(7, '赵强_计算机二级证书.jpg', '证书', 'https://via.placeholder.com/800x600/2196F3/FFFFFF?text=计算机二级证书', NOW(), '计算机二级证书', 1);

-- 学生（student_id = 8）
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(8, '学生_个人简历.pdf', '简历', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), '个人简历', 1),
(8, '学生_英语六级证书.jpg', '证书', 'https://via.placeholder.com/800x600/FF9800/FFFFFF?text=英语六级证书', NOW(), '英语六级证书', 1),
(8, '学生_计算机二级证书.jpg', '证书', 'https://via.placeholder.com/800x600/9C27B0/FFFFFF?text=计算机二级证书', NOW(), '计算机二级证书', 1);

-- 查看插入的数据
SELECT id, student_id, file_name, file_type, file_url FROM student_archives WHERE student_id IN (7, 8) ORDER BY id;
