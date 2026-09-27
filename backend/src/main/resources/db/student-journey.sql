-- Additive migration. Read the actual schema before applying with administrator authorization.
-- Rollback: restore the prior application release and retain this table and its data.
-- No existing internship, chat, or application records are modified.
CREATE TABLE IF NOT EXISTS student_journey_preference (
  student_id BIGINT NOT NULL PRIMARY KEY,
  route_choice VARCHAR(16) NULL,
  seen BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
