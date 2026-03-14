-- 修复现有数据并生成测试数据
-- 数据库: internship
-- 表: student_internship_status

-- 1. 修复id=4的记录，补充company_id和position_id
UPDATE student_internship_status 
SET company_id = 3, 
    position_id = 19,
    status = 2
WHERE id = 4;

-- 2. 更新现有记录，补充实习时间信息和备注
-- 注意：internship_duration字段类型为int，存储天数

-- 记录1: 算法工程师，待确认
UPDATE student_internship_status 
SET internship_start_time = '2026-02-15',
    internship_end_time = '2026-05-15',
    internship_duration = 90,
    remark = '希望能在算法优化方面得到实践机会',
    company_confirm_status = 0
WHERE id = 1;

-- 记录2: 前端开发工程师，待确认
UPDATE student_internship_status 
SET internship_start_time = '2026-02-20',
    internship_end_time = '2026-05-20',
    internship_duration = 90,
    remark = '熟悉Vue和React框架',
    company_confirm_status = 0
WHERE id = 2;

-- 记录3: 后端开发工程师，待确认
UPDATE student_internship_status 
SET internship_start_time = '2026-02-25',
    internship_end_time = '2026-05-25',
    internship_duration = 90,
    remark = '熟悉Java和Spring Boot',
    company_confirm_status = 0
WHERE id = 3;

-- 记录4: 后端开发工程师，待确认
UPDATE student_internship_status 
SET internship_start_time = '2026-03-01',
    internship_end_time = '2026-06-01',
    internship_duration = 92,
    remark = '希望能够参与实际项目开发',
    company_confirm_status = 0
WHERE id = 4;

-- 记录5: 算法工程师，待确认
UPDATE student_internship_status 
SET internship_start_time = '2026-03-05',
    internship_end_time = '2026-06-05',
    internship_duration = 92,
    remark = '对机器学习有浓厚兴趣',
    company_confirm_status = 0
WHERE id = 5;

-- 3. 插入新的测试数据，包含不同状态的记录

-- 已确认的记录
INSERT INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(50, 3, 60, 2, 1, '2026-02-10', '2026-05-10', 90, '已确认的实习生', NOW(), NOW()),
(51, 3, 9, 2, 1, '2026-02-12', '2026-05-12', 90, '已确认的实习生', NOW(), NOW()),
(52, 3, 19, 2, 1, '2026-02-08', '2026-05-08', 90, '已确认的实习生', NOW(), NOW());

-- 已拒绝的记录
INSERT INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(53, 3, 60, 2, 2, '2026-02-15', '2026-05-15', 90, '已拒绝的申请', NOW(), NOW()),
(54, 3, 18, 2, 2, '2026-02-18', '2026-05-18', 90, '已拒绝的申请', NOW(), NOW());

-- 更多待确认的记录
INSERT INTO student_internship_status 
(student_id, company_id, position_id, status, company_confirm_status, internship_start_time, internship_end_time, internship_duration, remark, create_time, update_time)
VALUES 
(45, 3, 49, 2, 0, '2026-02-20', '2026-05-20', 90, 'UI设计岗位申请', NOW(), NOW()),
(46, 3, 61, 2, 0, '2026-02-22', '2026-05-22', 90, '安全工程师岗位申请', NOW(), NOW()),
(47, 3, 92, 2, 0, '2026-02-25', '2026-05-25', 90, '测试工程师岗位申请', NOW(), NOW()),
(48, 3, 93, 2, 0, '2026-02-28', '2026-05-28', 90, '数据分析师岗位申请', NOW(), NOW());

-- 验证数据
SELECT 
    'student_internship_status表统计' as info,
    COUNT(*) as total_count,
    SUM(CASE WHEN company_confirm_status = 0 THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN company_confirm_status = 1 THEN 1 ELSE 0 END) as confirmed_count,
    SUM(CASE WHEN company_confirm_status = 2 THEN 1 ELSE 0 END) as rejected_count
FROM student_internship_status;

-- 查看所有数据
SELECT 
    s.id,
    su.name as student_name,
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
ORDER BY s.company_confirm_status, s.create_time;
