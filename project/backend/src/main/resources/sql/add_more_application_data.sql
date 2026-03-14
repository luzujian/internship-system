-- 为字节跳动的各个岗位增加申请数据
-- 目标：每个岗位10-15个学生申请

SELECT '========== Part 1: 删除前端开发工程师的5个申请记录 ==========' as status;

-- 删除前端开发工程师的5个申请记录（从17个减少到12个）
DELETE FROM internship_application 
WHERE position_id = 18 
ORDER BY id 
LIMIT 5;

DELETE FROM student_internship_status 
WHERE position_id = 18 
ORDER BY id 
LIMIT 5;

SELECT '========== Part 2: 为产品经理增加9个申请记录 ==========' as status;

-- 为产品经理（id=60）增加9个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(21, 60, 3, 'pending', 0, NOW(), NOW(), NOW()),
(22, 60, 3, 'pending', 0, NOW(), NOW(), NOW()),
(23, 60, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(24, 60, 3, 'pending', 0, NOW(), NOW(), NOW()),
(25, 60, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(26, 60, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(27, 60, 3, 'pending', 0, NOW(), NOW(), NOW()),
(28, 60, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(29, 60, 3, 'pending', 0, NOW(), NOW(), NOW());

-- 为这些学生创建实习状态记录
INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(21, 60, 3, 2, 0, NOW(), NOW()),
(22, 60, 3, 2, 0, NOW(), NOW()),
(23, 60, 3, 2, 1, NOW(), NOW()),
(24, 60, 3, 2, 0, NOW(), NOW()),
(25, 60, 3, 2, 1, NOW(), NOW()),
(26, 60, 3, 2, 2, NOW(), NOW()),
(27, 60, 3, 2, 0, NOW(), NOW()),
(28, 60, 3, 2, 1, NOW(), NOW()),
(29, 60, 3, 2, 0, NOW(), NOW());

SELECT '========== Part 3: 为算法工程师增加12个申请记录 ==========' as status;

-- 为算法工程师（id=9）增加12个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(30, 9, 3, 'pending', 0, NOW(), NOW(), NOW()),
(31, 9, 3, 'pending', 0, NOW(), NOW(), NOW()),
(32, 9, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(33, 9, 3, 'pending', 0, NOW(), NOW(), NOW()),
(34, 9, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(35, 9, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(36, 9, 3, 'pending', 0, NOW(), NOW(), NOW()),
(37, 9, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(38, 9, 3, 'pending', 0, NOW(), NOW(), NOW()),
(39, 9, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(40, 9, 3, 'pending', 0, NOW(), NOW(), NOW()),
(41, 9, 3, 'rejected', 0, NOW(), NOW(), NOW());

INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(30, 9, 3, 2, 0, NOW(), NOW()),
(31, 9, 3, 2, 0, NOW(), NOW()),
(32, 9, 3, 2, 1, NOW(), NOW()),
(33, 9, 3, 2, 0, NOW(), NOW()),
(34, 9, 3, 2, 1, NOW(), NOW()),
(35, 9, 3, 2, 2, NOW(), NOW()),
(36, 9, 3, 2, 0, NOW(), NOW()),
(37, 9, 3, 2, 1, NOW(), NOW()),
(38, 9, 3, 2, 0, NOW(), NOW()),
(39, 9, 3, 2, 1, NOW(), NOW()),
(40, 9, 3, 2, 0, NOW(), NOW()),
(41, 9, 3, 2, 2, NOW(), NOW());

SELECT '========== Part 4: 为后端开发工程师增加12个申请记录 ==========' as status;

-- 为后端开发工程师（id=19）增加12个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(42, 19, 3, 'pending', 0, NOW(), NOW(), NOW()),
(43, 19, 3, 'pending', 0, NOW(), NOW(), NOW()),
(44, 19, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(45, 19, 3, 'pending', 0, NOW(), NOW(), NOW()),
(46, 19, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(47, 19, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(48, 19, 3, 'pending', 0, NOW(), NOW(), NOW()),
(49, 19, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(50, 19, 3, 'pending', 0, NOW(), NOW(), NOW()),
(51, 19, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(52, 19, 3, 'pending', 0, NOW(), NOW(), NOW()),
(53, 19, 3, 'rejected', 0, NOW(), NOW(), NOW());

INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(42, 19, 3, 2, 0, NOW(), NOW()),
(43, 19, 3, 2, 0, NOW(), NOW()),
(44, 19, 3, 2, 1, NOW(), NOW()),
(45, 19, 3, 2, 0, NOW(), NOW()),
(46, 19, 3, 2, 1, NOW(), NOW()),
(47, 19, 3, 2, 2, NOW(), NOW()),
(48, 19, 3, 2, 0, NOW(), NOW()),
(49, 19, 3, 2, 1, NOW(), NOW()),
(50, 19, 3, 2, 0, NOW(), NOW()),
(51, 19, 3, 2, 1, NOW(), NOW()),
(52, 19, 3, 2, 0, NOW(), NOW()),
(53, 19, 3, 2, 2, NOW(), NOW());

SELECT '========== Part 5: 为UI设计师增加12个申请记录 ==========' as status;

-- 为UI设计师（id=49）增加12个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(54, 49, 3, 'pending', 0, NOW(), NOW(), NOW()),
(55, 49, 3, 'pending', 0, NOW(), NOW(), NOW()),
(56, 49, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(57, 49, 3, 'pending', 0, NOW(), NOW(), NOW()),
(58, 49, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(59, 49, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(60, 49, 3, 'pending', 0, NOW(), NOW(), NOW()),
(61, 49, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(62, 49, 3, 'pending', 0, NOW(), NOW(), NOW()),
(63, 49, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(64, 49, 3, 'pending', 0, NOW(), NOW(), NOW()),
(65, 49, 3, 'rejected', 0, NOW(), NOW(), NOW());

INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(54, 49, 3, 2, 0, NOW(), NOW()),
(55, 49, 3, 2, 0, NOW(), NOW()),
(56, 49, 3, 2, 1, NOW(), NOW()),
(57, 49, 3, 2, 0, NOW(), NOW()),
(58, 49, 3, 2, 1, NOW(), NOW()),
(59, 49, 3, 2, 2, NOW(), NOW()),
(60, 49, 3, 2, 0, NOW(), NOW()),
(61, 49, 3, 2, 1, NOW(), NOW()),
(62, 49, 3, 2, 0, NOW(), NOW()),
(63, 49, 3, 2, 1, NOW(), NOW()),
(64, 49, 3, 2, 0, NOW(), NOW()),
(65, 49, 3, 2, 2, NOW(), NOW());

SELECT '========== Part 6: 为安全工程师增加12个申请记录 ==========' as status;

-- 为安全工程师（id=61）增加12个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(66, 61, 3, 'pending', 0, NOW(), NOW(), NOW()),
(67, 61, 3, 'pending', 0, NOW(), NOW(), NOW()),
(68, 61, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(69, 61, 3, 'pending', 0, NOW(), NOW(), NOW()),
(70, 61, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(71, 61, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(72, 61, 3, 'pending', 0, NOW(), NOW(), NOW()),
(73, 61, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(74, 61, 3, 'pending', 0, NOW(), NOW(), NOW()),
(75, 61, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(76, 61, 3, 'pending', 0, NOW(), NOW(), NOW()),
(77, 61, 3, 'rejected', 0, NOW(), NOW(), NOW());

INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(66, 61, 3, 2, 0, NOW(), NOW()),
(67, 61, 3, 2, 0, NOW(), NOW()),
(68, 61, 3, 2, 1, NOW(), NOW()),
(69, 61, 3, 2, 0, NOW(), NOW()),
(70, 61, 3, 2, 1, NOW(), NOW()),
(71, 61, 3, 2, 2, NOW(), NOW()),
(72, 61, 3, 2, 0, NOW(), NOW()),
(73, 61, 3, 2, 1, NOW(), NOW()),
(74, 61, 3, 2, 0, NOW(), NOW()),
(75, 61, 3, 2, 1, NOW(), NOW()),
(76, 61, 3, 2, 0, NOW(), NOW()),
(77, 61, 3, 2, 2, NOW(), NOW());

SELECT '========== Part 7: 为测试工程师增加12个申请记录 ==========' as status;

-- 为测试工程师（id=92）增加12个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(78, 92, 3, 'pending', 0, NOW(), NOW(), NOW()),
(79, 92, 3, 'pending', 0, NOW(), NOW(), NOW()),
(1, 92, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(2, 92, 3, 'pending', 0, NOW(), NOW(), NOW()),
(3, 92, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(4, 92, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(5, 92, 3, 'pending', 0, NOW(), NOW(), NOW()),
(6, 92, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(7, 92, 3, 'pending', 0, NOW(), NOW(), NOW()),
(8, 92, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(9, 92, 3, 'pending', 0, NOW(), NOW(), NOW()),
(10, 92, 3, 'rejected', 0, NOW(), NOW(), NOW());

INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(78, 92, 3, 2, 0, NOW(), NOW()),
(79, 92, 3, 2, 0, NOW(), NOW()),
(1, 92, 3, 2, 1, NOW(), NOW()),
(2, 92, 3, 2, 0, NOW(), NOW()),
(3, 92, 3, 2, 1, NOW(), NOW()),
(4, 92, 3, 2, 2, NOW(), NOW()),
(5, 92, 3, 2, 0, NOW(), NOW()),
(6, 92, 3, 2, 1, NOW(), NOW()),
(7, 92, 3, 2, 0, NOW(), NOW()),
(8, 92, 3, 2, 1, NOW(), NOW()),
(9, 92, 3, 2, 0, NOW(), NOW()),
(10, 92, 3, 2, 2, NOW(), NOW());

SELECT '========== Part 8: 为数据分析师增加12个申请记录 ==========' as status;

-- 为数据分析师（id=93）增加12个申请记录
INSERT INTO internship_application (student_id, position_id, company_id, status, viewed, apply_time, create_time, update_time)
VALUES
(11, 93, 3, 'pending', 0, NOW(), NOW(), NOW()),
(12, 93, 3, 'pending', 0, NOW(), NOW(), NOW()),
(13, 93, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(14, 93, 3, 'pending', 0, NOW(), NOW(), NOW()),
(15, 93, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(16, 93, 3, 'rejected', 0, NOW(), NOW(), NOW()),
(17, 93, 3, 'pending', 0, NOW(), NOW(), NOW()),
(18, 93, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(19, 93, 3, 'pending', 0, NOW(), NOW(), NOW()),
(20, 93, 3, 'confirmed', 0, NOW(), NOW(), NOW()),
(21, 93, 3, 'pending', 0, NOW(), NOW(), NOW()),
(22, 93, 3, 'rejected', 0, NOW(), NOW(), NOW());

INSERT INTO student_internship_status (student_id, position_id, company_id, status, company_confirm_status, create_time, update_time)
VALUES
(11, 93, 3, 2, 0, NOW(), NOW()),
(12, 93, 3, 2, 0, NOW(), NOW()),
(13, 93, 3, 2, 1, NOW(), NOW()),
(14, 93, 3, 2, 0, NOW(), NOW()),
(15, 93, 3, 2, 1, NOW(), NOW()),
(16, 93, 3, 2, 2, NOW(), NOW()),
(17, 93, 3, 2, 0, NOW(), NOW()),
(18, 93, 3, 2, 1, NOW(), NOW()),
(19, 93, 3, 2, 0, NOW(), NOW()),
(20, 93, 3, 2, 1, NOW(), NOW()),
(21, 93, 3, 2, 0, NOW(), NOW()),
(22, 93, 3, 2, 2, NOW(), NOW());

SELECT '========== Part 9: 验证数据增加结果 ==========' as status;

SELECT 
    p.id AS position_id,
    p.position_name,
    COUNT(ia.id) AS application_count
FROM position p
LEFT JOIN internship_application ia ON p.id = ia.position_id
WHERE p.company_id = 3 AND p.id IN (9, 18, 19, 49, 60, 61, 92, 93)
GROUP BY p.id, p.position_name
ORDER BY p.id;

SELECT '========== Part 10: 数据增加完成 ==========' as status;
SELECT '已为字节跳动的8个岗位增加申请数据，每个岗位约12个申请' AS message;
