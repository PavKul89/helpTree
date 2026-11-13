-- Verify the Block 23 ShedLock schema contract after V66.

DO $$
DECLARE
    missing TEXT;
BEGIN
    IF to_regclass('public.shedlock') IS NULL THEN
        RAISE EXCEPTION 'Block 23 schema is missing table shedlock';
    END IF;

    SELECT string_agg(expected.name || ' ' || expected.type, ', ')
    INTO missing
    FROM (
        VALUES
            ('name', 'character varying', 64, 'NO'),
            ('lock_until', 'timestamp without time zone', NULL::integer, 'NO'),
            ('locked_at', 'timestamp without time zone', NULL::integer, 'NO'),
            ('locked_by', 'character varying', 255, 'NO')
    ) AS expected(name, type, max_len, nullable)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'shedlock'
          AND c.column_name = expected.name
          AND c.data_type = expected.type
          AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_len
          AND c.is_nullable = expected.nullable
    );

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'shedlock has missing or mistyped column(s): %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.shedlock'::regclass
          AND contype = 'p'
          AND pg_get_constraintdef(oid) = 'PRIMARY KEY (name)'
    ) THEN
        RAISE EXCEPTION 'shedlock is missing its primary key on name';
    END IF;
END
$$;
