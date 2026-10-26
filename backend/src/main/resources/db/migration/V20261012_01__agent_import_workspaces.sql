-- Durable private import workspaces. Administrator runs this migration explicitly;
-- application startup never executes DDL. The business account needs CRUD only.
CREATE TABLE IF NOT EXISTS agent_import_workspace (
 id CHAR(36) PRIMARY KEY,
 owner_key VARCHAR(180) NOT NULL,
 session_id CHAR(36) NOT NULL,
 source_ref CHAR(36) NOT NULL,
 source_sha256 CHAR(64) NOT NULL,
 -- Keep canonical bytes: MySQL JSON rendering can reorder keys/change whitespace and invalidate a raw SHA.
 source_json MEDIUMTEXT NOT NULL,
 files_json JSON NOT NULL,
 state_json JSON NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at BIGINT NOT NULL,
 updated_at BIGINT NOT NULL,
 lease_token CHAR(36) NULL,
 lease_turn_id VARCHAR(100) NULL,
 lease_until BIGINT NOT NULL DEFAULT 0,
 cancelled_turn_id VARCHAR(100) NULL,
 INDEX idx_import_workspace_owner_session (owner_key, session_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
