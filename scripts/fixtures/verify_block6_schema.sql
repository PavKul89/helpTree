-- Verify the Block 6 schema contract after V42.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('reports'),
            ('report_events'),
            ('moderation_actions'),
            ('user_blocks'),
            ('notifications'),
            ('notification_preferences'),
            ('admin_audit_log'),
            ('outbox_events'),
            ('chat_user_state'),
            ('idx_users_status'),
            ('idx_users_city'),
            ('idx_posts_author_name'),
            ('idx_comments_created_at'),
            ('idx_messages_created'),
            ('idx_achievements_user_type'),
            ('idx_posts_deleted'),
            ('idx_helps_deleted'),
            ('idx_messages_deleted_at'),
            ('uq_users_email_normalized'),
            ('idx_auth_refresh_tokens_expires_at'),
            ('uq_helps_active_post'),
            ('uq_chat_users'),
            ('uq_chats_normalized_pair')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 6 schema is missing relation(s): %', missing;
    END IF;

    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('chk_users_rating_range'),
            ('chk_users_debt_count_non_negative'),
            ('chk_users_help_coins_non_negative'),
            ('chk_chats_distinct_users'),
            ('chk_chats_canonical_order')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = expected.name
    );

    -- V62 intentionally drops fk_chat_user_state_last_read when messages are
    -- partitioned by created_at: the column remains as historical state, but it
    -- can no longer reference a single-column key in the partitioned table.

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 6 schema is missing constraint(s): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_achievements_user_type'
          AND indexdef LIKE 'CREATE UNIQUE INDEX%'
          AND indexdef LIKE '%(user_id, type)%'
    ) THEN
        RAISE EXCEPTION 'idx_achievements_user_type must be unique on (user_id, type)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_users_email_normalized'
          AND indexdef LIKE 'CREATE UNIQUE INDEX%'
          AND indexdef LIKE '%lower(btrim%'
    ) THEN
        RAISE EXCEPTION 'uq_users_email_normalized must be a unique normalized email index';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_helps_active_post'
          AND indexdef LIKE '%CONFIRMED%'
    ) THEN
        RAISE EXCEPTION 'uq_helps_active_post must cover ACCEPTED, COMPLETED and CONFIRMED helps';
    END IF;
END $$;