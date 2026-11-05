-- Verify the Block 11 schema contract after V48.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run after `flyway:migrate`. V48 adds the image_objects ownership table, its
-- lifecycle indexes and the avatar/post-image backfill. The script checks the
-- table shape, the state constraints, the partial indexes used by quota and
-- cleanup queries, and that every default-bucket reference still has an active
-- ownership row.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    IF to_regclass('public.image_objects') IS NULL THEN
        RAISE EXCEPTION 'Block 11 schema is missing table image_objects';
    END IF;

    -- Columns and their types: the application maps these directly, so a
    -- silent type drift (for example INTEGER -> BIGINT) must fail the check.
    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('id', 'bigint', NULL::integer),
            ('owner_id', 'bigint', NULL::integer),
            ('object_key', 'character varying', 255),
            ('url', 'text', NULL::integer),
            ('content_type', 'character varying', 100),
            ('size_bytes', 'bigint', NULL::integer),
            ('width', 'integer', NULL::integer),
            ('height', 'integer', NULL::integer),
            ('sha256', 'character varying', 64),
            ('purpose', 'character varying', 20),
            ('status', 'character varying', 20),
            ('created_at', 'timestamp without time zone', NULL::integer),
            ('deleted_at', 'timestamp without time zone', NULL::integer)
    ) AS expected(name, type, max_len)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'image_objects'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = CASE
                  WHEN expected.name IN ('width', 'height', 'sha256', 'deleted_at')
                      THEN 'YES'
                  ELSE 'NO'
              END
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'image_objects has missing or mistyped column(s): %', missing;
    END IF;

    -- Ownership must cascade with the user: a deleted account cannot leave
    -- rows that the quota query would still count.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_image_objects_owner'
          AND conrelid = 'public.image_objects'::regclass
          AND confrelid = 'public.users'::regclass
          AND confdeltype = 'c'
    ) THEN
        RAISE EXCEPTION 'image_objects is missing the cascading owner foreign key';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_image_objects_object_key'
          AND conrelid = 'public.image_objects'::regclass
          AND contype IN ('u', 'p')
    ) THEN
        RAISE EXCEPTION 'image_objects is missing the unique object_key constraint';
    END IF;

    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('chk_image_objects_size'),
            ('chk_image_objects_dimensions'),
            ('chk_image_objects_purpose'),
            ('chk_image_objects_status'),
            ('chk_image_objects_deleted_state')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = expected.name
          AND conrelid = 'public.image_objects'::regclass
          AND contype = 'c'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'image_objects is missing check constraint(s): %', missing;
    END IF;

    -- Quota/count, deletion sweep and orphan candidates must stay partial so
    -- deleted rows drop out of the scans entirely.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_image_objects_owner_active'),
            ('idx_image_objects_deleted_cleanup'),
            ('idx_image_objects_orphan_candidates'),
            ('idx_image_objects_url')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 11 schema is missing image index(es): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_image_objects_owner_active'
          AND indexdef LIKE '%owner_id%'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%ACTIVE%'
    ) THEN
        RAISE EXCEPTION 'idx_image_objects_owner_active must be partial on active rows';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_image_objects_deleted_cleanup'
          AND indexdef LIKE '%deleted_at%'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%DELETED%'
    ) THEN
        RAISE EXCEPTION 'idx_image_objects_deleted_cleanup must be partial on deleted rows';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_image_objects_orphan_candidates'
          AND indexdef LIKE '%created_at%'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%ACTIVE%'
    ) THEN
        RAISE EXCEPTION 'idx_image_objects_orphan_candidates must be partial on active rows';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_image_objects_url'
          AND indexdef LIKE '%url%'
          AND indexdef LIKE '%WHERE%'
          AND indexdef LIKE '%ACTIVE%'
    ) THEN
        RAISE EXCEPTION 'idx_image_objects_url must be partial on active rows';
    END IF;

    -- State invariants. A mismatch here means the application wrote a row
    -- outside ImageObjectService.
    IF EXISTS (
        SELECT 1 FROM image_objects
        WHERE (status = 'ACTIVE' AND deleted_at IS NOT NULL)
           OR (status = 'DELETED' AND deleted_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'image_objects has an inconsistent status/deleted_at pair';
    END IF;

    IF EXISTS (
        SELECT 1 FROM image_objects
        WHERE size_bytes < 0
           OR (width IS NOT NULL AND width <= 0)
           OR (height IS NOT NULL AND height <= 0)
           OR (sha256 IS NOT NULL AND length(sha256) <> 64)
    ) THEN
        RAISE EXCEPTION 'image_objects has an invalid size, dimension or sha256 value';
    END IF;

    -- Keys are server-generated UUIDs with a single extension. A slash would
    -- let a crafted row address a different prefix in the bucket.
    IF EXISTS (
        SELECT 1 FROM image_objects
        WHERE object_key LIKE '%/%'
           OR position(chr(92) in object_key) > 0
           OR object_key LIKE '%..%'
    ) THEN
        RAISE EXCEPTION 'image_objects contains an object key with a path separator';
    END IF;

    -- Backfill coverage for the default bucket. Custom SILO_BUCKET deployments
    -- need an equivalent backfill; the predicate intentionally mirrors V48.
    IF EXISTS (
        SELECT 1
        FROM users u
        WHERE u.avatar_url ~ '/helptree-images/[^/?#]+$'
          AND NOT EXISTS (
              SELECT 1 FROM image_objects io
              WHERE io.url = u.avatar_url AND io.status = 'ACTIVE'
          )
    ) THEN
        RAISE EXCEPTION 'V48 backfill missed an avatar URL in the default bucket';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM post_images pi
        WHERE pi.image_url ~ '/helptree-images/[^/?#]+$'
          AND NOT EXISTS (
              SELECT 1 FROM image_objects io
              WHERE io.url = pi.image_url AND io.status = 'ACTIVE'
          )
    ) THEN
        RAISE EXCEPTION 'V48 backfill missed a post image URL in the default bucket';
    END IF;
END $$;
