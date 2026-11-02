-- V35: Prevent repeated coin accruals

-- Soft-delete for reviews: keep the unique (help_id, from_user_id) row
-- so the same review (and its REVIEW_BONUS) cannot be created twice.
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_reviews_deleted ON reviews(deleted);

-- Speeds up the 24-hour daily bonus check.
CREATE INDEX IF NOT EXISTS idx_coin_user_type_created
    ON coin_transactions(user_id, type, created_at DESC);
