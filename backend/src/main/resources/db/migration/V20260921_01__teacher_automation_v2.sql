-- Local-first additive migration. Back up task, run, send, guard and capability rows before applying.
-- Rollback: pause engine_version=2 tasks, cancel QUEUED/CLAIMED deliveries, deploy the compatibility
-- release. Keep all tables/columns and receipts. Do not DROP tables or replay completed sends.
ALTER TABLE teacher_auto_task
  ADD COLUMN engine_version INT NOT NULL DEFAULT 1,
  ADD COLUMN definition JSON NULL,
  ADD COLUMN activated_at DATETIME(3) NULL,
  ADD COLUMN next_run_at DATETIME(3) NULL,
  ADD COLUMN deleted_at DATETIME(3) NULL,
  ADD INDEX idx_automation_due (engine_version, status, next_run_at);

CREATE TABLE teacher_auto_task_draft (
 id CHAR(36) PRIMARY KEY, teacher_id BIGINT NOT NULL, teacher_type VARCHAR(32) NOT NULL,
 session_id VARCHAR(100) NOT NULL, revision BIGINT NOT NULL, source_text TEXT NOT NULL,
 definition JSON NOT NULL, issues JSON NOT NULL, updated_at DATETIME(3) NOT NULL,
 INDEX idx_automation_draft_owner (teacher_id, teacher_type, session_id, updated_at)
);
CREATE TABLE teacher_auto_task_revision (
 task_id BIGINT NOT NULL, version BIGINT NOT NULL, definition JSON NOT NULL,
 created_at DATETIME(3) NOT NULL, PRIMARY KEY (task_id, version)
);
CREATE TABLE teacher_auto_task_dispatch (
 run_id CHAR(36) PRIMARY KEY, task_id BIGINT NOT NULL, task_version BIGINT NOT NULL,
 occurrence_key VARCHAR(180) NOT NULL, definition JSON NOT NULL, audience JSON NULL,
 cursor_id BIGINT NOT NULL DEFAULT 0, enumerated TINYINT NOT NULL DEFAULT 0,
 valid_until DATETIME(3) NOT NULL, lease_token CHAR(36) NULL, lease_until DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL, UNIQUE KEY uq_dispatch_occurrence (task_id, occurrence_key),
 INDEX idx_dispatch_lease (enumerated, lease_until)
);
CREATE TABLE teacher_auto_task_delivery (
 id CHAR(36) PRIMARY KEY, run_id CHAR(36) NOT NULL, task_id BIGINT NOT NULL,
 task_version BIGINT NOT NULL, student_id BIGINT NOT NULL, student_name VARCHAR(100) NOT NULL,
 channel VARCHAR(32) NOT NULL, subject VARCHAR(180) NOT NULL, body TEXT NOT NULL,
 matter_key VARCHAR(240) NOT NULL, status VARCHAR(24) NOT NULL, attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(3) NOT NULL, lease_token CHAR(36) NULL, lease_until DATETIME(3) NULL,
 guard_token CHAR(36) NULL, detail VARCHAR(1000) NULL, evidence TEXT NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_delivery_recipient (run_id, student_id, channel),
 INDEX idx_delivery_ready (status, next_attempt_at),
 INDEX idx_delivery_task (task_id, created_at),
 INDEX idx_delivery_once (task_id, student_id, matter_key, channel, status)
);
CREATE TABLE teacher_auto_task_match (
 task_id BIGINT NOT NULL, student_id BIGINT NOT NULL, active TINYINT NOT NULL,
 episode BIGINT NOT NULL DEFAULT 1, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY (task_id, student_id)
);
