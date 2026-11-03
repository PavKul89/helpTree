-- V38: Help lifecycle integrity.
--
-- Goals:
--   * one active help per post, including under concurrent accepts;
--   * an append-only status history for every help fact;
--   * stored cancellation reason/initiator/time;
--   * database-level guards against repeated coin and achievement awards.
--
-- The migration is intentionally defensive: duplicate active helps are resolved
-- deterministically before the unique index is created, and history is backfilled
-- only once per help.

-- ============================================================
-- 1. CANCELLATION METADATA
-- ============================================================
ALTER TABLE helps ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500) NULL;
ALTER TABLE helps ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP NULL;
ALTER TABLE helps ADD COLUMN IF NOT EXISTS cancelled_by_id BIGINT NULL;
ALTER TABLE helps ADD COLUMN IF NOT EXISTS cancel_initiator VARCHAR(20) NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_helps_cancelled_by'
    ) THEN
        ALTER TABLE helps
            ADD CONSTRAINT fk_helps_cancelled_by
            FOREIGN KEY (cancelled_by_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Existing cancellations did not store a reason: mark them as historical/system.
UPDATE helps
SET cancel_initiator = COALESCE(cancel_initiator, 'SYSTEM'),
    cancel_reason = COALESCE(cancel_reason, 'Историческая запись: причина не сохранялась'),
    cancelled_at = COALESCE(cancelled_at, updated_at)
WHERE status = 'CANCELLED';

-- ============================================================
-- 2. STATUS HISTORY
-- ============================================================
CREATE TABLE IF NOT EXISTS help_status_history (
    id BIGSERIAL PRIMARY KEY,
    help_id BIGINT NOT NULL REFERENCES helps(id) ON DELETE CASCADE,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    actor_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_help_history_status CHECK (
        to_status IN ('ACCEPTED', 'COMPLETED', 'CONFIRMED', 'CANCELLED')
    ),
    CONSTRAINT chk_help_history_actor_role CHECK (
        actor_role IN ('HELPER', 'RECEIVER', 'ADMIN', 'SYSTEM')
    )
);

CREATE INDEX IF NOT EXISTS idx_help_status_history_help_id
    ON help_status_history (help_id, created_at);

CREATE INDEX IF NOT EXISTS idx_help_status_history_actor
    ON help_status_history (actor_id);

-- ============================================================
-- 3. RESOLVE DUPLICATE ACTIVE HELPS
-- ============================================================
-- Keep the most advanced help per post (CONFIRMED > COMPLETED > ACCEPTED) and
-- the oldest one on ties. The others are cancelled with a system reason.
WITH ranked AS (
    SELECT h.id,
           ROW_NUMBER() OVER (
               PARTITION BY h.post_id
               ORDER BY CASE h.status
                            WHEN 'CONFIRMED' THEN 1
                            WHEN 'COMPLETED' THEN 2
                            WHEN 'ACCEPTED'  THEN 3
                            ELSE 4
                        END,
                        h.id
           ) AS rn
    FROM helps h
    WHERE h.status IN ('ACCEPTED', 'COMPLETED', 'CONFIRMED')
      AND h.deleted = FALSE
)
UPDATE helps h
SET status = 'CANCELLED',
    cancel_reason = 'Автоматическая отмена дубликата активной помощи при миграции',
    cancel_initiator = 'SYSTEM',
    cancelled_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
FROM ranked r
WHERE h.id = r.id AND r.rn > 1;

-- ============================================================
-- 4. BACKFILL HISTORY (one row per known transition)
-- ============================================================
INSERT INTO help_status_history (help_id, from_status, to_status, actor_id, actor_role, reason, created_at)
SELECT h.id, NULL, 'ACCEPTED', h.helper_id, 'HELPER', NULL, COALESCE(h.accepted_at, h.created_at)
FROM helps h
WHERE COALESCE(h.accepted_at, h.created_at) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM help_status_history x WHERE x.help_id = h.id AND x.to_status = 'ACCEPTED'
  );

