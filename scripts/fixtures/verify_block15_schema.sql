-- Verify the Block 15 schema contract after V51.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run after `flyway:migrate`. V51 completes the reports/moderation domain:
--   * reports gains description/internal_note/duplicate_of_id/idempotency_key;
--   * REVIEW becomes a report target, REPORT becomes a moderation target;
--   * report_events gains internal_note and the ACTION_APPLIED event type;
--   * moderation_actions accepts the hide/remove/restore/report decisions;
--   * posts/comments/reviews gain moderation_status independent from the
--     author's soft-delete flag.
-- The script checks the table shape, constraints, indexes and the invariants
-- that keep the public feed and the audit trail consistent.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- ---------------------------------------------------------------
    -- reports: renamed and new columns
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('description', 'text', NULL::integer),
            ('internal_note', 'text', NULL::integer),
            ('duplicate_of_id', 'bigint', NULL::integer),
            ('idempotency_key', 'character varying', 100),
            ('assigned_moderator_id', 'bigint', NULL::integer)
    ) AS expected(name, type, max_len)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'reports'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = 'YES'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'reports has missing or mistyped column(s): %', missing;
    END IF;

    -- The V42 columns were renamed; leaving them behind would split the model.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'reports'
          AND column_name IN ('details', 'assigned_to_id')
    ) THEN
        RAISE EXCEPTION 'reports still exposes the pre-V51 columns details/assigned_to_id';
    END IF;

    -- ---------------------------------------------------------------
    -- report_events: internal_note replaced note
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'report_events'
          AND c.column_name = 'internal_note'
          AND c.data_type = 'text'
          AND c.is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'report_events.internal_note is missing or has the wrong type/nullability';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'report_events'
          AND column_name = 'note'
    ) THEN
        RAISE EXCEPTION 'report_events still exposes the pre-V51 note column';
    END IF;

    -- ---------------------------------------------------------------
    -- moderation_status on user-visible content
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.table_name, ', ')
    INTO missing
    FROM (
        VALUES ('posts'), ('comments'), ('reviews')
    ) AS expected(table_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = expected.table_name
          AND c.column_name = 'moderation_status'
          AND c.data_type = 'character varying'
          AND c.character_maximum_length = 20
          AND c.is_nullable = 'NO'
          AND lower(c.column_default) LIKE '%active%'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'content table(s) missing moderation_status (varchar(20) NOT NULL DEFAULT ACTIVE): %', missing;
    END IF;

    -- ---------------------------------------------------------------
    -- Constraints: report target/reason/status/resolution contract
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_reports_target_type'
          AND conrelid = 'public.reports'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%REVIEW%'
          AND pg_get_constraintdef(oid) NOT LIKE '%''REPORT''%'
    ) THEN
        RAISE EXCEPTION 'chk_reports_target_type must accept REVIEW and reject REPORT as a report target';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_reports_status'
          AND conrelid = 'public.reports'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%OPEN%'
          AND pg_get_constraintdef(oid) LIKE '%IN_REVIEW%'
          AND pg_get_constraintdef(oid) LIKE '%RESOLVED%'
          AND pg_get_constraintdef(oid) LIKE '%REJECTED%'
          AND pg_get_constraintdef(oid) LIKE '%DUPLICATE%'
    ) THEN
        RAISE EXCEPTION 'reports is missing the OPEN/IN_REVIEW/RESOLVED/REJECTED/DUPLICATE status constraint';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_reports_resolution'
          AND conrelid = 'public.reports'::regclass
          AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'reports is missing chk_reports_resolution';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_reports_description_length'
          AND conrelid = 'public.reports'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%2000%'
    ) THEN
        RAISE EXCEPTION 'reports is missing the 2000 character description limit';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_reports_idempotency_key'
          AND conrelid = 'public.reports'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%100%'
    ) THEN
        RAISE EXCEPTION 'reports is missing chk_reports_idempotency_key';
    END IF;

    -- ---------------------------------------------------------------
    -- Duplicate link has to survive deleting the original report.
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'reports_duplicate_of_id_fkey'
          AND conrelid = 'public.reports'::regclass
          AND contype = 'f'
          AND confrelid = 'public.reports'::regclass
          AND confdeltype = 'n'
    ) THEN
        RAISE EXCEPTION 'reports.duplicate_of_id must reference reports(id) ON DELETE SET NULL';
    END IF;

    -- ---------------------------------------------------------------
    -- report_events / moderation_actions vocabularies
    -- ---------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_report_events_type'
          AND conrelid = 'public.report_events'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%ACTION_APPLIED%'
          AND pg_get_constraintdef(oid) LIKE '%LINKED_DUPLICATE%'
          AND pg_get_constraintdef(oid) LIKE '%REOPENED%'
    ) THEN
        RAISE EXCEPTION 'chk_report_events_type is missing Block 15 event types';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_moderation_actions_target_type'
          AND conrelid = 'public.moderation_actions'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%''REPORT''%'
          AND pg_get_constraintdef(oid) LIKE '%''REVIEW''%'
    ) THEN
        RAISE EXCEPTION 'chk_moderation_actions_target_type must accept REPORT and REVIEW';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_moderation_actions_type'
          AND conrelid = 'public.moderation_actions'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%HIDE_CONTENT%'
          AND pg_get_constraintdef(oid) LIKE '%REMOVE_CONTENT%'
          AND pg_get_constraintdef(oid) LIKE '%RESTORE_CONTENT%'
          AND pg_get_constraintdef(oid) LIKE '%RESOLVE_REPORT%'
          AND pg_get_constraintdef(oid) LIKE '%LINK_DUPLICATE%'
    ) THEN
        RAISE EXCEPTION 'chk_moderation_actions_type is missing Block 15 action types';
    END IF;

    -- ---------------------------------------------------------------
    -- moderation_status CHECK constraints
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.table_name, ', ')
    INTO missing
    FROM (
        VALUES ('posts'), ('comments'), ('reviews')
    ) AS expected(table_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_' || expected.table_name || '_moderation_status'
          AND conrelid = ('public.' || expected.table_name)::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%HIDDEN%'
          AND pg_get_constraintdef(oid) LIKE '%REMOVED%'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'content table(s) missing the ACTIVE/HIDDEN/REMOVED check: %', missing;
    END IF;

    -- ---------------------------------------------------------------
    -- Indexes
    -- ---------------------------------------------------------------
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('uq_reports_open_reporter_target'),
            ('uq_reports_reporter_idempotency'),
            ('idx_reports_duplicate_of'),
            ('idx_reports_reason'),
            ('idx_reports_priority_created'),
            ('idx_reports_status_created'),
            ('idx_reports_target'),
            ('idx_reports_reporter_created'),
            ('idx_reports_assigned'),
            ('idx_report_events_report_created'),
            ('idx_moderation_actions_target'),
            ('idx_moderation_actions_report'),
            ('idx_posts_moderation_status'),
            ('idx_posts_public_moderated_feed'),
            ('idx_comments_moderation_status'),
            ('idx_comments_public_thread'),
            ('idx_reviews_moderation_status'),
            ('idx_reviews_public_recipient'),
            ('idx_user_blocks_active_moderation')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 15 schema is missing index(es): %', missing;
    END IF;

    -- One open report per reporter+target: the guard against duplicate spam.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_reports_open_reporter_target'
          AND indexdef LIKE 'CREATE UNIQUE INDEX%'
          AND indexdef LIKE '%reporter_id%'
          AND indexdef LIKE '%target_type%'
          AND indexdef LIKE '%target_id%'
          AND indexdef LIKE '%OPEN%'
          AND indexdef LIKE '%IN_REVIEW%'
    ) THEN
        RAISE EXCEPTION 'uq_reports_open_reporter_target must be a partial unique index on open reports';
    END IF;

    -- Idempotency is scoped to the reporter and ignores NULL keys.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_reports_reporter_idempotency'
          AND indexdef LIKE 'CREATE UNIQUE INDEX%'
          AND indexdef LIKE '%reporter_id%'
          AND indexdef LIKE '%idempotency_key%'
          AND indexdef LIKE '%IS NOT NULL%'
    ) THEN
        RAISE EXCEPTION 'uq_reports_reporter_idempotency must be a partial unique index on (reporter_id, idempotency_key)';
    END IF;

    -- The public feed indexes must only cover visible, non-deleted rows.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_posts_public_moderated_feed'
          AND indexdef LIKE '%ACTIVE%'
          AND indexdef LIKE '%deleted%'
          AND indexdef LIKE '%CANCELLED%'
    ) THEN
        RAISE EXCEPTION 'idx_posts_public_moderated_feed must be partial on ACTIVE, non-deleted, non-cancelled posts';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_comments_public_thread'
          AND indexdef LIKE '%ACTIVE%'
          AND indexdef LIKE '%deleted%'
    ) THEN
        RAISE EXCEPTION 'idx_comments_public_thread must be partial on ACTIVE and non-deleted comments';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_reviews_public_recipient'
          AND indexdef LIKE '%ACTIVE%'
          AND indexdef LIKE '%deleted%'
    ) THEN
        RAISE EXCEPTION 'idx_reviews_public_recipient must be partial on ACTIVE and non-deleted reviews';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_user_blocks_active_moderation'
          AND indexdef LIKE '%MODERATION%'
          AND indexdef LIKE '%lifted_at IS NULL%'
    ) THEN
        RAISE EXCEPTION 'idx_user_blocks_active_moderation must be partial on active MODERATION blocks';
    END IF;

    -- ---------------------------------------------------------------
    -- Data invariants
    -- ---------------------------------------------------------------
    -- Closed reports always carry a resolution timestamp; open ones never do.
    IF EXISTS (
        SELECT 1
        FROM reports
        WHERE (status IN ('RESOLVED', 'REJECTED', 'DUPLICATE') AND resolved_at IS NULL)
           OR (status IN ('OPEN', 'IN_REVIEW') AND resolved_at IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'reports contains a row whose status and resolved_at disagree';
    END IF;

    -- A report cannot be a duplicate of itself.
    IF EXISTS (
        SELECT 1
        FROM reports
        WHERE duplicate_of_id IS NOT NULL
          AND duplicate_of_id = id
    ) THEN
        RAISE EXCEPTION 'reports contains a self-referencing duplicate_of_id';
    END IF;

    -- moderation_status is terminal only for rows that still exist; the CHECK
    -- constraint already rejects unknown values, this catches a manual import.
    IF EXISTS (
        SELECT 1 FROM posts WHERE moderation_status NOT IN ('ACTIVE', 'HIDDEN', 'REMOVED')
        UNION ALL
        SELECT 1 FROM comments WHERE moderation_status NOT IN ('ACTIVE', 'HIDDEN', 'REMOVED')
        UNION ALL
        SELECT 1 FROM reviews WHERE moderation_status NOT IN ('ACTIVE', 'HIDDEN', 'REMOVED')
    ) THEN
        RAISE EXCEPTION 'content tables contain an unknown moderation_status value';
    END IF;
END $$;
