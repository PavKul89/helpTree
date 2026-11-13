-- Verify the Block 17 schema contract after V54.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- V54 evolves the V42 notification domain without deleting data:
--   * the legacy account-wide settings table is renamed to
--     account_notification_preferences (Block 14 still reads it);
--   * a new per-type/per-channel notification_preferences table is created;
--   * notifications.data is renamed to notifications.payload_json;
--   * the outbox dispatch index becomes (status, available_at, id).
--
-- The script checks the shape, defaults, constraints, cascade rules and indexes
-- that the notification service depends on.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- ============================================================
    -- 1. LEGACY ACCOUNT SETTINGS
    -- ============================================================
    IF to_regclass('public.account_notification_preferences') IS NULL THEN
        RAISE EXCEPTION 'account_notification_preferences does not exist (V42 legacy table must be renamed, not dropped)';
    END IF;

    -- The V42 table must keep its account-wide shape: one row per user.
    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('user_id', 'bigint', NULL::integer, 'NO'),
            ('email_enabled', 'boolean', NULL::integer, 'NO'),
            ('push_enabled', 'boolean', NULL::integer, 'NO'),
            ('in_app_enabled', 'boolean', NULL::integer, 'NO'),
            ('digest_frequency', 'character varying', 20, 'NO'),
            ('quiet_hours_start', 'time without time zone', NULL::integer, 'YES'),
            ('quiet_hours_end', 'time without time zone', NULL::integer, 'YES'),
            ('created_at', 'timestamp without time zone', NULL::integer, 'NO'),
            ('updated_at', 'timestamp without time zone', NULL::integer, 'NO')
    ) AS expected(name, type, max_len, is_nullable)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'account_notification_preferences'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = expected.is_nullable
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'account_notification_preferences has missing or mistyped column(s): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'account_notification_preferences_pkey'
          AND conrelid = 'public.account_notification_preferences'::regclass
          AND contype = 'p'
          AND pg_get_constraintdef(oid) LIKE '%user_id%'
    ) THEN
        RAISE EXCEPTION 'account_notification_preferences must keep user_id as its primary key';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_account_notification_preferences_digest'
          AND conrelid = 'public.account_notification_preferences'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%INSTANT%'
          AND pg_get_constraintdef(oid) LIKE '%OFF%'
    ) THEN
        RAISE EXCEPTION 'chk_account_notification_preferences_digest is missing or does not accept INSTANT..OFF';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_account_notification_preferences_quiet_hours'
          AND conrelid = 'public.account_notification_preferences'::regclass
          AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'chk_account_notification_preferences_quiet_hours is missing';
    END IF;

    -- ============================================================
    -- 2. PER-TYPE / PER-CHANNEL PREFERENCES
    -- ============================================================
    IF to_regclass('public.notification_preferences') IS NULL THEN
        RAISE EXCEPTION 'notification_preferences does not exist';
    END IF;

    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('id', 'bigint', NULL::integer, 'NO'),
            ('user_id', 'bigint', NULL::integer, 'NO'),
            ('type', 'character varying', 50, 'NO'),
            ('channel', 'character varying', 20, 'NO'),
            ('enabled', 'boolean', NULL::integer, 'NO'),
            ('created_at', 'timestamp without time zone', NULL::integer, 'NO'),
            ('updated_at', 'timestamp without time zone', NULL::integer, 'NO')
    ) AS expected(name, type, max_len, is_nullable)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'notification_preferences'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = expected.is_nullable
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'notification_preferences has missing or mistyped column(s): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notification_preferences'
          AND column_name = 'id'
          AND lower(column_default) LIKE 'nextval(%'
    ) THEN
        RAISE EXCEPTION 'notification_preferences.id must be generated (BIGSERIAL)';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notification_preferences'
          AND column_name = 'enabled'
          AND column_default LIKE 'true%'
    ) THEN
        RAISE EXCEPTION 'notification_preferences.enabled must default to true';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notification_preferences'
          AND column_name = 'created_at'
          AND column_default LIKE '%CURRENT_TIMESTAMP%'
    ) THEN
        RAISE EXCEPTION 'notification_preferences.created_at must default to CURRENT_TIMESTAMP';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notification_preferences'
          AND column_name = 'updated_at'
          AND column_default LIKE '%CURRENT_TIMESTAMP%'
    ) THEN
        RAISE EXCEPTION 'notification_preferences.updated_at must default to CURRENT_TIMESTAMP';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_notification_preferences_user_type_channel'
          AND conrelid = 'public.notification_preferences'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) LIKE '%user_id%'
          AND pg_get_constraintdef(oid) LIKE '%type%'
          AND pg_get_constraintdef(oid) LIKE '%channel%'
    ) THEN
        RAISE EXCEPTION 'uq_notification_preferences_user_type_channel must be UNIQUE (user_id, type, channel)';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_notification_preferences_channel'
          AND conrelid = 'public.notification_preferences'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%IN_APP%'
          AND pg_get_constraintdef(oid) LIKE '%EMAIL%'
          AND pg_get_constraintdef(oid) LIKE '%TELEGRAM%'
    ) THEN
        RAISE EXCEPTION 'chk_notification_preferences_channel must accept IN_APP/EMAIL/TELEGRAM';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_notification_preferences_type_not_blank'
          AND conrelid = 'public.notification_preferences'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%length%'
    ) THEN
        RAISE EXCEPTION 'chk_notification_preferences_type_not_blank is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.notification_preferences'::regclass
          AND contype = 'f'
          AND confrelid = 'public.users'::regclass
          AND confdeltype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%FOREIGN KEY (user_id)%'
    ) THEN
        RAISE EXCEPTION 'notification_preferences.user_id must reference users(id) ON DELETE CASCADE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_notification_preferences_user'
          AND indexdef LIKE '%user_id%'
          AND indexdef LIKE '%type%'
          AND indexdef LIKE '%channel%'
    ) THEN
        RAISE EXCEPTION 'idx_notification_preferences_user must cover (user_id, type, channel)';
    END IF;

    -- ============================================================
    -- 3. CANONICAL NOTIFICATIONS
    -- ============================================================
    IF to_regclass('public.notifications') IS NULL THEN
        RAISE EXCEPTION 'notifications does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'payload_json'
          AND data_type = 'jsonb'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'notifications.payload_json must exist as a NOT NULL jsonb column';
    END IF;

    -- The V42 column must be renamed, not duplicated: two payload columns would
    -- let the service write one and the client read the other.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'data'
    ) THEN
        RAISE EXCEPTION 'notifications.data must be renamed to payload_json';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_notifications_user_dedupe'
          AND indexdef LIKE 'CREATE UNIQUE INDEX%'
          AND indexdef LIKE '%user_id%'
          AND indexdef LIKE '%dedupe_key%'
          AND indexdef LIKE '%dedupe_key IS NOT NULL%'
    ) THEN
        RAISE EXCEPTION 'uq_notifications_user_dedupe must be a partial UNIQUE (user_id, dedupe_key) index';
    END IF;

    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_notifications_user_created'),
            ('idx_notifications_user_unread')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'notifications is missing index(es): %', missing;
    END IF;

    -- ============================================================
    -- 4. OUTBOX GUARANTEES
    -- ============================================================
    IF to_regclass('public.outbox_events') IS NULL THEN
        RAISE EXCEPTION 'outbox_events does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_outbox_events_status'
          AND conrelid = 'public.outbox_events'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%PENDING%'
          AND pg_get_constraintdef(oid) LIKE '%PROCESSING%'
          AND pg_get_constraintdef(oid) LIKE '%PUBLISHED%'
          AND pg_get_constraintdef(oid) LIKE '%FAILED%'
          AND pg_get_constraintdef(oid) LIKE '%DEAD%'
    ) THEN
        RAISE EXCEPTION 'chk_outbox_events_status must accept PENDING/PROCESSING/PUBLISHED/FAILED/DEAD';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_outbox_events_attempts'
          AND conrelid = 'public.outbox_events'::regclass
          AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'chk_outbox_events_attempts is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_outbox_events_dispatch'
          AND indexdef LIKE '%status%'
          AND indexdef LIKE '%available_at%'
          AND indexdef LIKE '%id%'
    ) THEN
        RAISE EXCEPTION 'idx_outbox_events_dispatch must cover (status, available_at, id)';
    END IF;

    -- V62 partitions outbox_events, so the dedupe index is intentionally
    -- non-unique (a partitioned UNIQUE index must include the partition key).
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_outbox_events_dedupe'
          AND indexdef LIKE 'CREATE INDEX%'
          AND indexdef NOT LIKE 'CREATE UNIQUE INDEX%'
          AND indexdef LIKE '%dedupe_key%'
          AND indexdef LIKE '%dedupe_key IS NOT NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_outbox_events_dedupe must be a partial non-unique index on dedupe_key';
    END IF;

    -- ============================================================
    -- 5. DATA INVARIANTS
    -- ============================================================
    IF EXISTS (
        SELECT 1
        FROM notification_preferences
        WHERE channel NOT IN ('IN_APP', 'EMAIL', 'TELEGRAM')
    ) THEN
        RAISE EXCEPTION 'notification_preferences contains an unknown channel';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM outbox_events
        WHERE status NOT IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD')
    ) THEN
        RAISE EXCEPTION 'outbox_events contains an unknown status';
    END IF;

    -- One stored preference per (user, type, channel) is what the upsert relies on.
    IF EXISTS (
        SELECT 1
        FROM notification_preferences
        GROUP BY user_id, type, channel
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'notification_preferences contains duplicate (user_id, type, channel) rows';
    END IF;
END $$;
