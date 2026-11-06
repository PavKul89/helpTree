-- V49: Block 12 - shared rate-limit counters and login lockout state.
--
-- The previous rate limiter kept its counters in a ConcurrentHashMap inside one
-- JVM. Two instances therefore each allowed a full quota, the login lockout was
-- also local, and neither map was ever evicted. This migration moves both pieces
-- of state into the database that every instance already shares, so an atomic
-- upsert is the single source of truth.
--
-- A row in rate_limit_buckets is a fixed-window counter. The window is reset
-- lazily in the same statement that increments it (see JdbcRateLimitStore), and
-- expires_at lets the maintenance job delete idle rows in bounded batches.
-- login_locks stores only an opaque SHA-256 key, never an email address.
--
-- Both tables are forward-only and additive. No existing data is touched.

CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    bucket_key VARCHAR(255) PRIMARY KEY,
    window_started_at TIMESTAMP NOT NULL,
    request_count INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_rate_limit_buckets_count CHECK (request_count > 0)
);

-- The maintenance job deletes rows strictly older than now; the index keeps
-- that scan proportional to the expired set instead of the whole table.
CREATE INDEX IF NOT EXISTS idx_rate_limit_buckets_expires_at
    ON rate_limit_buckets (expires_at);

CREATE TABLE IF NOT EXISTS login_locks (
    lock_key VARCHAR(255) PRIMARY KEY,
    locked_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

-- Expired locks are ignored by the lookup and removed by the maintenance job.
CREATE INDEX IF NOT EXISTS idx_login_locks_locked_until
    ON login_locks (locked_until);

COMMENT ON TABLE rate_limit_buckets IS
    'Fixed-window request counters shared by all application instances (Block 12).';
COMMENT ON TABLE login_locks IS
    'Temporary per-(IP, account) login lockouts; keys are SHA-256 hashes (Block 12).';
