-- Manual migration, administrator approval required under AGENTS.md.
-- AnnouncementServiceImpl keeps publish_time NULL until a draft is published.
-- This preserves all existing timestamps and changes no announcement contents.
ALTER TABLE announcements
    MODIFY COLUMN publish_time DATETIME NULL DEFAULT NULL
    COMMENT 'Publication time; NULL until first publication';
