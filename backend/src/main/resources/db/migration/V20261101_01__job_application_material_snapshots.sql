-- 管理员确认后手动执行，不由应用启动自动执行。业务账号仍仅使用 CRUD 权限。
-- 不回填历史申请：当前材料不能代表历史投递时的版本。
CREATE TABLE IF NOT EXISTS job_application_material_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_application_id BIGINT NOT NULL,
    company_application_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    submitted_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_material_application (job_application_id),
    UNIQUE KEY uk_job_material_company_application (company_application_id),
    KEY idx_job_material_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_application_material_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL COMMENT 'resume/certificate',
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_material_submission (submission_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
