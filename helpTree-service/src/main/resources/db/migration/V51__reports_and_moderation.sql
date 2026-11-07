-- V51: complete the reports / moderation domain.
--
-- V42 created the base tables. This migration aligns them with the product
-- model documented in docs/REPORTS_MODERATION.md:
--   * a report has a public description and a staff-only internal note;
--   * a moderator can link a duplicate to the original report;
--   * report creation is idempotent per reporter;
--   * REVIEW becomes a supported target type;
--   * content gets an explicit moderation status that is independent from the
--     author's soft-delete flag.
--
-- Every statement is additive or a rename. No row is deleted.

-- ============================================================
-- 1. REPORTS
-- ============================================================

ALTER TABLE reports RENAME COLUMN details TO description;
ALTER TABLE reports RENAME COLUMN assigned_to_id TO assigned_moderator_id;

ALTER TABLE reports ADD COLUMN IF NOT EXISTS internal_note TEXT NULL;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS duplicate_of_id BIGINT NULL
    REFERENCES reports(id) ON DELETE SET NULL;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) NULL;

-- The V42 target check predates REVIEW. Keep HELP/CHAT in the allowed list so
-- historical reports remain readable, but the service no longer creates them.
ALTER TABLE reports DROP CONSTRAINT IF EXISTS chk_reports_target_type;
ALTER TABLE reports ADD CONSTRAINT chk_reports_target_type CHECK (
    target_type IN ('POST', 'USER', 'COMMENT', 'REVIEW', 'MESSAGE', 'HELP', 'CHAT')
);

ALTER TABLE reports DROP CONSTRAINT IF EXISTS chk_reports_description_length;
ALTER TABLE reports ADD CONSTRAINT chk_reports_description_length CHECK (
    description IS NULL OR length(description) <= 2000
);

ALTER TABLE reports DROP CONSTRAINT IF EXISTS chk_reports_idempotency_key;
ALTER TABLE reports ADD CONSTRAINT chk_reports_idempotency_key CHECK (
    idempotency_key IS NULL OR length(btrim(idempotency_key)) BETWEEN 1 AND 100
);

-- A reporter retrying the same request must get the first report back. The
-- key is scoped to the reporter, so two users may use the same client value.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reports_reporter_idempotency
    ON reports (reporter_id, idempotency_key)
    WHERE reporter_id IS NOT NULL
      AND idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reports_duplicate_of
    ON reports (duplicate_of_id)
    WHERE duplicate_of_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reports_reason
    ON reports (reason, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reports_priority_created
    ON reports (priority, created_at DESC)
    WHERE status IN ('OPEN', 'IN_REVIEW');

-- ============================================================
-- 2. REPORT EVENTS (APPEND-ONLY)
-- ============================================================

ALTER TABLE report_events RENAME COLUMN note TO internal_note;

ALTER TABLE report_events ADD COLUMN IF NOT EXISTS internal_note TEXT NULL;

ALTER TABLE report_events DROP CONSTRAINT IF EXISTS chk_report_events_type;
ALTER TABLE report_events ADD CONSTRAINT chk_report_events_type CHECK (
    event_type IN ('CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'NOTE',
                   'ESCALATED', 'RESOLVED', 'REOPENED', 'LINKED_DUPLICATE',
                   'ACTION_APPLIED')
);

-- ============================================================
-- 3. MODERATION ACTIONS (APPEND-ONLY)
-- ============================================================

ALTER TABLE moderation_actions DROP CONSTRAINT IF EXISTS chk_moderation_actions_target_type;
ALTER TABLE moderation_actions ADD CONSTRAINT chk_moderation_actions_target_type CHECK (
    target_type IN ('POST', 'USER', 'COMMENT', 'REVIEW', 'MESSAGE', 'HELP', 'CHAT', 'REPORT')
);

ALTER TABLE moderation_actions DROP CONSTRAINT IF EXISTS chk_moderation_actions_type;
ALTER TABLE moderation_actions ADD CONSTRAINT chk_moderation_actions_type CHECK (
    action_type IN ('WARN', 'MUTE', 'UNMUTE', 'BAN', 'UNBAN',
                    'DELETE_CONTENT', 'HIDE_CONTENT', 'REMOVE_CONTENT',
                    'RESTORE_CONTENT', 'RESOLVE_REPORT', 'DISMISS_REPORT',
                    'LINK_DUPLICATE', 'ESCALATE', 'OTHER')
);

-- ============================================================
-- 4. CONTENT MODERATION STATUS
-- ============================================================
--
-- The author's soft-delete stays in `deleted`; the moderator's decision lives
-- in `moderation_status`. This keeps three cases distinguishable:
--   * author deleted the content          -> deleted = true
--   * moderator hid it temporarily        -> moderation_status = 'HIDDEN'
--   * moderator removed it permanently    -> moderation_status = 'REMOVED'

ALTER TABLE posts ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE comments ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_posts_moderation_status') THEN
        ALTER TABLE posts ADD CONSTRAINT chk_posts_moderation_status
            CHECK (moderation_status IN ('ACTIVE', 'HIDDEN', 'REMOVED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_comments_moderation_status') THEN
        ALTER TABLE comments ADD CONSTRAINT chk_comments_moderation_status
            CHECK (moderation_status IN ('ACTIVE', 'HIDDEN', 'REMOVED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_reviews_moderation_status') THEN
        ALTER TABLE reviews ADD CONSTRAINT chk_reviews_moderation_status
            CHECK (moderation_status IN ('ACTIVE', 'HIDDEN', 'REMOVED'));
    END IF;
END $$;

-- Public feeds always filter by ACTIVE. Partial indexes keep those scans small
-- even when the archive grows.
CREATE INDEX IF NOT EXISTS idx_posts_moderation_status
    ON posts (moderation_status);

CREATE INDEX IF NOT EXISTS idx_posts_public_feed
    ON posts (created_at DESC)
    WHERE moderation_status = 'ACTIVE' AND COALESCE(deleted, FALSE) = FALSE;

CREATE INDEX IF NOT EXISTS idx_comments_moderation_status
    ON comments (moderation_status);

CREATE INDEX IF NOT EXISTS idx_comments_public_thread
    ON comments (post_id, created_at)
    WHERE moderation_status = 'ACTIVE' AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_reviews_moderation_status
    ON reviews (moderation_status);

CREATE INDEX IF NOT EXISTS idx_reviews_public_recipient
    ON reviews (to_user_id, created_at DESC)
    WHERE moderation_status = 'ACTIVE' AND deleted = FALSE;

-- ============================================================
-- 5. USER BLOCK HELPERS
-- ============================================================
-- user_blocks already exists from V42. This partial index makes the "is this
-- user actively blocked by moderation?" check a single index lookup.
CREATE INDEX IF NOT EXISTS idx_user_blocks_active_moderation
    ON user_blocks (user_id, ends_at)
    WHERE lifted_at IS NULL AND source = 'MODERATION';