INSERT INTO help_status_history (help_id, from_status, to_status, actor_id, actor_role, reason, created_at)
SELECT h.id, 'ACCEPTED', 'COMPLETED', h.helper_id, 'HELPER', NULL, h.completed_at
FROM helps h
WHERE h.completed_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM help_status_history x WHERE x.help_id = h.id AND x.to_status = 'COMPLETED'
  );

INSERT INTO help_status_history (help_id, from_status, to_status, actor_id, actor_role, reason, created_at)
SELECT h.id, 'COMPLETED', 'CONFIRMED', h.receiver_id, 'RECEIVER', NULL, h.confirmed_at
FROM helps h
WHERE h.confirmed_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM help_status_history x WHERE x.help_id = h.id AND x.to_status = 'CONFIRMED'
  );

INSERT INTO help_status_history (help_id, from_status, to_status, actor_id, actor_role, reason, created_at)
SELECT h.id,
       CASE WHEN h.accepted_at IS NOT NULL THEN 'ACCEPTED' ELSE NULL END,
       'CANCELLED',
       h.cancelled_by_id,
       COALESCE(h.cancel_initiator, 'SYSTEM'),
       h.cancel_reason,
       COALESCE(h.cancelled_at, h.updated_at)
FROM helps h
WHERE h.status = 'CANCELLED'
  AND NOT EXISTS (
      SELECT 1 FROM help_status_history x WHERE x.help_id = h.id AND x.to_status = 'CANCELLED'
  );

-- ============================================================
-- 5. DATABASE GUARDS
-- ============================================================
ALTER TABLE helps DROP CONSTRAINT IF EXISTS chk_helps_status;
ALTER TABLE helps
    ADD CONSTRAINT chk_helps_status CHECK (
        status IN ('ACCEPTED', 'COMPLETED', 'CONFIRMED', 'CANCELLED')
    );

ALTER TABLE helps DROP CONSTRAINT IF EXISTS chk_helps_distinct_users;
ALTER TABLE helps
    ADD CONSTRAINT chk_helps_distinct_users CHECK (helper_id <> receiver_id);

ALTER TABLE helps DROP CONSTRAINT IF EXISTS chk_helps_cancel_initiator;
ALTER TABLE helps
    ADD CONSTRAINT chk_helps_cancel_initiator CHECK (
        cancel_initiator IS NULL OR cancel_initiator IN ('HELPER', 'RECEIVER', 'ADMIN', 'SYSTEM')
    );

-- Backstop for acceptHelp: only one ACCEPTED/COMPLETED help may exist per post.
CREATE UNIQUE INDEX IF NOT EXISTS uq_helps_active_post
    ON helps (post_id)
    WHERE status IN ('ACCEPTED', 'COMPLETED') AND deleted = FALSE;

-- Speeds up the stale-ACCEPTED SLA sweep.
CREATE INDEX IF NOT EXISTS idx_helps_status_accepted_at
    ON helps (status, accepted_at)
    WHERE deleted = FALSE;

-- One HELP_GIVEN / HELP_RECEIVED transaction per user and post. A repeated
-- confirmation therefore fails at the database level too.
CREATE UNIQUE INDEX IF NOT EXISTS uq_coin_help_given_per_post
    ON coin_transactions (user_id, related_post_id)
    WHERE type = 'HELP_GIVEN' AND related_post_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_coin_help_received_per_post
    ON coin_transactions (user_id, related_post_id)
    WHERE type = 'HELP_RECEIVED' AND related_post_id IS NOT NULL;

-- One achievement of each type per user. The application uses ON CONFLICT DO
-- NOTHING, so a concurrent duplicate is a no-op instead of a failed transaction.
DELETE FROM achievements a
USING achievements b
WHERE a.user_id = b.user_id
  AND a.type = b.type
  AND a.id > b.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_achievements_user_type
    ON achievements (user_id, type);
