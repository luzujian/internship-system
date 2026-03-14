-- 为学生添加测试的个人简历和获奖证书数据
-- 注意：这些是测试数据，实际文件URL需要替换为真实的OSS文件地址

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

-- 为前几个学生添加个人简历
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_个人简历.pdf', '简历', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/resumes/zhangsan_resume.pdf', NOW(), NOW(), NOW()),
(2, '李四_个人简历.pdf', '简历', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/resumes/lisi_resume.pdf', NOW(), NOW(), NOW()),
(3, '王五_个人简历.pdf', '简历', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/resumes/wangwu_resume.pdf', NOW(), NOW(), NOW());

-- 为前几个学生添加获奖证书
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time) VALUES
(1, '张三_英语六级证书.jpg', '证书', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/certificates/zhangsan_cet6.jpg', NOW(), NOW(), NOW()),
(1, '张三_计算机二级证书.jpg', '证书', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/certificates/zhangsan_ncre2.jpg', NOW(), NOW(), NOW()),
(2, '李四_奖学金证书.jpg', '证书', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/certificates/lisi_scholarship.jpg', NOW(), NOW(), NOW()),
(2, '李四_优秀学生干部证书.jpg', '证书', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/certificates/lisi_excellent_student.jpg', NOW(), NOW(), NOW()),
(3, '王五_数学建模竞赛证书.jpg', '证书', 'https://example-bucket.oss-cn-hangzhou.aliyuncs.com/certificates/wangwu_math_modeling.jpg', NOW(), NOW(), NOW());

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

SELECT '========== 测试数据插入完成 ==========' as info;
SELECT '已为3个学生添加了个人简历和获奖证书的测试数据' AS message;
SELECT '注意：这些是示例URL，实际使用时需要替换为真实的OSS文件地址' AS warning;
