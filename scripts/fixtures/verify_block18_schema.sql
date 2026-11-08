-- Verify the Block 18 schema contract after V55.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Block 18 does not rewrite the V42 audit table. It only adds composite and
-- partial indexes required by the new server-side filters:
--   * admin_audit_log (action, created_at) and (entity_type, created_at);
--   * soft-deleted posts / reviews;
--   * the open report queue;
--   * active auth sessions per user.
--
-- The script also re-checks the append-only audit shape (columns, constraints,
-- foreign key) so a later migration cannot silently widen or narrow it.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- ============================================================
    -- 1. AUDIT TABLE SHAPE
    -- ============================================================
    IF to_regclass('public.admin_audit_log') IS NULL THEN
        RAISE EXCEPTION 'admin_audit_log does not exist';
    END IF;

    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('id', 'bigint', NULL::integer, 'NO'),
            ('admin_id', 'bigint', NULL::integer, 'YES'),
            ('action', 'character varying', 80, 'NO'),
            ('entity_type', 'character varying', 40, 'NO'),
            ('entity_id', 'bigint', NULL::integer, 'YES'),
            ('before_state', 'jsonb', NULL::integer, 'YES'),
            ('after_state', 'jsonb', NULL::integer, 'YES'),
            ('reason', 'character varying', 1000, 'YES'),
            ('ip_address', 'character varying', 64, 'YES'),
            ('user_agent', 'character varying', 512, 'YES'),
            ('request_id', 'character varying', 100, 'YES'),
            ('created_at', 'timestamp without time zone', NULL::integer, 'NO')
    ) AS expected(name, type, max_len, is_nullable)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'admin_audit_log'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = expected.is_nullable
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'admin_audit_log has missing or mistyped column(s): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'admin_audit_log'
          AND column_name = 'id'
          AND lower(column_default) LIKE 'nextval(%'
    ) THEN
        RAISE EXCEPTION 'admin_audit_log.id must be generated (BIGSERIAL)';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'admin_audit_log'
          AND column_name = 'created_at'
          AND column_default LIKE '%CURRENT_TIMESTAMP%'
    ) THEN
        RAISE EXCEPTION 'admin_audit_log.created_at must default to CURRENT_TIMESTAMP';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'admin_audit_log_pkey'
          AND conrelid = 'public.admin_audit_log'::regclass
          AND contype = 'p'
          AND pg_get_constraintdef(oid) LIKE '%id%'
    ) THEN
        RAISE EXCEPTION 'admin_audit_log must keep id as its primary key';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_admin_audit_action_not_blank'
          AND conrelid = 'public.admin_audit_log'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%action%'
          AND pg_get_constraintdef(oid) LIKE '%length%'
    ) THEN
        RAISE EXCEPTION 'chk_admin_audit_action_not_blank is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_admin_audit_entity_not_blank'
          AND conrelid = 'public.admin_audit_log'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%entity_type%'
          AND pg_get_constraintdef(oid) LIKE '%length%'
    ) THEN
        RAISE EXCEPTION 'chk_admin_audit_entity_not_blank is missing';
    END IF;

    -- History must survive account deletion: SET NULL, not CASCADE.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.admin_audit_log'::regclass
          AND contype = 'f'
          AND confrelid = 'public.users'::regclass
          AND confdeltype = 'n'
          AND pg_get_constraintdef(oid) LIKE '%FOREIGN KEY (admin_id)%'
    ) THEN
        RAISE EXCEPTION 'admin_audit_log.admin_id must reference users(id) ON DELETE SET NULL';
    END IF;

    -- ============================================================
    -- 2. V42 AUDIT INDEXES (must remain)
    -- ============================================================
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_admin_audit_log_admin_created'),
            ('idx_admin_audit_log_entity'),
            ('idx_admin_audit_log_created')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'admin_audit_log is missing V42 index(es): %', missing;
    END IF;

    -- ============================================================
    -- 3. V55 AUDIT SEARCH INDEXES
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_admin_audit_log_action_created'
          AND indexdef LIKE '%action%'
          AND indexdef LIKE '%created_at%'
    ) THEN
        RAISE EXCEPTION 'idx_admin_audit_log_action_created must cover (action, created_at)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_admin_audit_log_entity_type_created'
          AND indexdef LIKE '%entity_type%'
          AND indexdef LIKE '%created_at%'
    ) THEN
        RAISE EXCEPTION 'idx_admin_audit_log_entity_type_created must cover (entity_type, created_at)';
    END IF;

    -- ============================================================
    -- 4. DELETED OBJECT LISTS
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'posts'
          AND column_name = 'deleted_at'
    ) THEN
        RAISE EXCEPTION 'posts.deleted_at is required by the deleted-requests list';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'reviews'
          AND column_name = 'deleted_at'
    ) THEN
        RAISE EXCEPTION 'reviews.deleted_at is required by the deleted-reviews list';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_posts_deleted_at'
          AND indexdef LIKE '%deleted_at%'
          AND indexdef LIKE '%deleted = true%'
    ) THEN
        RAISE EXCEPTION 'idx_posts_deleted_at must be a partial index on deleted = TRUE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_reviews_deleted_at'
          AND indexdef LIKE '%deleted_at%'
          AND indexdef LIKE '%deleted = true%'
    ) THEN
        RAISE EXCEPTION 'idx_reviews_deleted_at must be a partial index on deleted = TRUE';
    END IF;

    -- ============================================================
    -- 5. MODERATION SLA QUEUE
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_reports_open_queue'
          AND indexdef LIKE '%created_at%'
          AND indexdef LIKE '%OPEN%'
          AND indexdef LIKE '%IN_REVIEW%'
    ) THEN
        RAISE EXCEPTION 'idx_reports_open_queue must be a partial index over OPEN/IN_REVIEW reports';
    END IF;

    -- ============================================================
    -- 6. ACTIVE SESSIONS
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'auth_sessions'
          AND column_name = 'revoked_at'
    ) THEN
        RAISE EXCEPTION 'auth_sessions.revoked_at is required by the active-session index';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_auth_sessions_active_user_created'
          AND indexdef LIKE '%user_id%'
          AND indexdef LIKE '%created_at%'
          AND indexdef LIKE '%revoked_at IS NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_auth_sessions_active_user_created must be a partial index on revoked_at IS NULL';
    END IF;

    -- ============================================================
    -- 7. DATA INVARIANTS
    -- ============================================================
    IF EXISTS (
        SELECT 1 FROM admin_audit_log
        WHERE btrim(action) = '' OR btrim(entity_type) = ''
    ) THEN
        RAISE EXCEPTION 'admin_audit_log contains a blank action or entity_type';
    END IF;

    IF EXISTS (
        SELECT 1 FROM admin_audit_log
        WHERE length(reason) > 1000
    ) THEN
        RAISE EXCEPTION 'admin_audit_log contains a reason longer than 1000 characters';
    END IF;

    IF EXISTS (
        SELECT 1 FROM admin_audit_log
        WHERE created_at IS NULL
    ) THEN
        RAISE EXCEPTION 'admin_audit_log contains a row without created_at';
    END IF;
END $$;
