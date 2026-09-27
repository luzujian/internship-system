-- Approved local migration only. Execute through the administrator migration procedure.
-- Existing task/send rows are retained. Run receipts have no cascading business deletion.
CREATE TABLE IF NOT EXISTS teacher_auto_task_run (
  id CHAR(36) NOT NULL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  occurrence_key VARCHAR(180) NOT NULL,
  task_version BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  started_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  receipt JSON NULL,
  error_message VARCHAR(1000) NULL,
  UNIQUE KEY uk_teacher_task_occurrence (task_id, occurrence_key),
  KEY idx_teacher_task_run_status (task_id, status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
