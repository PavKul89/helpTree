-- Verify the Block 7 schema contract after V43.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run immediately after `flyway:migrate` (as in the CI flow). The consistency
-- block compares user_rating_stats with the facts it is projected from; running
-- it later on a live database is still valid because every write path rebuilds
-- the projection, but a deliberately stale row would be reported here.
\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- Columns added by V43.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('active_helps_given'),
            ('review_count'),
            ('average_review_rating')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_rating_stats'
          AND column_name = expected.name
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 7 schema is missing column(s): %', missing;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_rating_stats'
          AND column_name IN ('active_helps_given', 'review_count')
          AND (is_nullable = 'YES' OR data_type <> 'bigint')
    ) THEN
        RAISE EXCEPTION 'active_helps_given/review_count must be NOT NULL BIGINT';
    END IF;

    -- Indexes added by V43.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_helps_helper_status_active'),
            ('idx_helps_receiver_status_active'),
            ('idx_helps_status_confirmed_at'),
            ('idx_reviews_to_user_created'),
            ('idx_reviews_rating_created'),
            ('idx_reviews_created_at'),
            ('idx_user_rating_stats_last_calculated')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 7 schema is missing index(es): %', missing;
    END IF;

    -- Partial indexes must actually exclude soft-deleted rows.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_helps_helper_status_active'
          AND indexdef LIKE '%(helper_id, status)%'
          AND indexdef LIKE '%deleted = false%'
    ) THEN
        RAISE EXCEPTION 'idx_helps_helper_status_active must be partial on deleted = false';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_reviews_to_user_created'
          AND indexdef LIKE '%(to_user_id, created_at DESC)%'
          AND indexdef LIKE '%deleted = false%'
    ) THEN
        RAISE EXCEPTION 'idx_reviews_to_user_created must be partial on deleted = false';
    END IF;

    -- The projection must equal the facts it is built from.
    IF EXISTS (
        SELECT 1
        FROM user_rating_stats s
        WHERE s.total_helps_given <> (
                  SELECT COUNT(*) FROM helps h
                  WHERE h.helper_id = s.user_id
                    AND (h.deleted = FALSE OR h.deleted IS NULL))
           OR s.successful_helps <> (
                  SELECT COUNT(*) FROM helps h
                  WHERE h.helper_id = s.user_id
                    AND h.status = 'CONFIRMED'
                    AND (h.deleted = FALSE OR h.deleted IS NULL))
           OR s.cancelled_helps <> (
                  SELECT COUNT(*) FROM helps h
                  WHERE h.helper_id = s.user_id
                    AND h.status = 'CANCELLED'
                    AND (h.deleted = FALSE OR h.deleted IS NULL))
           OR s.active_helps_given <> (
                  SELECT COUNT(*) FROM helps h
                  WHERE h.helper_id = s.user_id
                    AND h.status IN ('ACCEPTED', 'COMPLETED')
                    AND (h.deleted = FALSE OR h.deleted IS NULL))
           OR s.total_helps_received <> (
                  SELECT COUNT(*) FROM helps h
                  WHERE h.receiver_id = s.user_id
                    AND h.status = 'CONFIRMED'
                    AND (h.deleted = FALSE OR h.deleted IS NULL))
           OR s.review_count <> (
                  SELECT COUNT(*) FROM reviews r
                  WHERE r.to_user_id = s.user_id
                    AND (r.deleted = FALSE OR r.deleted IS NULL))
    ) THEN
        RAISE EXCEPTION 'user_rating_stats is out of sync with helps/reviews facts';
    END IF;
END $$;
