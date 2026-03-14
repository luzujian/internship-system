-- 增加更多测试数据
-- 数据库: internship
-- 表: student_internship_status 和 internship_application

-- ========================================
-- 第一部分：为实习确认页面增加测试数据
-- 目标：30-40条数据
-- 待确认：15-20条，已确认：10-12条，已拒绝：5-8条
-- ========================================

-- 更多待确认的记录 (15条)
INSERT INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(55, 3, 9, 2, 0, '2026-03-01', '2026-06-01', 92, '前端开发，熟悉Vue.js', NOW(), NOW()),
(56, 3, 19, 2, 0, '2026-03-05', '2026-06-05', 92, '后端开发，熟悉Spring Boot', NOW(), NOW()),
(57, 3, 60, 2, 0, '2026-03-08', '2026-06-08', 92, '算法工程师，熟悉机器学习', NOW(), NOW()),
(58, 3, 18, 2, 0, '2026-03-10', '2026-06-10', 92, '前端开发，熟悉React', NOW(), NOW()),
(59, 3, 49, 2, 0, '2026-03-12', '2026-06-12', 92, 'UI设计，熟悉Figma', NOW(), NOW()),
(60, 3, 61, 2, 0, '2026-03-15', '2026-06-15', 92, '安全工程师，熟悉网络安全', NOW(), NOW()),
(61, 3, 92, 2, 0, '2026-03-18', '2026-06-18', 92, '测试工程师，熟悉自动化测试', NOW(), NOW()),
(62, 3, 93, 2, 0, '2026-03-20', '2026-06-20', 92, '数据分析师，熟悉Python', NOW(), NOW()),
(63, 3, 9, 2, 0, '2026-03-22', '2026-06-22', 92, '前端开发，熟悉TypeScript', NOW(), NOW()),
(64, 3, 19, 2, 0, '2026-03-25', '2026-06-25', 92, '后端开发，熟悉MySQL', NOW(), NOW()),
(65, 3, 60, 2, 0, '2026-03-28', '2026-06-28', 92, '算法工程师，熟悉深度学习', NOW(), NOW()),
(66, 3, 18, 2, 0, '2026-04-01', '2026-07-01', 91, '前端开发，熟悉小程序开发', NOW(), NOW()),
(67, 3, 49, 2, 0, '2026-04-05', '2026-07-05', 91, 'UI设计，熟悉移动端设计', NOW(), NOW()),
(68, 3, 61, 2, 0, '2026-04-08', '2026-07-08', 91, '安全工程师，熟悉渗透测试', NOW(), NOW()),
(69, 3, 92, 2, 0, '2026-04-10', '2026-07-10', 91, '测试工程师，熟悉性能测试', NOW(), NOW());

-- 更多已确认的记录 (8条)
INSERT INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(70, 3, 9, 2, 1, '2026-02-05', '2026-05-05', 90, '已确认的前端开发实习生', NOW(), NOW()),
(71, 3, 19, 2, 1, '2026-02-08', '2026-05-08', 90, '已确认的后端开发实习生', NOW(), NOW()),
(72, 3, 60, 2, 1, '2026-02-10', '2026-05-10', 90, '已确认的算法工程师实习生', NOW(), NOW()),
(73, 3, 18, 2, 1, '2026-02-12', '2026-05-12', 90, '已确认的前端开发实习生', NOW(), NOW()),
(74, 3, 49, 2, 1, '2026-02-15', '2026-05-15', 90, '已确认的UI设计实习生', NOW(), NOW()),
(75, 3, 61, 2, 1, '2026-02-18', '2026-05-18', 90, '已确认的安全工程师实习生', NOW(), NOW()),
(76, 3, 92, 2, 1, '2026-02-20', '2026-05-20', 90, '已确认的测试工程师实习生', NOW(), NOW()),
(77, 3, 93, 2, 1, '2026-02-22', '2026-05-22', 90, '已确认的数据分析师实习生', NOW(), NOW());

-- 更多已拒绝的记录 (3条)
INSERT INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(78, 3, 9, 2, 2, '2026-02-25', '2026-05-25', 90, '已拒绝的前端开发申请', NOW(), NOW()),
(79, 3, 19, 2, 2, '2026-02-28', '2026-05-28', 90, '已拒绝的后端开发申请', NOW(), NOW()),
(80, 3, 60, 2, 2, '2026-03-02', '2026-06-02', 92, '已拒绝的算法工程师申请', NOW(), NOW());

-- ========================================
-- 第二部分：为岗位申请查看页面增加测试数据
-- 目标：25-30条数据
-- 未查看：10-15条，已查看：10-15条
-- ========================================

