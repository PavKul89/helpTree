-- V54: centralized notification domain (Block 17).
--
-- V42 created two objects that Block 17 needs to evolve:
--   * `notification_preferences` is a per-user, account-wide settings row
--     (email/push/in-app/digest/quiet hours) owned by the Block 14 account API;
--   * `notifications.data` stores the event payload.
--
-- The new notification model needs per-type/per-channel preferences and a
-- canonical `payload_json` column, so the legacy account settings table is
-- renamed (never dropped) and a new notification_preferences table is created.
-- Existing settings stay readable through /api/account/notifications.
--
-- Every statement is additive or a rename; no row is deleted.

-- ============================================================
-- 1. LEGACY ACCOUNT-WIDE SETTINGS
-- ============================================================
ALTER TABLE notification_preferences RENAME TO account_notification_preferences;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class
        WHERE relname = 'notification_preferences_pkey'
          AND relkind = 'i'
    ) THEN
        ALTER INDEX notification_preferences_pkey
            RENAME TO account_notification_preferences_pkey;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_notification_preferences_digest'
    ) THEN
        ALTER TABLE account_notification_preferences
            RENAME CONSTRAINT chk_notification_preferences_digest
            TO chk_account_notification_preferences_digest;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_notification_preferences_quiet_hours'
    ) THEN
        ALTER TABLE account_notification_preferences
            RENAME CONSTRAINT chk_notification_preferences_quiet_hours
            TO chk_account_notification_preferences_quiet_hours;
    END IF;
END $$;

-- ============================================================
-- 2. PER-TYPE / PER-CHANNEL PREFERENCES
-- ============================================================
CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_preferences_user_type_channel
        UNIQUE (user_id, type, channel),
    CONSTRAINT chk_notification_preferences_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'TELEGRAM')),
    CONSTRAINT chk_notification_preferences_type_not_blank
        CHECK (length(btrim(type)) > 0)
);

-- Reading the effective setting for one event always filters by all three
-- columns; the unique constraint already provides that index. This additional
-- index keeps "all preferences of a user" a single range scan for the settings
-- screen and the effective-preferences response.
CREATE INDEX idx_notification_preferences_user
    ON notification_preferences (user_id, type, channel);

-- ============================================================
-- 3. CANONICAL NOTIFICATION PAYLOAD
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'data'
    ) THEN
        ALTER TABLE notifications RENAME COLUMN data TO payload_json;
    END IF;
END $$;

-- ============================================================
-- 4. OUTBOX GUARANTEES
-- ============================================================
-- The V42 outbox table already has status/attempts/available_at/locked_* and a
-- dedupe index. The scheduler claims by (status, available_at, id); extending
-- the dispatch index with the id makes that order index-only.
DROP INDEX IF EXISTS idx_outbox_events_dispatch;
CREATE INDEX idx_outbox_events_dispatch
    ON outbox_events (status, available_at, id);
