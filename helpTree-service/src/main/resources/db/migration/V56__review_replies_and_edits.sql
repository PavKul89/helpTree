-- V56: Block 21.1 — review replies and author edits.
--
-- A review can be answered exactly once by its recipient. The reply stays
-- editable for 24 hours. The author may also edit the rating/comment for 24
-- hours after creation; every edit recalculates the recipient rating. Both
-- windows are enforced server-side, these columns only persist the state.
--
-- Every statement is additive and idempotent.

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS reply_text TEXT NULL;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS reply_at TIMESTAMP NULL;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS reply_updated_at TIMESTAMP NULL;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP NULL;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_reviews_edit_count'
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT chk_reviews_edit_count CHECK (edit_count >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_reviews_reply_state'
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT chk_reviews_reply_state CHECK (
                (reply_text IS NULL AND reply_at IS NULL AND reply_updated_at IS NULL)
                OR (reply_text IS NOT NULL AND reply_at IS NOT NULL)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_reviews_reply_not_blank'
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT chk_reviews_reply_not_blank CHECK (
                reply_text IS NULL OR length(btrim(reply_text)) > 0
            );
    END IF;
END $$;

-- "Unanswered reviews" and the recipient inbox filter by recipient and reply
-- state, newest first. The existing public recipient index is not partial on
-- reply state, so this one keeps the reply queue index-only.
CREATE INDEX IF NOT EXISTS idx_reviews_recipient_unanswered
    ON reviews (to_user_id, created_at DESC)
    WHERE reply_text IS NULL AND deleted = FALSE;

-- The edit window is checked against created_at; this partial index makes the
-- author's "recent reviews" lookup cheap when a review is edited.
CREATE INDEX IF NOT EXISTS idx_reviews_author_recent
    ON reviews (from_user_id, created_at DESC)
    WHERE deleted = FALSE;
