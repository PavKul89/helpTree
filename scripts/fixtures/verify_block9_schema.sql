-- Verify the Block 9 schema contract after V46.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run immediately after `flyway:migrate` (as in the CI flow). The consistency
-- blocks also hold on a live database: the geocoding worker only writes a
-- resolved pair of coordinates together, and request handlers never touch the
-- queue.
\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- pg_trgm backs the trigram GIN indexes and the case-insensitive substring
    -- search used by the map specification and the unified search UNION.
    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'
    ) THEN
        RAISE EXCEPTION 'Block 9 requires the pg_trgm extension';
    END IF;

    -- Trigram GIN indexes added by V46.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_posts_title_trgm'),
            ('idx_posts_description_trgm'),
            ('idx_posts_category_trgm'),
            ('idx_users_name_trgm'),
            ('idx_users_city_trgm'),
            ('idx_reviews_comment_trgm')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = expected.name
          AND indexdef LIKE '%USING gin%'
          AND indexdef LIKE '%gin_trgm_ops%'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 9 schema is missing trigram GIN index(es): %', missing;
    END IF;

    -- Partial map/feed indexes added by V46.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_posts_map_active'),
            ('idx_posts_map_category'),
            ('idx_posts_public_feed'),
            ('idx_users_public_rating'),
            ('idx_helps_created')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 9 schema is missing partial index(es): %', missing;
    END IF;

    -- The map indexes must stay partial: a full index would include cancelled
    -- and coordinate-less rows and lose the intended scan shape.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_posts_map_active'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%latitude IS NOT NULL%'
          AND indexdef LIKE '%longitude IS NOT NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_posts_map_active must be partial on located active posts';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_posts_map_category'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%category%'
          AND indexdef LIKE '%latitude IS NOT NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_posts_map_category must be partial on located active posts';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_posts_public_feed'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%created_at DESC%'
    ) THEN
        RAISE EXCEPTION 'idx_posts_public_feed must be partial and newest-first';
    END IF;

    -- Durable geocoding queue added by V46.
    IF to_regclass('public.geocoding_cache') IS NULL THEN
        RAISE EXCEPTION 'Block 9 schema is missing geocoding_cache';
    END IF;

    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('city_key'),
            ('query_city'),
            ('latitude'),
            ('longitude'),
            ('status'),
            ('attempts'),
            ('last_error'),
            ('created_at'),
            ('updated_at'),
            ('resolved_at')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'geocoding_cache'
          AND column_name = expected.name
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'geocoding_cache is missing column(s): %', missing;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'geocoding_cache'
          AND column_name IN ('city_key', 'query_city', 'status', 'attempts',
                              'created_at', 'updated_at')
          AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'geocoding_cache key/status/attempt/timestamp columns must be NOT NULL';
    END IF;

    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('geocoding_cache_status_chk'),
            ('geocoding_cache_attempts_chk')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = expected.name
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 9 schema is missing constraint(s): %', missing;
    END IF;

    IF to_regclass('public.idx_geocoding_cache_queue') IS NULL THEN
        RAISE EXCEPTION 'Block 9 schema is missing idx_geocoding_cache_queue';
    END IF;

    -- Queue state invariants: a resolved row always has both coordinates,
    -- attempts never decreases below zero, and city keys are normalized
    -- (lowercase, no surrounding spaces).
    IF EXISTS (
        SELECT 1
        FROM geocoding_cache
        WHERE status NOT IN ('PENDING', 'PROCESSING', 'FOUND', 'FAILED')
           OR attempts < 0
           OR (status = 'FOUND' AND (latitude IS NULL OR longitude IS NULL))
           OR city_key <> btrim(city_key)
           OR city_key <> lower(city_key)
           OR city_key = ''
    ) THEN
        RAISE EXCEPTION 'geocoding_cache contains an invalid row';
    END IF;

    -- Posts that are visible on the map inherit their author's resolved
    -- coordinates. Manually set post coordinates are never overwritten, so the
    -- check only looks at rows with both values missing.
    IF EXISTS (
        SELECT 1
        FROM posts p
        JOIN users u ON u.id = p.user_id
        WHERE COALESCE(p.deleted, FALSE) = FALSE
          AND p.status <> 'CANCELLED'
          AND p.latitude IS NULL
          AND p.longitude IS NULL
          AND u.latitude IS NOT NULL
          AND u.longitude IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'V46 did not backfill coordinates for visible posts';
    END IF;
END $$;
