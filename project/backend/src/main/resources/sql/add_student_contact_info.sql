-- 为学生用户表添加联系电话和邮箱字段，并填充数据
-- 数据库: internship
-- 创建时间: 2026-02-15
-- 目标: 修复岗位申请查看页面联系电话未显示的问题

SET NAMES utf8mb4;

-- ========================================
-- 第一步：添加字段（使用存储过程检查）
-- ========================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    -- 添加 phone 字段
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = 'internship' 
          AND TABLE_NAME = 'student_users' 
          AND COLUMN_NAME = 'phone'
    ) THEN
        ALTER TABLE student_users ADD COLUMN phone VARCHAR(20) COMMENT '联系电话' AFTER class_id;
    END IF;
    
    -- 添加 email 字段
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = 'internship' 
          AND TABLE_NAME = 'student_users' 
          AND COLUMN_NAME = 'email'
    ) THEN
        ALTER TABLE student_users ADD COLUMN email VARCHAR(100) COMMENT '联系邮箱' AFTER phone;
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL add_column_if_not_exists();

-- 删除存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- ========================================
-- 第二步：为现有学生填充联系电话和邮箱
-- ========================================

UPDATE student_users SET phone = '13800138001', email = '20200001@example.com' WHERE id = 1;
UPDATE student_users SET phone = '13800138002', email = '20200002@example.com' WHERE id = 2;
UPDATE student_users SET phone = '13800138003', email = '20200003@example.com' WHERE id = 3;
UPDATE student_users SET phone = '13800138004', email = '20200004@example.com' WHERE id = 4;
UPDATE student_users SET phone = '13800138005', email = '20200005@example.com' WHERE id = 5;
UPDATE student_users SET phone = '13800138006', email = '20200006@example.com' WHERE id = 6;
UPDATE student_users SET phone = '13800138007', email = '20200007@example.com' WHERE id = 7;
UPDATE student_users SET phone = '13800138008', email = '20200008@example.com' WHERE id = 8;
UPDATE student_users SET phone = '13800138009', email = '20200009@example.com' WHERE id = 9;
UPDATE student_users SET phone = '13800138010', email = '20200010@example.com' WHERE id = 10;
UPDATE student_users SET phone = '13800138011', email = '20200011@example.com' WHERE id = 11;
UPDATE student_users SET phone = '13800138012', email = '20200012@example.com' WHERE id = 12;
UPDATE student_users SET phone = '13800138013', email = '20200013@example.com' WHERE id = 13;
UPDATE student_users SET phone = '13800138014', email = '20200014@example.com' WHERE id = 14;
UPDATE student_users SET phone = '13800138015', email = '20200015@example.com' WHERE id = 15;
UPDATE student_users SET phone = '13800138016', email = '20200016@example.com' WHERE id = 16;
UPDATE student_users SET phone = '13800138017', email = '20200017@example.com' WHERE id = 17;
UPDATE student_users SET phone = '13800138018', email = '20200018@example.com' WHERE id = 18;
UPDATE student_users SET phone = '13800138019', email = '20200019@example.com' WHERE id = 19;
UPDATE student_users SET phone = '13800138020', email = '20200020@example.com' WHERE id = 20;
UPDATE student_users SET phone = '13800138021', email = '20200021@example.com' WHERE id = 21;
UPDATE student_users SET phone = '13800138022', email = '20200022@example.com' WHERE id = 22;
UPDATE student_users SET phone = '13800138023', email = '20200023@example.com' WHERE id = 23;
UPDATE student_users SET phone = '13800138024', email = '20200024@example.com' WHERE id = 24;
UPDATE student_users SET phone = '13800138025', email = '20200025@example.com' WHERE id = 25;
UPDATE student_users SET phone = '13800138026', email = '20200026@example.com' WHERE id = 26;
UPDATE student_users SET phone = '13800138027', email = '20200027@example.com' WHERE id = 27;
UPDATE student_users SET phone = '13800138028', email = '20200028@example.com' WHERE id = 28;
UPDATE student_users SET phone = '13800138029', email = '20200029@example.com' WHERE id = 29;
UPDATE student_users SET phone = '13800138030', email = '20200030@example.com' WHERE id = 30;
UPDATE student_users SET phone = '13800138031', email = '20200031@example.com' WHERE id = 31;
UPDATE student_users SET phone = '13800138032', email = '20200032@example.com' WHERE id = 32;
UPDATE student_users SET phone = '13800138033', email = '20200033@example.com' WHERE id = 33;
UPDATE student_users SET phone = '13800138034', email = '20200034@example.com' WHERE id = 34;
UPDATE student_users SET phone = '13800138035', email = '20200035@example.com' WHERE id = 35;
UPDATE student_users SET phone = '13800138036', email = '20200036@example.com' WHERE id = 36;
UPDATE student_users SET phone = '13800138037', email = '20200037@example.com' WHERE id = 37;
UPDATE student_users SET phone = '13800138038', email = '20200038@example.com' WHERE id = 38;
UPDATE student_users SET phone = '13800138039', email = '20200039@example.com' WHERE id = 39;
UPDATE student_users SET phone = '13800138040', email = '20200040@example.com' WHERE id = 40;
UPDATE student_users SET phone = '13800138041', email = '20200041@example.com' WHERE id = 41;
UPDATE student_users SET phone = '13800138042', email = '20200042@example.com' WHERE id = 42;
UPDATE student_users SET phone = '13800138043', email = '20200043@example.com' WHERE id = 43;
UPDATE student_users SET phone = '13800138044', email = '20200044@example.com' WHERE id = 44;
UPDATE student_users SET phone = '13800138045', email = '20200045@example.com' WHERE id = 45;
UPDATE student_users SET phone = '13800138046', email = '20200046@example.com' WHERE id = 46;
UPDATE student_users SET phone = '13800138047', email = '20200047@example.com' WHERE id = 47;
UPDATE student_users SET phone = '13800138048', email = '20200048@example.com' WHERE id = 48;
UPDATE student_users SET phone = '13800138049', email = '20200049@example.com' WHERE id = 49;
UPDATE student_users SET phone = '13800138050', email = '20200050@example.com' WHERE id = 50;
UPDATE student_users SET phone = '13800138051', email = '20200051@example.com' WHERE id = 51;
UPDATE student_users SET phone = '13800138052', email = '20200052@example.com' WHERE id = 52;
UPDATE student_users SET phone = '13800138053', email = '20200053@example.com' WHERE id = 53;
UPDATE student_users SET phone = '13800138054', email = '20200054@example.com' WHERE id = 54;
UPDATE student_users SET phone = '13800138055', email = '20200055@example.com' WHERE id = 55;
UPDATE student_users SET phone = '13800138056', email = '20200056@example.com' WHERE id = 56;
UPDATE student_users SET phone = '13800138057', email = '20200057@example.com' WHERE id = 57;
UPDATE student_users SET phone = '13800138058', email = '20200058@example.com' WHERE id = 58;
UPDATE student_users SET phone = '13800138059', email = '20200059@example.com' WHERE id = 59;
UPDATE student_users SET phone = '13800138060', email = '20200060@example.com' WHERE id = 60;
UPDATE student_users SET phone = '13800138061', email = '20200061@example.com' WHERE id = 61;
UPDATE student_users SET phone = '13800138062', email = '20200062@example.com' WHERE id = 62;
UPDATE student_users SET phone = '13800138063', email = '20200063@example.com' WHERE id = 63;
UPDATE student_users SET phone = '13800138064', email = '20200064@example.com' WHERE id = 64;
UPDATE student_users SET phone = '13800138065', email = '20200065@example.com' WHERE id = 65;
UPDATE student_users SET phone = '13800138066', email = '20200066@example.com' WHERE id = 66;
UPDATE student_users SET phone = '13800138067', email = '20200067@example.com' WHERE id = 67;
UPDATE student_users SET phone = '13800138068', email = '20200068@example.com' WHERE id = 68;
UPDATE student_users SET phone = '13800138069', email = '20200069@example.com' WHERE id = 69;
UPDATE student_users SET phone = '13800138070', email = '20200070@example.com' WHERE id = 70;
UPDATE student_users SET phone = '13800138071', email = '20200071@example.com' WHERE id = 71;
UPDATE student_users SET phone = '13800138072', email = '20200072@example.com' WHERE id = 72;
UPDATE student_users SET phone = '13800138073', email = '20200073@example.com' WHERE id = 73;
UPDATE student_users SET phone = '13800138074', email = '20200074@example.com' WHERE id = 74;
UPDATE student_users SET phone = '13800138075', email = '20200075@example.com' WHERE id = 75;
UPDATE student_users SET phone = '13800138076', email = '20200076@example.com' WHERE id = 76;
UPDATE student_users SET phone = '13800138077', email = '20200077@example.com' WHERE id = 77;
UPDATE student_users SET phone = '13800138078', email = '20200078@example.com' WHERE id = 78;
UPDATE student_users SET phone = '13800138079', email = '20200079@example.com' WHERE id = 79;

