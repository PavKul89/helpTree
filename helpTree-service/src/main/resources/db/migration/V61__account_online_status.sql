-- V61: Block 21.6 — online status privacy and last activity.
--
-- last_seen_at is touched by the access-token validation path, throttled so a
-- busy client does not write on every request. A user is considered online when
-- the value is within the online window (5 minutes by default). The profile
-- shows it only when show_online_status is enabled.
--
-- Additive and idempotent.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NULL;

ALTER TABLE user_privacy_settings
    ADD COLUMN IF NOT EXISTS show_online_status BOOLEAN NOT NULL DEFAULT TRUE;

-- Public profile lookups read only recent activity; a partial index keeps the
-- "who is online" count cheap without indexing the whole users table.
CREATE INDEX IF NOT EXISTS idx_users_last_seen_at
    ON users (last_seen_at DESC)
    WHERE last_seen_at IS NOT NULL;
