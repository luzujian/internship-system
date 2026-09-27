-- Review and execute only after the administrator explicitly authorizes DDL.
-- Additive migration: preserve existing task/send history. No automatic startup DDL.
CREATE TABLE IF NOT EXISTS agent_notification_guard (
  dedupe_key CHAR(64) NOT NULL PRIMARY KEY,
  student_id BIGINT NOT NULL,
  todo_id VARCHAR(240) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  state VARCHAR(16) NOT NULL,
  claim_token CHAR(36) NULL,
  task_id BIGINT NULL,
  send_log_id BIGINT NULL,
  last_success_at DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL,
  INDEX idx_notification_state (state,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Check information_schema.COLUMNS first; run each ALTER only if the column is missing.
ALTER TABLE teacher_auto_task ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE teacher_auto_task_send_log ADD COLUMN todo_id VARCHAR(240) NULL;
ALTER TABLE teacher_auto_task_send_log ADD COLUMN run_id CHAR(36) NULL;
-- Roll back application features, keeping these additive tables/columns and all receipts.
-- Role policies and explicit per-user changes must not be truncated to the old 500-character limit.
ALTER TABLE system_config MODIFY COLUMN config_value TEXT;
