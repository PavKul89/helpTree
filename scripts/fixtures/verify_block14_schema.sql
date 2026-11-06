-- Verify the Block 14 schema contract after V50.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run after `flyway:migrate`. V50 adds the irreversible anonymization marker on
-- users, two partial indexes used by the deleted/anonymized admin views and the
-- user_privacy_settings table that backs the account privacy endpoints. The
-- script checks the table shape, defaults, constraints, indexes and the
-- invariants that make an anonymized row safe for historical references.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- ---------------------------------------------------------------
    -- users.anonymized_at
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'users'
          AND c.column_name = 'anonymized_at'
          AND c.data_type = 'timestamp without time zone'
          AND c.is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'users.anonymized_at is missing or has the wrong type/nullability';
    END IF;

    -- ---------------------------------------------------------------
    -- user_privacy_settings shape
    -- ---------------------------------------------------------------
    IF to_regclass('public.user_privacy_settings') IS NULL THEN
        RAISE EXCEPTION 'Block 14 schema is missing table user_privacy_settings';
    END IF;

    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('user_id', 'bigint', NULL::integer),
            ('profile_visibility', 'character varying', 20),
            ('show_city', 'boolean', NULL::integer),
            ('show_email', 'boolean', NULL::integer),
            ('show_phone', 'boolean', NULL::integer),
            ('allow_direct_messages', 'boolean', NULL::integer),
            ('searchable', 'boolean', NULL::integer),
            ('created_at', 'timestamp without time zone', NULL::integer),
            ('updated_at', 'timestamp without time zone', NULL::integer)
    ) AS expected(name, type, max_len)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'user_privacy_settings'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = 'NO'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'user_privacy_settings has missing or mistyped column(s): %', missing;
    END IF;

    -- ---------------------------------------------------------------
    -- Column defaults: absence of a row means "defaults", and the service
    -- mirrors these values, so a drift here silently changes new accounts.
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.name || ' -> ' || expected.value, ', ')
    INTO missing
    FROM (
        VALUES
            ('profile_visibility', 'REGISTERED'),
            ('show_city', 'true'),
            ('show_email', 'false'),
            ('show_phone', 'false'),
            ('allow_direct_messages', 'true'),
            ('searchable', 'true')
    ) AS expected(name, value)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'user_privacy_settings'
          AND c.column_name = expected.name
          AND lower(c.column_default) LIKE '%' || lower(expected.value) || '%'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'user_privacy_settings has wrong default(s): %', missing;
    END IF;

    -- ---------------------------------------------------------------
    -- Primary key, visibility constraint and cascading FK
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'user_privacy_settings_pkey'
          AND conrelid = 'public.user_privacy_settings'::regclass
          AND contype = 'p'
    ) THEN
        RAISE EXCEPTION 'user_privacy_settings is missing its primary key';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_privacy_settings_visibility'
          AND conrelid = 'public.user_privacy_settings'::regclass
          AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'user_privacy_settings is missing chk_user_privacy_settings_visibility';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'user_privacy_settings_user_id_fkey'
          AND conrelid = 'public.user_privacy_settings'::regclass
          AND contype = 'f'
          AND confrelid = 'public.users'::regclass
          AND confdeltype = 'c'
    ) THEN
        RAISE EXCEPTION 'user_privacy_settings.user_id must reference users(id) ON DELETE CASCADE';
    END IF;

    -- ---------------------------------------------------------------
    -- Indexes used by admin filters and the public projection.
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_users_anonymized_at'),
            ('idx_users_deleted_at'),
            ('idx_user_privacy_settings_visibility')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 14 schema is missing index(es): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_users_anonymized_at'
          AND indexdef LIKE '%anonymized_at%'
          AND indexdef LIKE '%IS NOT NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_users_anonymized_at must be a partial index on non-null markers';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_users_deleted_at'
          AND indexdef LIKE '%deleted_at%'
          AND indexdef LIKE '%deleted = true%'
    ) THEN
        RAISE EXCEPTION 'idx_users_deleted_at must be a partial index for deleted = true';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_user_privacy_settings_visibility'
          AND indexdef LIKE '%profile_visibility%'
          AND indexdef LIKE '%searchable%'
    ) THEN
        RAISE EXCEPTION 'idx_user_privacy_settings_visibility must cover (profile_visibility, searchable)';
    END IF;

    -- ---------------------------------------------------------------
    -- Data invariants
    -- ---------------------------------------------------------------
    -- An anonymized row is terminal: it is always soft-deleted and its email is
    -- replaced with the reserved anonymized address. A marker set without the
    -- PII rewrite would leave personal data behind.
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE anonymized_at IS NOT NULL
          AND (deleted IS DISTINCT FROM TRUE
               OR email NOT LIKE 'anonymized+%@anon.helptree.local')
    ) THEN
        RAISE EXCEPTION 'users contains an anonymized row that is not deleted or still has a real email';
    END IF;

    -- deleted_at is only meaningful together with deleted = true.
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE deleted_at IS NOT NULL
          AND deleted IS DISTINCT FROM TRUE
    ) THEN
        RAISE EXCEPTION 'users contains deleted_at without deleted = true';
    END IF;

    -- Privacy rows must always point at an existing user and carry a valid
    -- visibility value (the CHECK constraint enforces the latter for new rows).
    IF EXISTS (
        SELECT 1
        FROM user_privacy_settings p
        LEFT JOIN users u ON u.id = p.user_id
        WHERE u.id IS NULL
           OR p.profile_visibility NOT IN ('PUBLIC', 'REGISTERED', 'PRIVATE')
    ) THEN
        RAISE EXCEPTION 'user_privacy_settings contains an orphan or invalid visibility';
    END IF;
END $$;
