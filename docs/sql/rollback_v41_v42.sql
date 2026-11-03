-- Emergency rollback of V41/V42 to the V40 schema shape.
--
-- Use only after:
--   1. a fresh backup of the current database;
--   2. a successful rehearsal on a copy.
--
-- This script does NOT reverse the documented data normalization
-- (rating 0 -> 3.0, debt_count NULL -> 0). Restore the pre-migration backup
-- when exact historical values are required.
--
-- Run with: psql -v ON_ERROR_STOP=1 -f docs/sql/rollback_v41_v42.sql

BEGIN;

-- V42 tables (child tables first).
DROP TABLE IF EXISTS report_events;
DROP TABLE IF EXISTS moderation_actions;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS user_blocks;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS notification_preferences;
DROP TABLE IF EXISTS admin_audit_log;
DROP TABLE IF EXISTS outbox_events;

ALTER TABLE chat_user_state
    DROP CONSTRAINT IF EXISTS fk_chat_user_state_last_read;

-- V41 constraints.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_rating_range;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_debt_count_non_negative;
ALTER TABLE chats DROP CONSTRAINT IF EXISTS chk_chats_distinct_users;
ALTER TABLE chats DROP CONSTRAINT IF EXISTS chk_chats_canonical_order;

-- Restore V40 nullability/defaults. Values remain normalized.
ALTER TABLE users ALTER COLUMN rating DROP NOT NULL;
ALTER TABLE users ALTER COLUMN rating SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN debt_count DROP NOT NULL;
ALTER TABLE users ALTER COLUMN debt_count SET DEFAULT 0;

-- V41 indexes.
DROP INDEX IF EXISTS idx_users_status;
DROP INDEX IF EXISTS idx_users_city;
DROP INDEX IF EXISTS idx_posts_author_name;
DROP INDEX IF EXISTS idx_comments_created_at;
DROP INDEX IF EXISTS idx_messages_created;
DROP INDEX IF EXISTS idx_posts_deleted;
DROP INDEX IF EXISTS idx_helps_deleted;
DROP INDEX IF EXISTS idx_messages_deleted_at;
DROP INDEX IF EXISTS uq_users_email_normalized;

-- Restore the V38 achievement index name.
DROP INDEX IF EXISTS idx_achievements_user_type;
CREATE UNIQUE INDEX IF NOT EXISTS uq_achievements_user_type
    ON achievements (user_id, type);

-- Restore the V38 active-help definition (without CONFIRMED).
DROP INDEX IF EXISTS uq_helps_active_post;
CREATE UNIQUE INDEX uq_helps_active_post
    ON helps (post_id)
    WHERE status IN ('ACCEPTED', 'COMPLETED')
      AND deleted = FALSE;

-- Remove the applied migration history rows last.
DELETE FROM flyway_schema_history WHERE version IN ('41', '42');

COMMIT;
