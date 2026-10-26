-- 管理员「辣子鸡」确认后手动执行；应用启动与业务账号均不执行 DDL。
-- 历史待审核申请保持 COUNSELOR；仅新增/重新提交时按有效企业档案路由。
ALTER TABLE student_application
    ADD COLUMN review_target VARCHAR(16) NOT NULL DEFAULT 'COUNSELOR' COMMENT 'COUNSELOR/COMPANY',
    ADD COLUMN company_id BIGINT NULL,
    ADD COLUMN position_id BIGINT NULL,
    ADD COLUMN confirmation_record_id BIGINT NULL,
    ADD INDEX idx_application_confirmation (confirmation_record_id);

-- 每次提交保留申请及协议版本快照，防止重提更换单位后历史企业读取新材料。
ALTER TABLE internship_confirmation_record
    ADD COLUMN source_application_id BIGINT NULL,
    ADD COLUMN agreement_submission_id BIGINT NULL,
    ADD COLUMN application_snapshot LONGTEXT NULL,
    ADD INDEX idx_confirmation_source_application (source_application_id);
