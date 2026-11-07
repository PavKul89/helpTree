-- Verify the Block 16 schema contract after V53.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run after `flyway:migrate`. V53 introduces the user-initiated block relation:
--   * user_blocks_relations is a separate domain from the V42 moderation journal
--     `user_blocks` and from the account-level `users.blockedAt/blockedUntil`;
--   * one row per ordered pair (blocker_id, blocked_id);
--   * scope is limited to ALL/MESSAGES/PROFILE/HELP_REQUESTS;
--   * a block can be permanent (expires_at IS NULL) or time-limited;
--   * a user can never block themselves and the pair cascades with the users.
-- The script checks the shape, defaults, constraints, cascade rules and indexes,
-- plus the invariants that keep an imported row consistent with the service.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- ---------------------------------------------------------------
    -- Table and columns
    -- ---------------------------------------------------------------
    IF to_regclass('public.user_blocks_relations') IS NULL THEN
        RAISE EXCEPTION 'user_blocks_relations does not exist';
    END IF;

    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('id', 'bigint', NULL::integer, 'NO'),
            ('blocker_id', 'bigint', NULL::integer, 'NO'),
            ('blocked_id', 'bigint', NULL::integer, 'NO'),
            ('scope', 'character varying', 20, 'NO'),
            ('created_at', 'timestamp without time zone', NULL::integer, 'NO'),
            ('expires_at', 'timestamp without time zone', NULL::integer, 'YES')
    ) AS expected(name, type, max_len, is_nullable)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'user_blocks_relations'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = expected.is_nullable
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'user_blocks_relations has missing or mistyped column(s): %', missing;
    END IF;

    -- `id` must be a generated BIGSERIAL primary key.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_blocks_relations'
          AND column_name = 'id'
          AND lower(column_default) LIKE 'nextval(%'
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations.id must be generated (BIGSERIAL)';
    END IF;

    -- Defaults mirror the service contract: scope defaults to ALL and the
    -- creation timestamp is filled by the database, so an INSERT cannot forget
    -- either value.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_blocks_relations'
          AND column_name = 'scope'
          AND column_default LIKE '''ALL''%'
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations.scope must default to ALL';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_blocks_relations'
          AND column_name = 'created_at'
          AND column_default LIKE '%CURRENT_TIMESTAMP%'
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations.created_at must default to CURRENT_TIMESTAMP';
    END IF;

    -- ---------------------------------------------------------------
    -- Constraints: scope, self-block, period, unique pair
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_blocks_relations_scope'
          AND conrelid = 'public.user_blocks_relations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%ALL%'
          AND pg_get_constraintdef(oid) LIKE '%MESSAGES%'
          AND pg_get_constraintdef(oid) LIKE '%PROFILE%'
          AND pg_get_constraintdef(oid) LIKE '%HELP_REQUESTS%'
    ) THEN
        RAISE EXCEPTION 'chk_user_blocks_relations_scope must accept ALL/MESSAGES/PROFILE/HELP_REQUESTS';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_blocks_relations_self'
          AND conrelid = 'public.user_blocks_relations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%blocker_id%'
          AND pg_get_constraintdef(oid) LIKE '%blocked_id%'
    ) THEN
        RAISE EXCEPTION 'chk_user_blocks_relations_self must reject blocker_id = blocked_id';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_blocks_relations_period'
          AND conrelid = 'public.user_blocks_relations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%expires_at%'
          AND pg_get_constraintdef(oid) LIKE '%created_at%'
    ) THEN
        RAISE EXCEPTION 'chk_user_blocks_relations_period must require expires_at > created_at';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_user_blocks_relations_pair'
          AND conrelid = 'public.user_blocks_relations'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) LIKE '%blocker_id%'
          AND pg_get_constraintdef(oid) LIKE '%blocked_id%'
    ) THEN
        RAISE EXCEPTION 'uq_user_blocks_relations_pair must be UNIQUE (blocker_id, blocked_id)';
    END IF;

    -- ---------------------------------------------------------------
    -- Foreign keys: deleting either user removes the relation
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'user_blocks_relations_blocker_id_fkey'
          AND conrelid = 'public.user_blocks_relations'::regclass
          AND contype = 'f'
          AND confrelid = 'public.users'::regclass
          AND confdeltype = 'c'
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations.blocker_id must reference users(id) ON DELETE CASCADE';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'user_blocks_relations_blocked_id_fkey'
          AND conrelid = 'public.user_blocks_relations'::regclass
          AND contype = 'f'
          AND confrelid = 'public.users'::regclass
          AND confdeltype = 'c'
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations.blocked_id must reference users(id) ON DELETE CASCADE';
    END IF;

    -- ---------------------------------------------------------------
    -- Indexes: both directions of a bidirectional lookup
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_user_blocks_relations_blocker'),
            ('idx_user_blocks_relations_blocked')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 16 schema is missing index(es): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_user_blocks_relations_blocker'
          AND indexdef LIKE '%blocker_id%'
          AND indexdef LIKE '%expires_at%'
    ) THEN
        RAISE EXCEPTION 'idx_user_blocks_relations_blocker must cover (blocker_id, expires_at)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_user_blocks_relations_blocked'
          AND indexdef LIKE '%blocked_id%'
          AND indexdef LIKE '%expires_at%'
    ) THEN
        RAISE EXCEPTION 'idx_user_blocks_relations_blocked must cover (blocked_id, expires_at)';
    END IF;

    -- ---------------------------------------------------------------
    -- Data invariants (guards against imports that bypass the service)
    -- ---------------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM user_blocks_relations
        WHERE scope NOT IN ('ALL', 'MESSAGES', 'PROFILE', 'HELP_REQUESTS')
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations contains an unknown scope value';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_blocks_relations
        WHERE blocker_id = blocked_id
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations contains a self-block';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_blocks_relations
        WHERE expires_at IS NOT NULL
          AND expires_at <= created_at
    ) THEN
        RAISE EXCEPTION 'user_blocks_relations contains a block that expires at or before creation';
    END IF;

    -- The user-facing block relation must stay a separate domain from the
    -- moderation/debt journal V42. A table swap or accidental rename would
    -- silently make moderation sanctions bidirectional.
    IF to_regclass('public.user_blocks') IS NULL THEN
        RAISE EXCEPTION 'user_blocks (V42 moderation journal) is missing; Block 16 must not replace it';
    END IF;
END $$;
