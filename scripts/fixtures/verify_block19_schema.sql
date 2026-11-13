\set ON_ERROR_STOP on

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'helps'
          AND column_name = 'completed_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        RAISE EXCEPTION 'helps.completed_at must exist as timestamp without time zone';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class idx ON idx.oid = i.indexrelid
        JOIN pg_class tbl ON tbl.oid = i.indrelid
        JOIN pg_namespace n ON n.oid = tbl.relnamespace
        WHERE n.nspname = 'public'
          AND tbl.relname = 'helps'
          AND idx.relname = 'idx_helps_completed_sla'
          AND pg_get_indexdef(i.indexrelid, 1, TRUE) = 'completed_at'
          AND lower(pg_get_expr(i.indpred, i.indrelid)) LIKE '%status%'
          AND lower(pg_get_expr(i.indpred, i.indrelid)) LIKE '%completed%'
          AND lower(pg_get_expr(i.indpred, i.indrelid)) LIKE '%deleted%'
          AND lower(pg_get_expr(i.indpred, i.indrelid)) LIKE '%false%'
          AND lower(pg_get_expr(i.indpred, i.indrelid)) LIKE '%is null%'
    ) THEN
        RAISE EXCEPTION 'idx_helps_completed_sla must cover completed_at for live COMPLETED helps';
    END IF;
END $$;
