-- V50: account privacy settings and the irreversible anonymization marker.
--
-- This migration is additive. Existing users keep their current visibility:
-- REGISTERED means a profile is visible to authenticated community members,
-- which matches the pre-V50 behaviour. anonymized_at is a terminal marker: once
-- set, the row no longer contains personal data even if it is still referenced
-- by historical helps, posts or audit records.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_users_anonymized_at
    ON users (anonymized_at)
    WHERE anonymized_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_deleted_at
    ON users (deleted_at DESC)
    WHERE deleted = TRUE;

CREATE TABLE IF NOT EXISTS user_privacy_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    profile_visibility VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    show_city BOOLEAN NOT NULL DEFAULT TRUE,
    show_email BOOLEAN NOT NULL DEFAULT FALSE,
    show_phone BOOLEAN NOT NULL DEFAULT FALSE,
    allow_direct_messages BOOLEAN NOT NULL DEFAULT TRUE,
    searchable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_privacy_settings_visibility CHECK (
        profile_visibility IN ('PUBLIC', 'REGISTERED', 'PRIVATE')
    )
);

CREATE INDEX IF NOT EXISTS idx_user_privacy_settings_visibility
    ON user_privacy_settings (profile_visibility, searchable);
