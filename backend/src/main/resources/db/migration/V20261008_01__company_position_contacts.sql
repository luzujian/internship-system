-- 已获管理员确认；使用 root 执行结构调整，业务写入仍使用 user。
CREATE TABLE IF NOT EXISTS company_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(100) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_contact (company_id, name, phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @contact_column_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'position' AND column_name = 'contact_id');
SET @contact_ddl = IF(@contact_column_exists = 0,
    'ALTER TABLE position ADD COLUMN contact_id BIGINT NULL COMMENT ''岗位联系人，NULL使用企业原联系人'', ADD INDEX idx_position_contact (contact_id)',
    'SELECT 1');
PREPARE company_contact_ddl FROM @contact_ddl;
EXECUTE company_contact_ddl;
DEALLOCATE PREPARE company_contact_ddl;

-- 业务账号另行执行：保留企业原联系人，也供新岗位选择。
-- INSERT INTO company_contact (company_id, name, phone, email)
-- SELECT id, TRIM(contact_person), TRIM(contact_phone), COALESCE(contact_email, '') FROM company_users
-- WHERE TRIM(contact_person) != '' AND TRIM(contact_phone) != ''
-- ON DUPLICATE KEY UPDATE id = company_contact.id;
