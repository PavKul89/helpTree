-- V43: make user_rating_stats a projection of confirmed facts.
--
-- Rating, achievements and statistics must describe the same set of actions:
--   * helps (ACCEPTED / COMPLETED / CONFIRMED / CANCELLED, not deleted)
--   * reviews (not deleted)
--
-- The columns below are added to the existing projection table so a user row
-- can expose all four help semantics separately:
--   * total_helps_given    - every non-deleted help record where the user helped
--   * successful_helps     - CONFIRMED help acts
--   * cancelled_helps      - CANCELLED help records
--   * active_helps_given   - ACCEPTED or COMPLETED help records
--   * total_helps_received - CONFIRMED help acts received by the user
--   * review_count / average_review_rating - active reviews received
--
-- Migration is forward-only: existing rows are backfilled from the facts and
-- marked stale (last_calculated = NULL) so the scheduler / next read rebuilds
-- them with the new formula.

ALTER TABLE user_rating_stats
    ADD COLUMN IF NOT EXISTS active_helps_given BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_rating_stats
    ADD COLUMN IF NOT EXISTS review_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_rating_stats
    ADD COLUMN IF NOT EXISTS average_review_rating DOUBLE PRECISION;

UPDATE user_rating_stats
SET active_helps_given = 0
WHERE active_helps_given IS NULL;

UPDATE user_rating_stats
SET review_count = 0
WHERE review_count IS NULL;

-- Backfill every existing projection row from the single source of truth.
UPDATE user_rating_stats s
SET total_helps_given = COALESCE((
        SELECT COUNT(*) FROM helps h
        WHERE h.helper_id = s.user_id
          AND (h.deleted = FALSE OR h.deleted IS NULL)
    ), 0),
    successful_helps = COALESCE((
        SELECT COUNT(*) FROM helps h
        WHERE h.helper_id = s.user_id
          AND h.status = 'CONFIRMED'
          AND (h.deleted = FALSE OR h.deleted IS NULL)
    ), 0),
    cancelled_helps = COALESCE((
        SELECT COUNT(*) FROM helps h
        WHERE h.helper_id = s.user_id
          AND h.status = 'CANCELLED'
          AND (h.deleted = FALSE OR h.deleted IS NULL)
    ), 0),
    active_helps_given = COALESCE((
        SELECT COUNT(*) FROM helps h
        WHERE h.helper_id = s.user_id
          AND h.status IN ('ACCEPTED', 'COMPLETED')
          AND (h.deleted = FALSE OR h.deleted IS NULL)
    ), 0),
    total_helps_received = COALESCE((
        SELECT COUNT(*) FROM helps h
        WHERE h.receiver_id = s.user_id
          AND h.status = 'CONFIRMED'
          AND (h.deleted = FALSE OR h.deleted IS NULL)
    ), 0),
    review_count = COALESCE((
        SELECT COUNT(*) FROM reviews r
        WHERE r.to_user_id = s.user_id
          AND (r.deleted = FALSE OR r.deleted IS NULL)
    ), 0),
    average_review_rating = (
        SELECT AVG(r.rating) FROM reviews r
        WHERE r.to_user_id = s.user_id
          AND (r.deleted = FALSE OR r.deleted IS NULL)
    ),
    last_calculated = NULL,
    updated_at = CURRENT_TIMESTAMP;

-- Aggregates used by the rating projection and the global statistics.
CREATE INDEX IF NOT EXISTS idx_helps_helper_status_active
    ON helps (helper_id, status)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_helps_receiver_status_active
    ON helps (receiver_id, status)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_helps_status_confirmed_at
    ON helps (status, confirmed_at)
    WHERE deleted = FALSE;

-- Admin review list filters: recipient, rating, created-at range.
CREATE INDEX IF NOT EXISTS idx_reviews_to_user_created
    ON reviews (to_user_id, created_at DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_reviews_rating_created
    ON reviews (rating, created_at DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_reviews_created_at
    ON reviews (created_at DESC)
    WHERE deleted = FALSE;

-- The scheduled recalculation must also pick up rows deliberately marked stale
-- by this migration (last_calculated is NULL).
CREATE INDEX IF NOT EXISTS idx_user_rating_stats_last_calculated
    ON user_rating_stats (last_calculated);
