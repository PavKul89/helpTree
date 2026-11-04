-- Verify the Block 8 schema contract after V45.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run immediately after `flyway:migrate` (as in the CI flow). The consistency
-- blocks also hold on a live database because every comment write updates the
-- projection in the same transaction; a deliberately desynchronized row would
-- be reported here.
\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- Columns added by V45.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('deleted'),
            ('deleted_at'),
            ('deleted_by_id'),
            ('deleted_by_author'),
            ('deleted_reason'),
            ('depth'),
            ('updated_at')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'comments'
          AND column_name = expected.name
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 8 schema is missing comments column(s): %', missing;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'comments'
          AND column_name = 'depth'
          AND (is_nullable = 'YES' OR data_type <> 'integer')
    ) THEN
        RAISE EXCEPTION 'comments.depth must be a NOT NULL INTEGER';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'comments'
          AND column_name IN ('deleted', 'deleted_by_author')
          AND (is_nullable = 'YES' OR data_type <> 'boolean')
    ) THEN
        RAISE EXCEPTION 'comments.deleted/deleted_by_author must be NOT NULL BOOLEAN';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'posts'
          AND column_name = 'comments_count'
          AND is_nullable = 'NO'
          AND data_type = 'integer'
    ) THEN
        RAISE EXCEPTION 'posts.comments_count must be a NOT NULL INTEGER';
    END IF;

    -- Constraints added by V45.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('fk_comments_deleted_by'),
            ('chk_comments_depth'),
            ('chk_comments_deleted_state'),
            ('chk_posts_comments_count')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = expected.name
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 8 schema is missing constraint(s): %', missing;
    END IF;

    -- Indexes added by V45.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_comments_post_roots_created'),
            ('idx_comments_post_thread'),
            ('idx_comments_post_deleted_created')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 8 schema is missing index(es): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_comments_post_roots_created'
          AND indexdef LIKE '%parent_comment_id IS NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_comments_post_roots_created must be partial on root comments';
    END IF;

    -- Every root is depth 0, and a reply is exactly one level deeper than its
    -- parent. The 20-level bound mirrors the defensive limit in V45's backfill.
    IF EXISTS (
        SELECT 1
        FROM comments c
        LEFT JOIN comments p ON p.id = c.parent_comment_id
        WHERE (c.parent_comment_id IS NULL AND c.depth <> 0)
           OR (c.parent_comment_id IS NOT NULL
               AND p.id IS NOT NULL
               AND p.depth < 20
               AND c.depth <> p.depth + 1)
    ) THEN
        RAISE EXCEPTION 'comments.depth is inconsistent with parent_comment_id';
    END IF;

    -- A tombstone must always carry the deletion timestamp, and a live row
    -- must not look deleted.
    IF EXISTS (
        SELECT 1
        FROM comments
        WHERE (deleted = TRUE AND deleted_at IS NULL)
           OR (deleted = FALSE AND deleted_at IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'comments tombstone flags and deleted_at disagree';
    END IF;

    -- posts.comments_count is the projection of non-deleted comments.
    IF EXISTS (
        SELECT 1
        FROM posts p
        WHERE p.comments_count <> (
            SELECT COUNT(*)
            FROM comments c
            WHERE c.post_id = p.id
              AND c.deleted = FALSE
        )
    ) THEN
        RAISE EXCEPTION 'posts.comments_count is out of sync with non-deleted comments';
    END IF;
END $$;
