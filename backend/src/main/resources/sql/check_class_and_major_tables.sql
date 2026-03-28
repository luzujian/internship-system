-- 检查班级表是否存在以及是否有数据

-- 检查班级表是否存在
SELECT '检查班级表是否存在' as info;
SELECT 
    TABLE_NAME as 表名,
    TABLE_ROWS as 行数,
    CREATE_TIME as 创建时间
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'class';

-- 如果班级表存在，查看班级数据
SELECT '班级数据（如果表存在）' as info;
SELECT * FROM class ORDER BY id;

-- 检查专业表是否存在
SELECT '检查专业表是否存在' as info;
SELECT 
    TABLE_NAME as 表名,
    TABLE_ROWS as 行数,
    CREATE_TIME as 创建时间
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'major';

-- 如果专业表存在，查看专业数据
SELECT '专业数据（如果表存在）' as info;
SELECT * FROM major ORDER BY id;