-- ========================================
-- 第三步：验证数据
-- ========================================

-- 验证1：检查表结构
SELECT 
    '验证1: student_users表结构' as verification_info,
    COLUMN_NAME as column_name,
    COLUMN_TYPE as column_type,
    IS_NULLABLE as is_nullable,
    COLUMN_COMMENT as column_comment
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'internship' 
  AND TABLE_NAME = 'student_users'
  AND COLUMN_NAME IN ('phone', 'email')
ORDER BY ORDINAL_POSITION;

-- 验证2：检查学生联系电话数据（显示前10条）
SELECT 
    '验证2: 学生联系电话数据' as verification_info,
    id,
    name,
    phone,
    email
FROM student_users
ORDER BY id
LIMIT 10;

-- 验证3：检查实习申请表中的联系电话数据（显示前10条）
SELECT 
    '验证3: 实习申请表联系电话数据' as verification_info,
    ia.id,
    su.name as student_name,
    su.phone,
    su.email,
    p.position_name
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
LEFT JOIN position p ON ia.position_id = p.id
WHERE ia.company_id = 3
ORDER BY ia.apply_time DESC
LIMIT 10;

-- ========================================
-- 执行说明
-- ========================================

-- 1. 执行本脚本添加字段并填充数据
-- 2. 查看验证查询的结果，确认数据是否正确
-- 3. 刷新前端页面，检查联系电话是否正确显示

-- 注意事项：
-- - 使用存储过程检查字段是否存在，避免重复添加
-- - 为所有学生填充了测试用的联系电话和邮箱
-- - 联系电话格式：13800138001-13800138079
-- - 邮箱格式：20200001@example.com-20200079@example.com
