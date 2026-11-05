-- Verify the Block 10 schema contract after V47.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run after `flyway:migrate`. V47 is index-only, so this script checks the
-- presence and shape of the indexes that bound the activity feed, the recursive
-- help graph and the rating-history retention job.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- Activity feed: participant/timestamp composite indexes. The UNION source
    -- for each event type relies on one of these to avoid a sequential scan.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_helps_helper_activity'),
            ('idx_helps_receiver_activity'),
            ('idx_posts_user_activity'),
            ('idx_achievements_user_earned')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 10 schema is missing activity index(es): %', missing;
    END IF;

    -- The activity indexes must stay partial on live rows; otherwise a scan
    -- includes soft-deleted history and loses the intended shape.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_helps_helper_activity'
          AND indexdef LIKE '%helper_id%'
          AND indexdef LIKE '%COALESCE%'
          AND indexdef LIKE '%WHERE%'
    ) THEN
        RAISE EXCEPTION 'idx_helps_helper_activity must order by the projected event time';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_helps_receiver_activity'
          AND indexdef LIKE '%receiver_id%'
          AND indexdef LIKE '%COALESCE%'
          AND indexdef LIKE '%WHERE%'
    ) THEN
        RAISE EXCEPTION 'idx_helps_receiver_activity must order by the projected event time';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_posts_user_activity'
          AND indexdef LIKE '%user_id%'
          AND indexdef LIKE '%created_at DESC%'
          AND indexdef LIKE '%WHERE%'
    ) THEN
        RAISE EXCEPTION 'idx_posts_user_activity must be partial and newest-first';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_achievements_user_earned'
          AND indexdef LIKE '%user_id%'
          AND indexdef LIKE '%earned_at DESC%'
          AND indexdef LIKE '%WHERE%'
    ) THEN
        RAISE EXCEPTION 'idx_achievements_user_earned must be partial and newest-first';
    END IF;

    -- Help graph: the recursive CTE walks confirmed edges and counts them.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_helps_confirmed_edges'),
            ('idx_helps_confirmed_order')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 10 schema is missing graph index(es): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_helps_confirmed_edges'
          AND indexdef LIKE '%helper_id%'
          AND indexdef LIKE '%receiver_id%'
          AND indexdef LIKE '%CONFIRMED%'
          AND indexdef LIKE '%WHERE%'
    ) THEN
        RAISE EXCEPTION 'idx_helps_confirmed_edges must be partial on confirmed helps';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_helps_confirmed_order'
          AND indexdef LIKE '%confirmed_at%'
          AND indexdef LIKE '%WHERE%'
    ) THEN
        RAISE EXCEPTION 'idx_helps_confirmed_order must be partial on live helps';
    END IF;

    -- Rating-history retention: the DELETE subquery orders by calculated_at.
    IF to_regclass('public.idx_rating_history_retention') IS NULL THEN
        RAISE EXCEPTION 'Block 10 schema is missing idx_rating_history_retention';
    END IF;
END $$;
