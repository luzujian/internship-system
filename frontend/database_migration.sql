-- 数据库迁移脚本 - 企业端功能
-- 执行前请确认备份数据库

-- ========================================
-- 1. 为 position 表添加字段
-- ========================================

-- department
SET @columnname = 'department';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN department VARCHAR(100) COMMENT ''部门'' AFTER position_name'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- position_type
SET @columnname = 'position_type';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN position_type VARCHAR(50) COMMENT ''岗位类型'' AFTER department'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- province
SET @columnname = 'province';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN province VARCHAR(50) COMMENT ''省份'' AFTER position_type'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- city
SET @columnname = 'city';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN city VARCHAR(50) COMMENT ''城市'' AFTER province'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- district
SET @columnname = 'district';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN district VARCHAR(50) COMMENT ''区县'' AFTER city'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- detail_address
SET @columnname = 'detail_address';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN detail_address VARCHAR(255) COMMENT ''详细地址'' AFTER district'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- salary_min
SET @columnname = 'salary_min';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN salary_min DECIMAL(10,2) COMMENT ''最低薪资'' AFTER detail_address'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- salary_max
SET @columnname = 'salary_max';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN salary_max DECIMAL(10,2) COMMENT ''最高薪资'' AFTER salary_min'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- description
SET @columnname = 'description';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN description TEXT COMMENT ''岗位描述'' AFTER salary_max'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- internship_start_date
SET @columnname = 'internship_start_date';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN internship_start_date DATE COMMENT ''实习开始日期'' AFTER description'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- internship_end_date
SET @columnname = 'internship_end_date';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN internship_end_date DATE COMMENT ''实习结束日期'' AFTER internship_start_date'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- status
SET @columnname = 'status';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN status VARCHAR(20) DEFAULT ''active'' COMMENT ''状态：active 主动，paused 暂停'' AFTER internship_end_date'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- publish_date
SET @columnname = 'publish_date';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position ADD COLUMN publish_date DATETIME COMMENT ''发布日期'' AFTER status'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- 2. 为 company_users 表添加字段
-- ========================================

-- industry
SET @columnname = 'industry';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN industry VARCHAR(100) COMMENT ''行业'' AFTER company_tag'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- scale
SET @columnname = 'scale';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN scale VARCHAR(50) COMMENT ''公司规模'' AFTER industry'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- website
SET @columnname = 'website';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN website VARCHAR(255) COMMENT ''公司网站'' AFTER scale'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- logo
SET @columnname = 'logo';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN logo VARCHAR(500) COMMENT ''公司 Logo'' AFTER website'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- province
SET @columnname = 'province';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN province VARCHAR(50) COMMENT ''省份'' AFTER logo'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- city
SET @columnname = 'city';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN city VARCHAR(50) COMMENT ''城市'' AFTER province'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- district
SET @columnname = 'district';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN district VARCHAR(50) COMMENT ''区县'' AFTER city'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- detail_address
SET @columnname = 'detail_address';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN detail_address VARCHAR(255) COMMENT ''详细地址'' AFTER district'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- cooperation_mode
SET @columnname = 'cooperation_mode';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN cooperation_mode VARCHAR(50) COMMENT ''合作模式'' AFTER detail_address'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- description
SET @columnname = 'description';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN description TEXT COMMENT ''企业描述'' AFTER cooperation_mode'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- photos
SET @columnname = 'photos';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN photos TEXT COMMENT ''企业照片'' AFTER description'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- videos
SET @columnname = 'videos';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'company_users')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE company_users ADD COLUMN videos TEXT COMMENT ''企业视频'' AFTER photos'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- 3. 为 student_internship_status 表添加字段（检查是否已存在）
-- ========================================

-- 这些字段可能已经存在，检查一下
SET @columnname = 'internship_start_time';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'student_internship_status')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE student_internship_status ADD COLUMN internship_start_time DATETIME COMMENT ''实习开始时间'' AFTER position_id'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'internship_end_time';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'student_internship_status')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE student_internship_status ADD COLUMN internship_end_time DATETIME COMMENT ''实习结束时间'' AFTER internship_start_time'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'internship_duration';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'student_internship_status')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE student_internship_status ADD COLUMN internship_duration INT COMMENT ''实习时长（天）'' AFTER internship_end_time'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'feedback';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'student_internship_status')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE student_internship_status ADD COLUMN feedback TEXT COMMENT ''企业反馈'' AFTER internship_duration'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'remark';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'student_internship_status')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE student_internship_status ADD COLUMN remark TEXT COMMENT ''备注'' AFTER feedback'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- 4. 创建 internship_application 表（如果不存在）
-- ========================================

CREATE TABLE IF NOT EXISTS internship_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    student_user_id VARCHAR(50) NOT NULL,
    position_id BIGINT NOT NULL,
    position_name VARCHAR(100) NOT NULL,
    company_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    viewed TINYINT(1) DEFAULT 0,
    viewed_time DATETIME,
    phone VARCHAR(20),
    email VARCHAR(100),
    gender VARCHAR(10),
    grade VARCHAR(20),
    school VARCHAR(255),
    education VARCHAR(50),
    major VARCHAR(100),
    skills JSON,
    experience TEXT,
    self_evaluation TEXT,
    apply_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_company_id (company_id),
    INDEX idx_position_id (position_id),
    INDEX idx_student_id (student_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习申请表';

-- ========================================
-- 5. 初始化 position_category 表数据（如果不存在）
-- ========================================

-- 检查 sort_order 字段是否存在，不存在则添加
SET @columnname = 'sort_order';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = 'position_category')
      AND (table_schema = DATABASE())
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE position_category ADD COLUMN sort_order INT DEFAULT 0 AFTER description'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 初始化岗位类别数据（如果不存在）
INSERT IGNORE INTO position_category (name, description, sort_order) VALUES
('技术/研发', '包括软件开发、测试、运维等技术岗位', 1),
('产品/运营', '包括产品经理、运营专员等岗位', 2),
('设计/创意', '包括 UI 设计、平面设计、创意设计等岗位', 3),
('市场/销售', '包括市场推广、销售代表、客户经理等岗位', 4),
('人力/行政', '包括人力资源、行政管理、前台等岗位', 5),
('财务/金融', '包括会计、出纳、金融分析等岗位', 6),
('传媒/影视', '包括编辑、记者、影视制作等岗位', 7),
('教育/培训', '包括教师、培训师、课程顾问等岗位', 8),
('医疗/护理', '包括医生、护士、药剂师等岗位', 9),
('其他', '其他类别岗位', 10);

-- ========================================
-- 迁移完成
-- ========================================
