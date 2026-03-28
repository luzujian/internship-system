-- 添加测试学生和实习状态数据
-- 用于验证教师端首页看板功能

-- 插入测试学生数据
INSERT INTO student_users (username, student_user_id, name, gender, grade, major_id, class_id, password, create_time, update_time) VALUES
('2021001', '2021001', '张三', 1, '2021', 1, 1, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021002', '2021002', '李四', 1, '2021', 1, 1, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021003', '2021003', '王五', 2, '2021', 1, 1, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021004', '2021004', '赵六', 1, '2021', 2, 2, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021005', '2021005', '钱七', 2, '2021', 2, 2, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021006', '2021006', '孙八', 1, '2021', 3, 3, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021007', '2021007', '周九', 1, '2021', 3, 3, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021008', '2021008', '吴十', 2, '2021', 3, 3, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021009', '2021009', '郑十一', 1, '2021', 1, 1, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW()),
('2021010', '2021010', '王十二', 1, '2021', 1, 1, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NOW(), NOW());

-- 插入实习状态数据
-- 已确定实习 (status = 2)
INSERT INTO student_internship_status (student_id, status, company_id, position_id, has_complaint, is_delayed, is_interrupted, create_time, update_time) VALUES
(1, 2, 1, 1, 0, 0, 0, NOW(), NOW()),
(2, 2, 1, 2, 0, 0, 0, NOW(), NOW()),
(3, 2, 2, 3, 0, 0, 0, NOW(), NOW());

-- 有offer但未确定 (status = 1)
INSERT INTO student_internship_status (student_id, status, company_id, position_id, has_complaint, is_delayed, is_interrupted, create_time, update_time) VALUES
(4, 1, 3, 4, 0, 0, 0, NOW(), NOW()),
(5, 1, 3, 5, 0, 0, 0, NOW(), NOW());

-- 没offer (status = 0)
INSERT INTO student_internship_status (student_id, status, company_id, position_id, has_complaint, is_delayed, is_interrupted, create_time, update_time) VALUES
(6, 0, NULL, NULL, 0, 0, 0, NOW(), NOW()),
(7, 0, NULL, NULL, 0, 0, 0, NOW(), NOW()),
(8, 0, NULL, NULL, 0, 0, 0, NOW(), NOW());

-- 延期 (is_delayed = 1)
INSERT INTO student_internship_status (student_id, status, company_id, position_id, has_complaint, is_delayed, is_interrupted, create_time, update_time) VALUES
(9, 2, 4, 6, 0, 1, 0, NOW(), NOW()),
(10, 2, 4, 7, 0, 1, 0, NOW(), NOW());

-- 验证数据
SELECT '学生数据:' as info;
SELECT id, student_user_id, name, gender, grade, major_id, class_id FROM student_users ORDER BY id;

SELECT '实习状态数据:' as info;
SELECT sis.id, su.student_user_id, su.name, sis.status, sis.company_id, sis.position_id, sis.is_delayed 
FROM student_internship_status sis 
LEFT JOIN student_users su ON sis.student_id = su.id 
ORDER BY sis.id;

-- 验证统计数据
SELECT '统计数据:' as info;
SELECT 
    COUNT(DISTINCT su.id) AS totalStudents,
    SUM(CASE WHEN sis.status = 2 THEN 1 ELSE 0 END) AS confirmed,
    SUM(CASE WHEN sis.status = 1 THEN 1 ELSE 0 END) AS offer,
    SUM(CASE WHEN sis.status = 0 OR sis.status IS NULL THEN 1 ELSE 0 END) AS noOffer,
    SUM(CASE WHEN sis.is_delayed = 1 THEN 1 ELSE 0 END) AS delay
FROM student_users su
LEFT JOIN student_internship_status sis ON su.id = sis.student_id;
