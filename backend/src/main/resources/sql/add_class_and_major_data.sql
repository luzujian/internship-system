-- 插入班级和专业测试数据
-- 用于修复教师端学生状态监控页面的下拉菜单数据问题

-- 插入专业数据
INSERT INTO major (name, department_id, create_time, update_time) VALUES
('临床医学', 1, NOW(), NOW()),
('护理学', 1, NOW(), NOW()),
('医学影像学', 1, NOW(), NOW()),
('医学检验技术', 1, NOW(), NOW()),
('药学', 1, NOW(), NOW());

-- 插入班级数据
-- 临床医学专业班级
INSERT INTO class (name, major_id, grade, student_count, create_time, update_time) VALUES
('2021级临床医学1班', 1, 2021, 30, NOW(), NOW()),
('2021级临床医学2班', 1, 2021, 32, NOW(), NOW()),
('2022级临床医学1班', 1, 2022, 35, NOW(), NOW()),
('2022级临床医学2班', 1, 2022, 33, NOW(), NOW()),
('2023级临床医学1班', 1, 2023, 38, NOW(), NOW()),
('2023级临床医学2班', 1, 2023, 36, NOW(), NOW());

-- 护理学专业班级
INSERT INTO class (name, major_id, grade, student_count, create_time, update_time) VALUES
('2021级护理学1班', 2, 2021, 28, NOW(), NOW()),
('2021级护理学2班', 2, 2021, 30, NOW(), NOW()),
('2022级护理学1班', 2, 2022, 32, NOW(), NOW()),
('2022级护理学2班', 2, 2022, 34, NOW(), NOW()),
('2023级护理学1班', 2, 2023, 36, NOW(), NOW()),
('2023级护理学2班', 2, 2023, 35, NOW(), NOW());

-- 医学影像学专业班级
INSERT INTO class (name, major_id, grade, student_count, create_time, update_time) VALUES
('2021级医学影像学1班', 3, 2021, 25, NOW(), NOW()),
('2022级医学影像学1班', 3, 2022, 28, NOW(), NOW()),
('2023级医学影像学1班', 3, 2023, 30, NOW(), NOW());

-- 医学检验技术专业班级
INSERT INTO class (name, major_id, grade, student_count, create_time, update_time) VALUES
('2021级医学检验技术1班', 4, 2021, 22, NOW(), NOW()),
('2022级医学检验技术1班', 4, 2022, 25, NOW(), NOW()),
('2023级医学检验技术1班', 4, 2023, 27, NOW(), NOW());

-- 药学专业班级
INSERT INTO class (name, major_id, grade, student_count, create_time, update_time) VALUES
('2021级药学1班', 5, 2021, 24, NOW(), NOW()),
('2022级药学1班', 5, 2022, 26, NOW(), NOW()),
('2023级药学1班', 5, 2023, 28, NOW(), NOW());

-- 验证数据
SELECT '专业数据:' as info;
SELECT id, name, department_id, create_time FROM major ORDER BY id;

SELECT '班级数据:' as info;
SELECT c.id, c.name, c.major_id, m.name as major_name, c.grade, c.student_count 
FROM class c 
LEFT JOIN major m ON c.major_id = m.id 
ORDER BY c.id;
