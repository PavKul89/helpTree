-- Verify the Block 12 schema contract after V49.
-- Raises an exception (non-zero psql exit with ON_ERROR_STOP) on any mismatch.
--
-- Run after `flyway:migrate`. V49 adds the shared rate-limit counter table and
-- the login-lock table. The script checks the table shape, the constraints that
-- keep counters sane, the TTL indexes used by the cleanup job and the key
-- length that bounds every application-generated key.

\set ON_ERROR_STOP on

DO $$
DECLARE
    missing TEXT;
BEGIN
    IF to_regclass('public.rate_limit_buckets') IS NULL THEN
        RAISE EXCEPTION 'Block 12 schema is missing table rate_limit_buckets';
    END IF;

    IF to_regclass('public.login_locks') IS NULL THEN
        RAISE EXCEPTION 'Block 12 schema is missing table login_locks';
    END IF;

    -- Columns and their types: the store maps these directly, so a silent type
    -- drift (for example INTEGER -> BIGINT) must fail the check.
    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('bucket_key', 'character varying', 255),
            ('window_started_at', 'timestamp without time zone', NULL::integer),
            ('request_count', 'integer', NULL::integer),
            ('expires_at', 'timestamp without time zone', NULL::integer)
    ) AS expected(name, type, max_len)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'rate_limit_buckets'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = 'NO'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'rate_limit_buckets has missing or mistyped column(s): %', missing;
    END IF;

    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('lock_key', 'character varying', 255),
            ('locked_until', 'timestamp without time zone', NULL::integer),
            ('created_at', 'timestamp without time zone', NULL::integer)
    ) AS expected(name, type, max_len)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'login_locks'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = 'NO'
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'login_locks has missing or mistyped column(s): %', missing;
    END IF;

    -- created_at must default to the clock the store queries use (LOCALTIMESTAMP)
    -- so rows created by other writers stay comparable with locked_until.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'login_locks'
          AND column_name = 'created_at'
          AND column_default IS NOT NULL
          AND upper(column_default) LIKE '%LOCALTIMESTAMP%'
    ) THEN
        RAISE EXCEPTION 'login_locks.created_at must default to LOCALTIMESTAMP';
    END IF;

    -- Both keys are primary keys: the atomic upsert relies on ON CONFLICT being
    -- able to resolve the row in a single statement.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'rate_limit_buckets_pkey'
          AND conrelid = 'public.rate_limit_buckets'::regclass
          AND contype = 'p'
    ) THEN
        RAISE EXCEPTION 'rate_limit_buckets is missing its primary key';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'login_locks_pkey'
          AND conrelid = 'public.login_locks'::regclass
          AND contype = 'p'
    ) THEN
        RAISE EXCEPTION 'login_locks is missing its primary key';
    END IF;

    -- A zero or negative counter would make the limiter reject the very first
    -- request forever, so the constraint is part of the contract.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_rate_limit_buckets_count'
          AND conrelid = 'public.rate_limit_buckets'::regclass
          AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'rate_limit_buckets is missing chk_rate_limit_buckets_count';
    END IF;

    -- Cleanup scans must be able to find expired rows without a full table scan.
    SELECT string_agg(expected.name, ', ')
    INTO missing
    FROM (
        VALUES
            ('idx_rate_limit_buckets_expires_at'),
            ('idx_login_locks_locked_until')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Block 12 schema is missing TTL index(es): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_rate_limit_buckets_expires_at'
          AND indexdef LIKE '%expires_at%'
    ) THEN
        RAISE EXCEPTION 'idx_rate_limit_buckets_expires_at must cover expires_at';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_login_locks_locked_until'
          AND indexdef LIKE '%locked_until%'
    ) THEN
        RAISE EXCEPTION 'idx_login_locks_locked_until must cover locked_until';
    END IF;

    -- The application hashes oversized keys down to 255 characters; a longer
    -- literal key means a caller bypassed RateLimitKeys.
    IF EXISTS (
        SELECT 1 FROM rate_limit_buckets WHERE length(bucket_key) > 255
    ) THEN
        RAISE EXCEPTION 'rate_limit_buckets contains a key longer than 255 characters';
    END IF;

    IF EXISTS (
        SELECT 1 FROM login_locks WHERE length(lock_key) > 255
    ) THEN
        RAISE EXCEPTION 'login_locks contains a key longer than 255 characters';
    END IF;

    -- Login lock keys are SHA-256 hashes wrapped in a fixed prefix. A raw email
    -- would indicate the store was bypassed and leaks personal data.
    IF EXISTS (
        SELECT 1
        FROM login_locks
        WHERE lock_key !~ '^login-lock:[0-9a-f]{64}$'
    ) THEN
        RAISE EXCEPTION 'login_locks contains a key that is not a hashed (IP, account) pair';
    END IF;

    -- Counters are always positive and their window has a positive duration.
    IF EXISTS (
        SELECT 1
        FROM rate_limit_buckets
        WHERE request_count <= 0
           OR window_started_at >= expires_at
    ) THEN
        RAISE EXCEPTION 'rate_limit_buckets contains an invalid counter or window';
    END IF;

    -- Locks must have a future deadline at rest only until they expire; an
    -- expired row is legitimate until the cleanup job removes it. Guard against
    -- a nonsensical deadline that would lock a pair for years.
    IF EXISTS (
        SELECT 1
        FROM login_locks
        WHERE locked_until > LOCALTIMESTAMP + INTERVAL '1 day'
    ) THEN
        RAISE EXCEPTION 'login_locks contains a lock longer than the one-day policy maximum';
    END IF;
END $$;
