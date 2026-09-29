-- DeepIntern durable history/actions. Review and run only after explicit administrator approval.
-- This migration is never executed by application startup. No credentials belong in this file.
CREATE TABLE IF NOT EXISTS agent_session (
 id CHAR(36) PRIMARY KEY, owner_key VARCHAR(180) NOT NULL, title VARCHAR(60) NULL,
 archived BOOLEAN NOT NULL DEFAULT FALSE, updated_at BIGINT NOT NULL,
 snapshot JSON NOT NULL, INDEX idx_agent_session_owner (owner_key, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS agent_task (
 id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL, owner_key VARCHAR(180) NOT NULL,
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(24) NOT NULL, cancelled BOOLEAN NOT NULL DEFAULT FALSE, snapshot JSON NOT NULL,
 INDEX idx_agent_task_session (session_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS agent_request (
 owner_key VARCHAR(180) NOT NULL, request_id VARCHAR(100) NOT NULL, task_id CHAR(36) NOT NULL,
 created_at BIGINT NOT NULL, PRIMARY KEY (owner_key, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS agent_draft (
 owner_key VARCHAR(180) NOT NULL, draft_key VARCHAR(36) NOT NULL, text TEXT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL,
 PRIMARY KEY (owner_key, draft_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS agent_session_event (
 id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL, owner_key VARCHAR(180) NOT NULL,
 kind VARCHAR(24) NOT NULL, created_at BIGINT NOT NULL, snapshot JSON NOT NULL,
 INDEX idx_agent_event_session (session_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS agent_action (
 id CHAR(36) PRIMARY KEY, owner_key VARCHAR(180) NOT NULL, action_type VARCHAR(64) NOT NULL,
 target_key VARCHAR(180) NOT NULL, parameters JSON NOT NULL, parameters_hash CHAR(64) NOT NULL,
 expected_version VARCHAR(180) NULL, status VARCHAR(24) NOT NULL,
 previewed_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
 receipt JSON NULL, error_code VARCHAR(64) NULL, error_message VARCHAR(500) NULL,
 INDEX idx_agent_action_owner (owner_key, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
