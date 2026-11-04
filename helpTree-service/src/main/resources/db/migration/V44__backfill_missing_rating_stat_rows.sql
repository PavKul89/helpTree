-- V44: backfill user_rating_stats rows that V43 did not create.
--
-- V43 converted user_rating_stats into a projection of helps/reviews, but its
-- UPDATE only touched rows that already existed. A legacy user with facts but
-- no stats row therefore fell back to the default 3.0 on read and was missing
-- from the top-rated list.
--
-- The INSERT below mirrors the application formula so the projection is usable
-- immediately, without waiting for the scheduler:
--   * base 3.0
--   * confirmed helps +0.10 each, capped at +1.5
--   * cancelled helps -0.10 each, capped at -1.0
--   * reviews (avg - 3.0) * 0.5 * min(1, count / 5), capped at +/-1.0
--   * debt above the default threshold (5) -0.3
--   * active block -0.7
--   * clamp 1..5 and round to 0.1
--
-- Existing rows are never overwritten: ON CONFLICT keeps V43's backfill and any
-- projection already maintained by the application. New users are covered by
-- the runtime self-heal in RatingService when the projection is first read.
--
-- The debt threshold is hard-coded because Flyway cannot read EconomyConfig.
-- Changing app.economy.debt-block-threshold must be accompanied by a future
-- migration that recomputes affected rows.

WITH help_facts AS (
    SELECT helper_id AS user_id,
           COUNT(*) AS total_helps,
           COUNT(*) FILTER (WHERE status = 'CONFIRMED') AS confirmed_helps,
           COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_helps,
           COUNT(*) FILTER (WHERE status IN ('ACCEPTED', 'COMPLETED')) AS active_helps
    FROM helps
    WHERE deleted = FALSE OR deleted IS NULL
    GROUP BY helper_id
),
received_facts AS (
    SELECT receiver_id AS user_id,
           COUNT(*) FILTER (WHERE status = 'CONFIRMED') AS received_helps
    FROM helps
    WHERE deleted = FALSE OR deleted IS NULL
    GROUP BY receiver_id
),
review_facts AS (
    SELECT to_user_id AS user_id,
           COUNT(*) AS review_count,
           AVG(rating)::numeric AS average_rating
    FROM reviews
    WHERE deleted = FALSE OR deleted IS NULL
    GROUP BY to_user_id
),
candidates AS (
    SELECT u.id AS user_id,
           COALESCE(h.total_helps, 0) AS total_helps_given,
           COALESCE(rc.received_helps, 0) AS total_helps_received,
           COALESCE(h.confirmed_helps, 0) AS successful_helps,
           COALESCE(h.cancelled_helps, 0) AS cancelled_helps,
           COALESCE(h.active_helps, 0) AS active_helps_given,
           COALESCE(rf.review_count, 0) AS review_count,
           rf.average_rating,
           3.0
               + LEAST(1.5, COALESCE(h.confirmed_helps, 0) * 0.10)
               - LEAST(1.0, COALESCE(h.cancelled_helps, 0) * 0.10)
               + CASE
                     WHEN COALESCE(rf.review_count, 0) > 0 AND rf.average_rating IS NOT NULL
                     THEN GREATEST(-1.0, LEAST(1.0,
                              (rf.average_rating - 3.0) * 0.5
                              * LEAST(1.0, rf.review_count::numeric / 5.0)))
                     ELSE 0
                 END
               - CASE WHEN COALESCE(u.debt_count, 0) > 5 THEN 0.3 ELSE 0 END
               - CASE
                     WHEN u.blocked_at IS NOT NULL
                          OR (u.blocked_until IS NOT NULL AND u.blocked_until > CURRENT_TIMESTAMP)
                     THEN 0.7
                     ELSE 0
                 END AS raw_rating
    FROM users u
    LEFT JOIN help_facts h ON h.user_id = u.id
    LEFT JOIN received_facts rc ON rc.user_id = u.id
    LEFT JOIN review_facts rf ON rf.user_id = u.id
    WHERE (u.deleted = FALSE OR u.deleted IS NULL)
      AND NOT EXISTS (
          SELECT 1 FROM user_rating_stats s WHERE s.user_id = u.id
      )
)
INSERT INTO user_rating_stats (
    user_id,
    total_helps_given,
    total_helps_received,
    successful_helps,
    cancelled_helps,
    active_helps_given,
    review_count,
    average_review_rating,
    current_rating,
    rating_trend,
    last_calculated,
    updated_at
)
SELECT user_id,
       total_helps_given,
       total_helps_received,
       successful_helps,
       cancelled_helps,
       active_helps_given,
       review_count,
       average_rating,
       ROUND(GREATEST(1.0, LEAST(5.0, raw_rating)), 1),
       'STABLE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM candidates
ON CONFLICT (user_id) DO NOTHING;
