-- 创建班级表和专业表（如果不存在），并插入测试数据

-- 创建班级表
CREATE TABLE IF NOT EXISTS `class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '班级ID',
    `name` VARCHAR(100) NOT NULL COMMENT '班级名称',
    `major_id` BIGINT NOT NULL COMMENT '所属专业ID',
    `teacher_user_id` VARCHAR(50) COMMENT '负责教师ID',
    `grade` INT COMMENT '年级',
    `student_count` INT DEFAULT 0 COMMENT '班级人数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_major_id` (`major_id`),
    KEY `idx_grade` (`grade`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

-- 创建专业表
CREATE TABLE IF NOT EXISTS `major` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '专业ID',
    `name` VARCHAR(100) NOT NULL COMMENT '专业名称',
    `department_id` BIGINT COMMENT '所属院系ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_department_id` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业表';

-- 插入专业数据
INSERT IGNORE INTO `major` (id, name, department_id, create_time, update_time) VALUES
(1, '临床医学', 1, NOW(), NOW()),
(2, '护理学', 1, NOW(), NOW()),
(3, '医学影像学', 1, NOW(), NOW()),
(4, '医学检验技术', 1, NOW(), NOW()),
(5, '药学', 1, NOW(), NOW());

-- 插入班级数据
INSERT IGNORE INTO `class` (id, name, major_id, grade, student_count, create_time, update_time) VALUES
-- 临床医学专业班级
(1, '2021级临床医学1班', 1, 2021, 30, NOW(), NOW()),
(2, '2021级临床医学2班', 1, 2021, 32, NOW(), NOW()),
(3, '2022级临床医学1班', 1, 2022, 35, NOW(), NOW()),
(4, '2022级临床医学2班', 1, 2022, 33, NOW(), NOW()),
(5, '2023级临床医学1班', 1, 2023, 38, NOW(), NOW()),
(6, '2023级临床医学2班', 1, 2023, 36, NOW(), NOW()),
(7, '2024级临床医学1班', 1, 2024, 40, NOW(), NOW()),
(8, '2024级临床医学2班', 1, 2024, 38, NOW(), NOW()),

-- 护理学专业班级
(9, '2021级护理学1班', 2, 2021, 28, NOW(), NOW()),
(10, '2021级护理学2班', 2, 2021, 30, NOW(), NOW()),
(11, '2022级护理学1班', 2, 2022, 32, NOW(), NOW()),
(12, '2022级护理学2班', 2, 2022, 34, NOW(), NOW()),
(13, '2023级护理学1班', 2, 2023, 36, NOW(), NOW()),
(14, '2023级护理学2班', 2, 2023, 35, NOW(), NOW()),
(15, '2024级护理学1班', 2, 2024, 38, NOW(), NOW()),
(16, '2024级护理学2班', 2, 2024, 36, NOW(), NOW()),

-- 医学影像学专业班级
(17, '2021级医学影像学1班', 3, 2021, 25, NOW(), NOW()),
(18, '2022级医学影像学1班', 3, 2022, 28, NOW(), NOW()),
(19, '2023级医学影像学1班', 3, 2023, 30, NOW(), NOW()),
(20, '2024级医学影像学1班', 3, 2024, 32, NOW(), NOW()),

-- 医学检验技术专业班级
(21, '2021级医学检验技术1班', 4, 2021, 22, NOW(), NOW()),
(22, '2022级医学检验技术1班', 4, 2022, 25, NOW(), NOW()),
(23, '2023级医学检验技术1班', 4, 2023, 27, NOW(), NOW()),
(24, '2024级医学检验技术1班', 4, 2024, 29, NOW(), NOW()),

-- 药学专业班级
(25, '2021级药学1班', 5, 2021, 24, NOW(), NOW()),
(26, '2022级药学1班', 5, 2022, 26, NOW(), NOW()),
(27, '2023级药学1班', 5, 2023, 28, NOW(), NOW()),
(28, '2024级药学1班', 5, 2024, 30, NOW(), NOW());

-- 验证数据
SELECT '专业数据:' as info;
SELECT id, name, department_id, create_time FROM major ORDER BY id;

SELECT '班级数据:' as info;
SELECT c.id, c.name, c.major_id, m.name as major_name, c.grade, c.student_count 
FROM class c 
LEFT JOIN major m ON c.major_id = m.id 
ORDER BY c.id;