-- 未查看的申请 (12条)
INSERT INTO internship_application 
(student_id, company_id, position_id, apply_status, viewed, apply_time, create_time, update_time)
VALUES 
(81, 3, 9, 'pending', 0, '2026-02-13 10:00:00', NOW(), NOW()),
(82, 3, 19, 'pending', 0, '2026-02-13 11:00:00', NOW(), NOW()),
(83, 3, 60, 'pending', 0, '2026-02-13 12:00:00', NOW(), NOW()),
(84, 3, 18, 'pending', 0, '2026-02-13 13:00:00', NOW(), NOW()),
(85, 3, 49, 'pending', 0, '2026-02-13 14:00:00', NOW(), NOW()),
(86, 3, 61, 'pending', 0, '2026-02-13 15:00:00', NOW(), NOW()),
(87, 3, 92, 'pending', 0, '2026-02-13 16:00:00', NOW(), NOW()),
(88, 3, 93, 'pending', 0, '2026-02-13 17:00:00', NOW(), NOW()),
(89, 3, 9, 'pending', 0, '2026-02-13 18:00:00', NOW(), NOW()),
(90, 3, 19, 'pending', 0, '2026-02-13 19:00:00', NOW(), NOW()),
(91, 3, 60, 'pending', 0, '2026-02-13 20:00:00', NOW(), NOW()),
(92, 3, 18, 'pending', 0, '2026-02-13 21:00:00', NOW(), NOW());

-- 已查看的申请 (13条)
INSERT INTO internship_application 
(student_id, company_id, position_id, apply_status, viewed, apply_time, create_time, update_time)
VALUES 
(93, 3, 49, 'pending', 1, '2026-02-12 09:00:00', NOW(), NOW()),
(94, 3, 61, 'pending', 1, '2026-02-12 10:00:00', NOW(), NOW()),
(95, 3, 92, 'pending', 1, '2026-02-12 11:00:00', NOW(), NOW()),
(96, 3, 93, 'pending', 1, '2026-02-12 12:00:00', NOW(), NOW()),
(97, 3, 9, 'confirmed', 1, '2026-02-11 09:00:00', NOW(), NOW()),
(98, 3, 19, 'confirmed', 1, '2026-02-11 10:00:00', NOW(), NOW()),
(99, 3, 60, 'confirmed', 1, '2026-02-11 11:00:00', NOW(), NOW()),
(100, 3, 18, 'rejected', 1, '2026-02-10 09:00:00', NOW(), NOW()),
(101, 3, 49, 'rejected', 1, '2026-02-10 10:00:00', NOW(), NOW()),
(102, 3, 61, 'pending', 1, '2026-02-09 09:00:00', NOW(), NOW()),
(103, 3, 92, 'pending', 1, '2026-02-09 10:00:00', NOW(), NOW()),
(104, 3, 93, 'pending', 1, '2026-02-09 11:00:00', NOW(), NOW()),
(105, 3, 9, 'pending', 1, '2026-02-08 09:00:00', NOW(), NOW());

-- ========================================
-- 第三部分：验证数据
-- ========================================

-- 验证实习确认页面数据
SELECT 
    '实习确认页面数据统计' as info,
    COUNT(*) as total_count,
    SUM(CASE WHEN company_confirm_status = 0 THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN company_confirm_status = 1 THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN company_confirm_status = 2 THEN 1 ELSE 0 END) as rejected_count
FROM student_internship_status
WHERE company_id = 3;

-- 验证岗位申请查看页面数据
SELECT 
    '岗位申请查看页面数据统计' as info,
    COUNT(*) as total_count,
    SUM(CASE WHEN viewed = 0 THEN 1 ELSE 0 END) as unviewed_count,
    SUM(CASE WHEN viewed = 1 THEN 1 ELSE 0 END) as viewed_count,
    SUM(CASE WHEN apply_status = 'pending' THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN apply_status = 'confirmed' THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN apply_status = 'rejected' THEN 1 ELSE 0 END) as rejected_count
FROM internship_application
WHERE company_id = 3;

-- 查看实习确认页面数据详情（按状态分组）
SELECT 
    s.id,
    su.name as student_name,
    su.student_user_id,
    p.position_name,
    s.company_confirm_status,
    CASE 
        WHEN s.company_confirm_status = 0 THEN '待确认'
        WHEN s.company_confirm_status = 1 THEN '已确认'
        WHEN s.company_confirm_status = 2 THEN '已拒绝'
        ELSE '未知'
    END as status_text,
    s.internship_start_time,
    s.internship_end_time,
    s.internship_duration,
    s.remark
FROM student_internship_status s
LEFT JOIN student_users su ON s.student_id = su.id
LEFT JOIN position p ON s.position_id = p.id
WHERE s.company_id = 3
ORDER BY s.company_confirm_status, s.create_time;

-- 查看岗位申请查看页面数据详情（按查看状态分组）
SELECT 
    a.id,
    su.name as student_name,
    su.student_user_id,
    p.position_name,
    a.apply_status,
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
WHERE a.company_id = 3
ORDER BY a.viewed, a.apply_time DESC;
