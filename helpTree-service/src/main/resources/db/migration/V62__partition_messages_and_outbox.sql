-- V62: Block 21.7 — range-partition the two append-only hot tables.
--
-- `messages` and `outbox_events` grow forever today. Both are queried by a
-- narrow, time-ordered access pattern, so monthly range partitions keep index
-- sizes and vacuum cost bounded and let operators drop a whole month at once.
--
-- The conversion copies every row, so it is safe on an existing database:
--   1. detach the id sequence so dropping the legacy table cannot drop it;
--   2. rename the legacy table and drop its indexes (names are reused below);
--   3. create the partitioned table with a default partition plus a monthly
--      window that covers all existing rows and the near future;
--   4. copy all rows (they route into the right partition);
--   5. restore the sequence, drop the legacy table and recreate every index.
--
-- Partitioned primary keys must include the partition key, so both tables get
-- PRIMARY KEY (id, created_at). The id remains globally unique because it is
-- backed by the original sequence and Hibernate's identity insert keeps using
-- RETURNING id. `chat_user_state.last_read_message_id` can no longer reference
-- a single-column key; the FK is dropped and the column stays as plain history
-- state (the application already tolerates a missing message).
--
-- The outbox dedupe index becomes non-unique because a unique index on a
-- partitioned table must include the partition key. The application does not
-- rely on database-level dedupe (findByDedupeKey is not used on the write path).

-- ============================================================
-- 1. MESSAGES: DROP THE MESSAGE FK AND DETACH THE SEQUENCE
-- ============================================================
ALTER TABLE chat_user_state
    DROP CONSTRAINT IF EXISTS fk_chat_user_state_last_read;

ALTER SEQUENCE IF EXISTS messages_id_seq OWNED BY NONE;

DROP INDEX IF EXISTS idx_messages_chat_id;
DROP INDEX IF EXISTS idx_messages_sender_id;
DROP INDEX IF EXISTS idx_messages_is_read;
DROP INDEX IF EXISTS idx_messages_deleted_at;
DROP INDEX IF EXISTS idx_messages_chat_id_id;
DROP INDEX IF EXISTS idx_messages_active_chat;
DROP INDEX IF EXISTS idx_messages_created;
DROP INDEX IF EXISTS idx_messages_retention;

ALTER TABLE messages RENAME TO messages_legacy_21;

CREATE TABLE messages (
    id BIGINT NOT NULL DEFAULT nextval('messages_id_seq'),
    chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    deleted_by_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    edited_at TIMESTAMP NULL,
    edit_count INTEGER NOT NULL DEFAULT 0,
    attachment_urls TEXT NOT NULL DEFAULT '[]',
    CONSTRAINT pk_messages PRIMARY KEY (id, created_at),
    CONSTRAINT chk_messages_edit_count CHECK (edit_count >= 0)
) PARTITION BY RANGE (created_at);

-- Monthly partitions covering every existing row and the next three months.
DO $$
DECLARE
    month_start DATE;
    month_stop DATE;
BEGIN
    SELECT date_trunc('month', COALESCE(MIN(created_at), CURRENT_TIMESTAMP))::date
      INTO month_start
      FROM messages_legacy_21;

    month_stop := (date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '3 months')::date;

    WHILE month_start < month_stop LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS messages_%s PARTITION OF messages FOR VALUES FROM (%L) TO (%L)',
            to_char(month_start, 'YYYY_MM'),
            month_start,
            month_start + INTERVAL '1 month'
        );
        month_start := (month_start + INTERVAL '1 month')::date;
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS messages_default PARTITION OF messages DEFAULT;

INSERT INTO messages (
    id, chat_id, sender_id, content, created_at, is_read,
    deleted_at, deleted_by_id, edited_at, edit_count, attachment_urls
)
SELECT
    id, chat_id, sender_id, content, created_at, is_read,
    deleted_at, deleted_by_id, edited_at, edit_count, attachment_urls
FROM messages_legacy_21;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'messages_id_seq' AND relkind = 'S') THEN
        PERFORM setval(
            'messages_id_seq',
            COALESCE((SELECT MAX(id) FROM messages), 1),
            EXISTS (SELECT 1 FROM messages)
        );
    END IF;
END $$;

DROP TABLE messages_legacy_21;

CREATE INDEX idx_messages_chat_id ON messages (chat_id);
CREATE INDEX idx_messages_sender_id ON messages (sender_id);
CREATE INDEX idx_messages_is_read ON messages (is_read);
CREATE INDEX idx_messages_deleted_at ON messages (deleted_at)
    WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_messages_chat_id_id ON messages (chat_id, id DESC);
CREATE INDEX idx_messages_active_chat ON messages (chat_id, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_created ON messages (created_at);
CREATE INDEX idx_messages_retention ON messages (created_at, id);

-- ============================================================
-- 2. OUTBOX EVENTS
-- ============================================================
ALTER SEQUENCE IF EXISTS outbox_events_id_seq OWNED BY NONE;

DROP INDEX IF EXISTS uq_outbox_events_dedupe;
DROP INDEX IF EXISTS idx_outbox_events_dispatch;
DROP INDEX IF EXISTS idx_outbox_events_aggregate;
DROP INDEX IF EXISTS idx_outbox_events_created;

ALTER TABLE outbox_events RENAME TO outbox_events_legacy_21;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL DEFAULT nextval('outbox_events_id_seq'),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    dedupe_key VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP NULL,
    locked_by VARCHAR(100) NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id, created_at),
    CONSTRAINT chk_outbox_events_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD')
    ),
    CONSTRAINT chk_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_events_aggregate_type_not_blank CHECK (length(btrim(aggregate_type)) > 0),
    CONSTRAINT chk_outbox_events_event_type_not_blank CHECK (length(btrim(event_type)) > 0)
) PARTITION BY RANGE (created_at);

DO $$
DECLARE
    month_start DATE;
    month_stop DATE;
BEGIN
    SELECT date_trunc('month', COALESCE(MIN(created_at), CURRENT_TIMESTAMP))::date
      INTO month_start
      FROM outbox_events_legacy_21;

    month_stop := (date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '3 months')::date;

    WHILE month_start < month_stop LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS outbox_events_%s PARTITION OF outbox_events FOR VALUES FROM (%L) TO (%L)',
            to_char(month_start, 'YYYY_MM'),
            month_start,
            month_start + INTERVAL '1 month'
        );
        month_start := (month_start + INTERVAL '1 month')::date;
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS outbox_events_default PARTITION OF outbox_events DEFAULT;

INSERT INTO outbox_events (
    id, aggregate_type, aggregate_id, event_type, payload, dedupe_key,
    status, attempts, available_at, locked_at, locked_by, last_error,
    created_at, published_at
)
SELECT
    id, aggregate_type, aggregate_id, event_type, payload, dedupe_key,
    status, attempts, available_at, locked_at, locked_by, last_error,
    created_at, published_at
FROM outbox_events_legacy_21;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'outbox_events_id_seq' AND relkind = 'S') THEN
        PERFORM setval(
            'outbox_events_id_seq',
            COALESCE((SELECT MAX(id) FROM outbox_events), 1),
            EXISTS (SELECT 1 FROM outbox_events)
        );
    END IF;
END $$;

DROP TABLE outbox_events_legacy_21;

-- Non-unique on purpose: see the header. It still accelerates dedupe lookups.
CREATE INDEX idx_outbox_events_dedupe ON outbox_events (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_outbox_events_dispatch ON outbox_events (status, available_at, id);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id, created_at DESC);
CREATE INDEX idx_outbox_events_created ON outbox_events (created_at DESC);
