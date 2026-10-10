-- 管理员确认后手动执行，不由应用启动自动执行。
CREATE TABLE IF NOT EXISTS internship_agreement_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    version INT NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    position_name VARCHAR(255) DEFAULT NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agreement_source_version (source_type, source_id, version),
    KEY idx_agreement_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS internship_agreement_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    file_upload_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agreement_submission_file (submission_id, file_upload_id),
    KEY idx_agreement_upload (file_upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
