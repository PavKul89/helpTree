-- V47: Block 10 — performance indexes for the activity feed, help graph and
-- rating-history retention.
--
-- This migration is forward-only and adds indexes only. No row is rewritten and
-- no data is deleted; the cleanup itself runs at runtime in bounded batches
-- (see RatingHistoryCleanupService), because a one-off DELETE inside a Flyway
-- migration would hold a long lock and grow the WAL without a bound.
--
-- Every index is partial on `COALESCE(deleted, FALSE) = FALSE` to match the
-- predicates used by the application (legacy rows can have deleted = NULL) and
-- to keep soft-deleted rows out of the index entirely.

-- === Activity feed ========================================================
-- HELP_GIVEN / HELP_RECEIVED: filter by participant, order by the same
-- COALESCE(...) timestamp the UNION projects. The id tiebreaker keeps
-- deep pagination deterministic.
CREATE INDEX IF NOT EXISTS idx_helps_helper_activity
    ON helps (helper_id,
              COALESCE(confirmed_at, completed_at, accepted_at, created_at) DESC,
              id DESC)
    WHERE COALESCE(deleted, FALSE) = FALSE;

CREATE INDEX IF NOT EXISTS idx_helps_receiver_activity
    ON helps (receiver_id,
              COALESCE(confirmed_at, completed_at, accepted_at, created_at) DESC,
              id DESC)
    WHERE COALESCE(deleted, FALSE) = FALSE;

-- POST_CREATED: one author's newest posts.
CREATE INDEX IF NOT EXISTS idx_posts_user_activity
    ON posts (user_id, created_at DESC, id DESC)
    WHERE COALESCE(deleted, FALSE) = FALSE;

-- ACHIEVEMENT_EARNED: one user's achievements, newest first. The existing
-- unique (user_id, type) index cannot serve this ordering.
CREATE INDEX IF NOT EXISTS idx_achievements_user_earned
    ON achievements (user_id, earned_at DESC)
    WHERE earned_at IS NOT NULL;

-- === Help graph ===========================================================
-- The recursive CTE walks helper_id -> receiver_id over confirmed, non-deleted
-- help acts. The composite edge index serves both the edge list and the
-- existence checks; the existing receiver_status_active index still covers
-- receiver-only lookups.
CREATE INDEX IF NOT EXISTS idx_helps_confirmed_edges
    ON helps (helper_id, receiver_id, id)
    WHERE status = 'CONFIRMED'
      AND COALESCE(deleted, FALSE) = FALSE;

-- Global graph and count queries order the confirmed component by confirmation
-- time. V43 created (status, confirmed_at) without an id tiebreaker.
CREATE INDEX IF NOT EXISTS idx_helps_confirmed_order
    ON helps (status, confirmed_at ASC, id ASC)
    WHERE COALESCE(deleted, FALSE) = FALSE;

-- === Rating history retention ============================================
-- V30/V33 already index calculated_at, which is enough for the ordered
-- LIMIT subquery. This covering variant lets the DELETE pick ids without a heap
-- fetch when the planner prefers an index-only scan.
CREATE INDEX IF NOT EXISTS idx_rating_history_retention
    ON rating_history (calculated_at ASC, id ASC);
