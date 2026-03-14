-- 为实习确认页面和岗位申请查看页面增加测试数据
-- 特点：使用数据库中现有的student_id和position_id，避免外键约束错误
-- 创建时间：2026-02-13

-- ========================================
-- 第一部分：为实习确认页面增加测试数据
-- 目标：30-40条数据
-- 状态分布：待确认15-20条，已确认10-12条，已拒绝5-8条
-- ========================================

-- 注意：由于无法直接查询数据库中的现有ID，这里使用常见的ID范围
-- 如果插入失败，请先执行check_existing_data.sql查看可用的ID

-- 待确认的实习确认申请（15条）
-- 假设student_id范围：1-100，position_id范围：1-100
INSERT IGNORE INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(1, 3, 9, 2, 0, '2026-03-01', '2026-06-01', 92, '前端开发，熟悉Vue.js和React', NOW(), NOW()),
(2, 3, 19, 2, 0, '2026-03-05', '2026-06-05', 92, '后端开发，熟悉Spring Boot和MySQL', NOW(), NOW()),
(3, 3, 60, 2, 0, '2026-03-08', '2026-06-08', 92, '算法工程师，熟悉机器学习和深度学习', NOW(), NOW()),
(4, 3, 18, 2, 0, '2026-03-10', '2026-06-10', 92, '前端开发，熟悉TypeScript和小程序开发', NOW(), NOW()),
(5, 3, 49, 2, 0, '2026-03-12', '2026-06-12', 92, 'UI设计，熟悉Figma和Sketch', NOW(), NOW()),
(6, 3, 61, 2, 0, '2026-03-15', '2026-06-15', 92, '安全工程师，熟悉网络安全和渗透测试', NOW(), NOW()),
(7, 3, 92, 2, 0, '2026-03-18', '2026-06-18', 92, '测试工程师，熟悉自动化测试和性能测试', NOW(), NOW()),
(8, 3, 93, 2, 0, '2026-03-20', '2026-06-20', 92, '数据分析师，熟悉Python和SQL', NOW(), NOW()),
(9, 3, 9, 2, 0, '2026-03-22', '2026-06-22', 92, '前端开发，熟悉Vue3和Vite', NOW(), NOW()),
(10, 3, 19, 2, 0, '2026-03-25', '2026-06-25', 92, '后端开发，熟悉Java和微服务架构', NOW(), NOW()),
(11, 3, 60, 2, 0, '2026-03-28', '2026-06-28', 92, '算法工程师，熟悉NLP和推荐系统', NOW(), NOW()),
(12, 3, 18, 2, 0, '2026-04-01', '2026-07-01', 91, '前端开发，熟悉React Native和Flutter', NOW(), NOW()),
(13, 3, 49, 2, 0, '2026-04-05', '2026-07-05', 91, 'UI设计，熟悉移动端设计和交互设计', NOW(), NOW()),
(14, 3, 61, 2, 0, '2026-04-08', '2026-07-08', 91, '安全工程师，熟悉Web安全和APP安全', NOW(), NOW()),
(15, 3, 92, 2, 0, '2026-04-10', '2026-07-10', 91, '测试工程师，熟悉接口测试和UI测试', NOW(), NOW());

-- 已确认的实习确认申请（8条）
INSERT IGNORE INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(16, 3, 9, 2, 1, '2026-02-05', '2026-05-05', 90, '已确认的前端开发实习生，工作认真负责', NOW(), NOW()),
(17, 3, 19, 2, 1, '2026-02-08', '2026-05-08', 90, '已确认的后端开发实习生，技术能力强', NOW(), NOW()),
(18, 3, 60, 2, 1, '2026-02-10', '2026-05-10', 90, '已确认的算法工程师实习生，学习能力强', NOW(), NOW()),
(19, 3, 18, 2, 1, '2026-02-12', '2026-05-12', 90, '已确认的前端开发实习生，沟通能力好', NOW(), NOW()),
(20, 3, 49, 2, 1, '2026-02-15', '2026-05-15', 90, '已确认的UI设计实习生，设计水平高', NOW(), NOW()),
(21, 3, 61, 2, 1, '2026-02-18', '2026-05-18', 90, '已确认的安全工程师实习生，安全意识强', NOW(), NOW()),
(22, 3, 92, 2, 1, '2026-02-20', '2026-05-20', 90, '已确认的测试工程师实习生，细心负责', NOW(), NOW()),
(23, 3, 93, 2, 1, '2026-02-22', '2026-05-22', 90, '已确认的数据分析师实习生，数据分析能力强', NOW(), NOW());

-- 已拒绝的实习确认申请（3条）
INSERT IGNORE INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(24, 3, 9, 2, 2, '2026-02-25', '2026-05-25', 90, '已拒绝的前端开发申请，技能不符合要求', NOW(), NOW()),
(25, 3, 19, 2, 2, '2026-02-28', '2026-05-28', 90, '已拒绝的后端开发申请，经验不足', NOW(), NOW()),
(26, 3, 60, 2, 2, '2026-03-02', '2026-06-02', 92, '已拒绝的算法工程师申请，项目经验不够', NOW(), NOW());

-- ========================================
-- 第二部分：为岗位申请查看页面增加测试数据
-- 目标：25-30条数据
-- 状态分布：未查看12-15条，已查看13-15条
-- ========================================

