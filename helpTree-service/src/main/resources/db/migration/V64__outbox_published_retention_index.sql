-- Speeds bounded retention cleanup without indexing pending/retry/dead rows.
CREATE INDEX IF NOT EXISTS idx_outbox_events_published_retention
    ON outbox_events (published_at, id, created_at)
    WHERE status = 'PUBLISHED' AND published_at IS NOT NULL;
