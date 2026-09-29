-- Manual migration: do not execute without administrator approval under AGENTS.md.
-- Apply before deploying the announcement ownership source change.
-- Existing rows deliberately remain NULL: names cannot prove historical ownership.
-- Legacy rows retain normal audience visibility and administrator management.
-- Do not backfill from a name match, including a currently unique name.
ALTER TABLE announcements
    ADD COLUMN publisher_user_id BIGINT NULL COMMENT 'Immutable creator account ID',
    ADD COLUMN publisher_user_type VARCHAR(16) NULL COMMENT 'Creator account namespace: ADMIN or TEACHER';
-- Rollback code before removing these columns; retain them to preserve attribution.
