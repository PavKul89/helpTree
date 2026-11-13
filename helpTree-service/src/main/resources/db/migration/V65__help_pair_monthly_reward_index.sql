CREATE INDEX IF NOT EXISTS idx_helps_confirmed_pair_month
    ON helps (helper_id, receiver_id, confirmed_at, id)
    WHERE status = 'CONFIRMED'
      AND confirmed_at IS NOT NULL
      AND COALESCE(deleted, FALSE) = FALSE;
