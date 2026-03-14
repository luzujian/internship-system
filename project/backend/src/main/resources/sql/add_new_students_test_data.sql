-- 增加更多学生用户测试数据
-- 数据库: internship
-- 创建时间: 2026-02-13
-- 目标: 为实习确认页面和岗位申请查看页面增加更多学生用户

-- ========================================
-- 第一部分：新增学生用户数据（30条）
-- ID范围: 80-109
-- ========================================

INSERT IGNORE INTO student_users 
(id, username, password, name, gender, student_user_id, major_id, grade, class_id, role, status, create_time, update_time, last_login_time)
VALUES 
(80, '20220001', '123456', '张明', 1, '20220001', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(81, '20220002', '123456', '李华', 2, '20220002', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(82, '20220003', '123456', '王强', 1, '20220003', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(83, '20220004', '123456', '赵敏', 2, '20220004', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(84, '20220005', '123456', '孙伟', 1, '20220005', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(85, '20220006', '123456', '周芳', 2, '20220006', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(86, '20220007', '123456', '吴刚', 1, '20220007', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(87, '20220008', '123456', '郑丽', 2, '20220008', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(88, '20220009', '123456', '陈杰', 1, '20220009', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(89, '20220010', '123456', '林静', 2, '20220010', 1, 2022, 79, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(90, '20220011', '123456', '黄磊', 1, '20220011', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(91, '20220012', '123456', '杨洋', 2, '20220012', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(92, '20220013', '123456', '朱涛', 1, '20220013', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(93, '20220014', '123456', '徐婷', 2, '20220014', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(94, '20220015', '123456', '马超', 1, '20220015', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(95, '20220016', '123456', '胡娜', 2, '20220016', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(96, '20220017', '123456', '郭鹏', 1, '20220017', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(97, '20220018', '123456', '何燕', 2, '20220018', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(98, '20220019', '123456', '高飞', 1, '20220019', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(99, '20220020', '123456', '罗兰', 2, '20220020', 2, 2022, 80, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(100, '20220021', '123456', '梁勇', 1, '20220021', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(101, '20220022', '123456', '宋薇', 2, '20220022', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(102, '20220023', '123456', '唐军', 1, '20220023', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(103, '20220024', '123456', '韩雪', 2, '20220024', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(104, '20220025', '123456', '冯波', 1, '20220025', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(105, '20220026', '123456', '邓梅', 2, '20220026', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(106, '20220027', '123456', '曹亮', 1, '20220027', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(107, '20220028', '123456', '彭丽', 2, '20220028', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(108, '20220029', '123456', '曾强', 1, '20220029', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL),
(109, '20220030', '123456', '肖芳', 2, '20220030', 3, 2022, 81, 'ROLE_STUDENT', 1, NOW(), NOW(), NULL);

-- ========================================
-- 第二部分：为新学生创建实习确认数据（30条）
-- ID范围: 80-109
-- company_id=3, position_id使用4, 13, 14
-- ========================================

-- 待确认的实习确认申请（15条）
INSERT IGNORE INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(80, 3, 13, 2, 0, '2026-03-01', '2026-06-01', 92, '前端开发，熟悉Vue.js和React框架', NOW(), NOW()),
(81, 3, 14, 2, 0, '2026-03-05', '2026-06-05', 92, '后端开发，熟悉Spring Boot和MySQL', NOW(), NOW()),
(82, 3, 4, 2, 0, '2026-03-08', '2026-06-08', 92, '算法工程师，熟悉机器学习和深度学习', NOW(), NOW()),
(83, 3, 13, 2, 0, '2026-03-10', '2026-06-10', 92, '前端开发，熟悉TypeScript和小程序开发', NOW(), NOW()),
(84, 3, 13, 2, 0, '2026-03-12', '2026-06-12', 92, 'UI设计，熟悉Figma和Sketch设计工具', NOW(), NOW()),
(85, 3, 13, 2, 0, '2026-03-15', '2026-06-15', 92, '安全工程师，熟悉网络安全和渗透测试', NOW(), NOW()),
(86, 3, 13, 2, 0, '2026-03-18', '2026-06-18', 92, '测试工程师，熟悉自动化测试和性能测试', NOW(), NOW()),
(87, 3, 13, 2, 0, '2026-03-20', '2026-06-20', 92, '数据分析师，熟悉Python和SQL', NOW(), NOW()),
(88, 3, 13, 2, 0, '2026-03-22', '2026-06-22', 92, '前端开发，熟悉Vue3和Vite', NOW(), NOW()),
(89, 3, 14, 2, 0, '2026-03-25', '2026-06-25', 92, '后端开发，熟悉Java和微服务架构', NOW(), NOW()),
(90, 3, 4, 2, 0, '2026-03-28', '2026-06-28', 92, '算法工程师，熟悉NLP和推荐系统', NOW(), NOW()),
(91, 3, 13, 2, 0, '2026-04-01', '2026-07-01', 91, '前端开发，熟悉React Native和Flutter', NOW(), NOW()),
(92, 3, 13, 2, 0, '2026-04-05', '2026-07-05', 91, 'UI设计，熟悉移动端设计和交互设计', NOW(), NOW()),
(93, 3, 13, 2, 0, '2026-04-08', '2026-07-08', 91, '安全工程师，熟悉Web安全和APP安全', NOW(), NOW()),
(94, 3, 13, 2, 0, '2026-04-10', '2026-07-10', 91, '测试工程师，熟悉接口测试和UI测试', NOW(), NOW());

-- 已确认的实习确认申请（10条）
INSERT IGNORE INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(95, 3, 13, 2, 1, '2026-02-05', '2026-05-05', 90, '已确认的前端开发实习生，工作认真负责', NOW(), NOW()),
(96, 3, 14, 2, 1, '2026-02-08', '2026-05-08', 90, '已确认的后端开发实习生，技术能力强', NOW(), NOW()),
(97, 3, 4, 2, 1, '2026-02-10', '2026-05-10', 90, '已确认的算法工程师实习生，学习能力强', NOW(), NOW()),
(98, 3, 13, 2, 1, '2026-02-12', '2026-05-12', 90, '已确认的前端开发实习生，沟通能力好', NOW(), NOW()),
(99, 3, 13, 2, 1, '2026-02-15', '2026-05-15', 90, '已确认的UI设计实习生，设计水平高', NOW(), NOW()),
(100, 3, 13, 2, 1, '2026-02-18', '2026-05-18', 90, '已确认的安全工程师实习生，安全意识强', NOW(), NOW()),
(101, 3, 13, 2, 1, '2026-02-20', '2026-05-20', 90, '已确认的测试工程师实习生，细心负责', NOW(), NOW()),
(102, 3, 13, 2, 1, '2026-02-22', '2026-05-22', 90, '已确认的数据分析师实习生，数据分析能力强', NOW(), NOW()),
(103, 3, 13, 2, 1, '2026-02-25', '2026-05-25', 90, '已确认的前端开发实习生，代码质量高', NOW(), NOW()),
(104, 3, 14, 2, 1, '2026-02-28', '2026-05-28', 90, '已确认的后端开发实习生，架构设计能力强', NOW(), NOW());

-- 已拒绝的实习确认申请（5条）
INSERT IGNORE INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(105, 3, 13, 2, 2, '2026-02-25', '2026-05-25', 90, '已拒绝的前端开发申请，技能不符合要求', NOW(), NOW()),
(106, 3, 14, 2, 2, '2026-02-28', '2026-05-28', 90, '已拒绝的后端开发申请，经验不足', NOW(), NOW()),
(107, 3, 4, 2, 2, '2026-03-02', '2026-06-02', 92, '已拒绝的算法工程师申请，项目经验不够', NOW(), NOW()),
(108, 3, 13, 2, 2, '2026-03-05', '2026-06-05', 92, '已拒绝的前端开发申请，技术栈不匹配', NOW(), NOW()),
(109, 3, 13, 2, 2, '2026-03-08', '2026-06-08', 92, '已拒绝的UI设计申请，作品集不符合要求', NOW(), NOW());

-- ========================================
-- 第三部分：为新学生创建岗位申请数据（30条）
-- ID范围: 80-109
-- company_id=3, position_id使用4, 13, 14
-- ========================================

-- 未查看的岗位申请（15条）
INSERT IGNORE INTO internship_application 
(student_id, company_id, position_id, status, viewed, skills, experience, self_evaluation, apply_time, create_time, update_time)
VALUES 
(80, 3, 13, 'pending', 0, 'Vue.js, React, TypeScript', '1年前端开发经验', '学习能力强，善于沟通', '2026-02-13 10:00:00', NOW(), NOW()),
(81, 3, 14, 'pending', 0, 'Java, Spring Boot, MySQL', '1年后端开发经验', '技术扎实，责任心强', '2026-02-13 11:00:00', NOW(), NOW()),
(82, 3, 4, 'pending', 0, 'Python, TensorFlow, PyTorch', '1年机器学习经验', '算法基础好，逻辑思维强', '2026-02-13 12:00:00', NOW(), NOW()),
(83, 3, 13, 'pending', 0, 'React, Vue, Node.js', '1年前端开发经验', '全栈开发能力，团队协作好', '2026-02-13 13:00:00', NOW(), NOW()),
(84, 3, 13, 'pending', 0, 'Figma, Sketch, Adobe XD', '1年UI设计经验', '设计感强，创意丰富', '2026-02-13 14:00:00', NOW(), NOW()),
(85, 3, 13, 'pending', 0, '网络安全, 渗透测试', '1年安全测试经验', '安全意识强，细心负责', '2026-02-13 15:00:00', NOW(), NOW()),
(86, 3, 13, 'pending', 0, 'Selenium, JUnit, Postman', '1年测试经验', '测试用例设计能力强', '2026-02-13 16:00:00', NOW(), NOW()),
(87, 3, 13, 'pending', 0, 'Python, SQL, Pandas', '1年数据分析经验', '数据处理能力强，逻辑清晰', '2026-02-13 17:00:00', NOW(), NOW()),
(88, 3, 13, 'pending', 0, 'Vue3, Vite, Pinia', '1年前端开发经验', '新技术学习快，适应能力强', '2026-02-13 18:00:00', NOW(), NOW()),
(89, 3, 14, 'pending', 0, 'Java, 微服务, Docker', '1年后端开发经验', '架构设计能力强，问题解决能力好', '2026-02-13 19:00:00', NOW(), NOW()),
(90, 3, 4, 'pending', 0, 'NLP, 推荐系统, 深度学习', '1年算法经验', '算法实现能力强，创新思维好', '2026-02-13 20:00:00', NOW(), NOW()),
(91, 3, 13, 'pending', 0, 'React Native, Flutter, 小程序', '1年跨平台开发经验', '移动端开发经验丰富', '2026-02-13 21:00:00', NOW(), NOW()),
(92, 3, 13, 'pending', 0, '移动端UI, 交互设计', '1年移动端设计经验', '用户体验设计能力强', '2026-02-13 22:00:00', NOW(), NOW()),
(93, 3, 13, 'pending', 0, 'Web安全, APP安全', '1年安全测试经验', '安全测试全面，漏洞挖掘能力强', '2026-02-13 23:00:00', NOW(), NOW()),
(94, 3, 13, 'pending', 0, '接口测试, UI测试, 性能测试', '1年测试经验', '测试覆盖全面，质量意识强', '2026-02-14 00:00:00', NOW(), NOW());

-- 已查看的岗位申请（15条）
INSERT IGNORE INTO internship_application 
(student_id, company_id, position_id, status, viewed, skills, experience, self_evaluation, apply_time, create_time, update_time)
VALUES 
(95, 3, 13, 'pending', 1, 'Figma, Sketch, Adobe XD', '1年UI设计经验', '设计感强，创意丰富', '2026-02-12 09:00:00', NOW(), NOW()),
(96, 3, 13, 'pending', 1, '网络安全, 渗透测试', '1年安全测试经验', '安全意识强，细心负责', '2026-02-12 10:00:00', NOW(), NOW()),
(97, 3, 13, 'pending', 1, 'Selenium, JUnit, Postman', '1年测试经验', '测试用例设计能力强', '2026-02-12 11:00:00', NOW(), NOW()),
(98, 3, 13, 'pending', 1, 'Python, SQL, Pandas', '1年数据分析经验', '数据处理能力强，逻辑清晰', '2026-02-12 12:00:00', NOW(), NOW()),
(99, 3, 13, 'confirmed', 1, 'Vue.js, React, TypeScript', '1年前端开发经验', '学习能力强，善于沟通', '2026-02-11 09:00:00', NOW(), NOW()),
(100, 3, 14, 'confirmed', 1, 'Java, Spring Boot, MySQL', '1年后端开发经验', '技术扎实，责任心强', '2026-02-11 10:00:00', NOW(), NOW()),
(101, 3, 4, 'confirmed', 1, 'Python, TensorFlow, PyTorch', '1年机器学习经验', '算法基础好，逻辑思维强', '2026-02-11 11:00:00', NOW(), NOW()),
(102, 3, 13, 'rejected', 1, 'React, Vue, Node.js', '1年前端开发经验', '全栈开发能力，团队协作好', '2026-02-10 09:00:00', NOW(), NOW()),
(103, 3, 13, 'rejected', 1, 'Figma, Sketch, Adobe XD', '1年UI设计经验', '设计感强，创意丰富', '2026-02-10 10:00:00', NOW(), NOW()),
(104, 3, 13, 'pending', 1, '网络安全, 渗透测试', '1年安全测试经验', '安全意识强，细心负责', '2026-02-09 09:00:00', NOW(), NOW()),
(105, 3, 13, 'pending', 1, 'Selenium, JUnit, Postman', '1年测试经验', '测试用例设计能力强', '2026-02-09 10:00:00', NOW(), NOW()),
(106, 3, 13, 'pending', 1, 'Python, SQL, Pandas', '1年数据分析经验', '数据处理能力强，逻辑清晰', '2026-02-09 11:00:00', NOW(), NOW()),
(107, 3, 13, 'pending', 1, 'Vue3, Vite, Pinia', '1年前端开发经验', '新技术学习快，适应能力强', '2026-02-08 09:00:00', NOW(), NOW()),
(108, 3, 14, 'pending', 1, 'Java, 微服务, Docker', '1年后端开发经验', '架构设计能力强，问题解决能力好', '2026-02-08 10:00:00', NOW(), NOW()),
(109, 3, 4, 'pending', 1, 'NLP, 推荐系统, 深度学习', '1年算法经验', '算法实现能力强，创新思维好', '2026-02-07 09:00:00', NOW(), NOW());

-- ========================================
-- 第四部分：验证数据
-- ========================================

-- 验证1：学生用户数据统计
SELECT 
    '验证1: 学生用户数据统计' as verification_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN grade = 2022 THEN 1 ELSE 0 END) as grade_2022_count,
    SUM(CASE WHEN major_id = 1 THEN 1 ELSE 0 END) as major_1_count,
    SUM(CASE WHEN major_id = 2 THEN 1 ELSE 0 END) as major_2_count,
    SUM(CASE WHEN major_id = 3 THEN 1 ELSE 0 END) as major_3_count
FROM student_users
WHERE id >= 80;

-- 验证2：实习确认页面数据统计（新增学生）
SELECT 
    '验证2: 实习确认页面数据统计（新增学生）' as verification_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN company_confirm_status = 0 THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN company_confirm_status = 1 THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN company_confirm_status = 2 THEN 1 ELSE 0 END) as rejected_count
FROM student_internship_status
WHERE student_id >= 80;

-- 验证3：岗位申请查看页面数据统计（新增学生）
SELECT 
    '验证3: 岗位申请查看页面数据统计（新增学生）' as verification_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN viewed = 0 THEN 1 ELSE 0 END) as unviewed_count,
    SUM(CASE WHEN viewed = 1 THEN 1 ELSE 0 END) as viewed_count,
    SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN status = 'confirmed' THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN status = 'rejected' THEN 1 ELSE 0 END) as rejected_count
FROM internship_application
WHERE student_id >= 80;

-- 验证4：查看新增学生用户详情（限制显示10条）
SELECT 
    '验证4: 新增学生用户详情' as verification_info,
    id,
    username,
    name,
    CASE 
        WHEN gender = 1 THEN '男'
        WHEN gender = 2 THEN '女'
        ELSE '未知'
    END as gender_text,
    student_user_id,
    grade,
    status
FROM student_users
WHERE id >= 80
ORDER BY id
LIMIT 10;

-- 验证5：查看新增学生实习确认详情（按状态分组，限制显示10条）
SELECT 
    '验证5: 新增学生实习确认详情' as verification_info,
    sis.id,
    su.name as student_name,
    su.student_user_id,
    p.position_name,
    sis.company_confirm_status,
    CASE 
        WHEN sis.company_confirm_status = 0 THEN '待确认'
        WHEN sis.company_confirm_status = 1 THEN '已确认'
        WHEN sis.company_confirm_status = 2 THEN '已拒绝'
        ELSE '未知'
    END as status_text,
    sis.internship_start_time,
    sis.internship_end_time,
    sis.internship_duration,
    sis.remark
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
LEFT JOIN position p ON sis.position_id = p.id
WHERE sis.student_id >= 80
ORDER BY sis.company_confirm_status, sis.create_time
LIMIT 10;

-- 验证6：查看新增学生岗位申请详情（按查看状态分组，限制显示10条）
SELECT 
    '验证6: 新增学生岗位申请详情' as verification_info,
    a.id,
    su.name as student_name,
    su.student_user_id,
    p.position_name,
    a.status,
    a.viewed,
    CASE 
        WHEN a.viewed = 0 THEN '未查看'
        WHEN a.viewed = 1 THEN '已查看'
        ELSE '未知'
    END as viewed_text,
    a.apply_time
FROM internship_application a
LEFT JOIN student_users su ON a.student_id = su.id
LEFT JOIN position p ON a.position_id = p.id
WHERE a.student_id >= 80
ORDER BY a.viewed, a.apply_time DESC
LIMIT 10;

-- ========================================
-- 执行说明
-- ========================================

-- 1. 执行本脚本插入新的学生用户和相关数据
-- 2. 查看验证查询的结果，确认数据是否正确
-- 3. 刷新前端页面，检查数据是否正确显示

-- 注意事项：
-- - 使用 INSERT IGNORE 避免重复插入
-- - 新增学生ID范围: 80-109（共30名学生）
-- - 新增学生年级: 2022级
-- - 新增学生专业: 计算机科学与技术(1)、软件工程(2)、网络工程(3)
-- - 新增学生班级: 计科201班(79)、计科202班(80)、软工201班(81)、软工202班(82)、网工201班(83)
-- - position_id使用4, 13, 14（company_id=3的有效岗位ID）
--   - 4: 算法工程师
--   - 13: 移动端开发工程师
--   - 14: 后端开发工程师