-- 未查看的岗位申请（12条）
INSERT IGNORE INTO internship_application 
(student_id, company_id, position_id, apply_status, viewed, apply_time, create_time, update_time)
VALUES 
(27, 3, 9, 'pending', 0, '2026-02-13 10:00:00', NOW(), NOW()),
(28, 3, 19, 'pending', 0, '2026-02-13 11:00:00', NOW(), NOW()),
(29, 3, 60, 'pending', 0, '2026-02-13 12:00:00', NOW(), NOW()),
(30, 3, 18, 'pending', 0, '2026-02-13 13:00:00', NOW(), NOW()),
(31, 3, 49, 'pending', 0, '2026-02-13 14:00:00', NOW(), NOW()),
(32, 3, 61, 'pending', 0, '2026-02-13 15:00:00', NOW(), NOW()),
(33, 3, 92, 'pending', 0, '2026-02-13 16:00:00', NOW(), NOW()),
(34, 3, 93, 'pending', 0, '2026-02-13 17:00:00', NOW(), NOW()),
(35, 3, 9, 'pending', 0, '2026-02-13 18:00:00', NOW(), NOW()),
(36, 3, 19, 'pending', 0, '2026-02-13 19:00:00', NOW(), NOW()),
(37, 3, 60, 'pending', 0, '2026-02-13 20:00:00', NOW(), NOW()),
(38, 3, 18, 'pending', 0, '2026-02-13 21:00:00', NOW(), NOW());

-- 已查看的岗位申请（13条）
INSERT IGNORE INTO internship_application 
(student_id, company_id, position_id, apply_status, viewed, apply_time, create_time, update_time)
VALUES 
(39, 3, 49, 'pending', 1, '2026-02-12 09:00:00', NOW(), NOW()),
(40, 3, 61, 'pending', 1, '2026-02-12 10:00:00', NOW(), NOW()),
(41, 3, 92, 'pending', 1, '2026-02-12 11:00:00', NOW(), NOW()),
(42, 3, 93, 'pending', 1, '2026-02-12 12:00:00', NOW(), NOW()),
(43, 3, 9, 'confirmed', 1, '2026-02-11 09:00:00', NOW(), NOW()),
(44, 3, 19, 'confirmed', 1, '2026-02-11 10:00:00', NOW(), NOW()),
(45, 3, 60, 'confirmed', 1, '2026-02-11 11:00:00', NOW(), NOW()),
(46, 3, 18, 'rejected', 1, '2026-02-10 09:00:00', NOW(), NOW()),
(47, 3, 49, 'rejected', 1, '2026-02-10 10:00:00', NOW(), NOW()),
(48, 3, 61, 'pending', 1, '2026-02-09 09:00:00', NOW(), NOW()),
(49, 3, 92, 'pending', 1, '2026-02-09 10:00:00', NOW(), NOW()),
(50, 3, 93, 'pending', 1, '2026-02-09 11:00:00', NOW(), NOW()),
(51, 3, 9, 'pending', 1, '2026-02-08 09:00:00', NOW(), NOW());

-- ========================================
-- 第三部分：验证数据
-- ========================================

-- 验证1：实习确认页面数据统计
SELECT 
    '验证1: 实习确认页面数据统计（company_id=3）' as verification_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN company_confirm_status = 0 THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN company_confirm_status = 1 THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN company_confirm_status = 2 THEN 1 ELSE 0 END) as rejected_count
FROM student_internship_status
WHERE company_id = 3;

-- 验证2：岗位申请查看页面数据统计
SELECT 
    '验证2: 岗位申请查看页面数据统计（company_id=3）' as verification_info,
    COUNT(*) as total_count,
    SUM(CASE WHEN viewed = 0 THEN 1 ELSE 0 END) as unviewed_count,
    SUM(CASE WHEN viewed = 1 THEN 1 ELSE 0 END) as viewed_count,
    SUM(CASE WHEN apply_status = 'pending' THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN apply_status = 'confirmed' THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN apply_status = 'rejected' THEN 1 ELSE 0 END) as rejected_count
FROM internship_application
WHERE company_id = 3;

-- 验证3：查看实习确认页面数据详情（按状态分组）
SELECT 
    '验证3: 实习确认页面数据详情' as verification_info,
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
WHERE sis.company_id = 3
ORDER BY sis.company_confirm_status, sis.create_time
LIMIT 10;

-- 验证4：查看岗位申请查看页面数据详情（按查看状态分组）
SELECT 
    '验证4: 岗位申请查看页面数据详情' as verification_info,
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
ORDER BY a.viewed, a.apply_time DESC
LIMIT 10;

-- ========================================
-- 执行说明
-- ========================================

-- 1. 首先执行 check_existing_data.sql 查看可用的student_id和position_id
-- 2. 如果student_id或position_id不存在，修改本脚本中的ID值
-- 3. 执行本脚本插入测试数据
-- 4. 查看验证查询的结果，确认数据是否正确
-- 5. 刷新前端页面，检查数据是否正确显示

-- 注意事项：
-- - 使用 INSERT IGNORE 避免重复插入
-- - 如果插入失败，请检查student_id和position_id是否存在
-- - 如果数据量不够，可以增加更多的INSERT语句
