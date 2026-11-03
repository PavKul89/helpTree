-- V37: secure chat lifecycle.
--
-- Goals:
--   * one chat per unordered participant pair, even under concurrent creation;
--   * per-user soft deletion without touching the other participant's history;
--   * per-user read state (last_read_message_id) instead of a global is_read flag;
--   * soft-deleted messages that remain in the history table.
--
-- Existing duplicate chats are merged before the unique index is created. Messages
-- are re-pointed to the surviving chat so no history is lost.

-- ============================================================
-- 1. MERGE DUPLICATE UNORDERED PAIRS
-- ============================================================
ALTER TABLE chats DROP CONSTRAINT IF EXISTS uq_chat_users;

CREATE TEMP TABLE _chat_dedupe ON COMMIT DROP AS
SELECT
    c.id,
    MIN(c.id) OVER (PARTITION BY LEAST(c.user1_id, c.user2_id), GREATEST(c.user1_id, c.user2_id)) AS keep_id,
    ROW_NUMBER() OVER (PARTITION BY LEAST(c.user1_id, c.user2_id), GREATEST(c.user1_id, c.user2_id) ORDER BY c.id) AS rn,
    MAX(c.last_message_at) OVER (PARTITION BY LEAST(c.user1_id, c.user2_id), GREATEST(c.user1_id, c.user2_id)) AS max_last_message_at
FROM chats c;

UPDATE messages m
SET chat_id = d.keep_id
FROM _chat_dedupe d
WHERE m.chat_id = d.id
  AND d.rn > 1;

UPDATE chats c
SET last_message_at = d.max_last_message_at
FROM _chat_dedupe d
WHERE c.id = d.keep_id
  AND d.rn = 1;

DELETE FROM chats c
USING _chat_dedupe d
WHERE c.id = d.id
  AND d.rn > 1;

DROP TABLE _chat_dedupe;

-- Store the pair in canonical order. This makes the legacy (user1, user2) unique
-- constraint meaningful and keeps application queries deterministic.
UPDATE chats
SET user1_id = user2_id,
    user2_id = user1_id
WHERE user1_id > user2_id;

ALTER TABLE chats
    ADD CONSTRAINT uq_chat_users UNIQUE (user1_id, user2_id);

-- Backstop for any legacy writer that ignores canonical ordering.
CREATE UNIQUE INDEX IF NOT EXISTS uq_chats_normalized_pair
    ON chats (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));

-- ============================================================
-- 2. PER-USER CHAT STATE
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_user_state (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    deleted_at TIMESTAMP NULL,
    last_read_message_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_user_state UNIQUE (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_user_state_user
    ON chat_user_state (user_id, deleted_at);

-- Backfill state for both participants of every existing chat. The last read
-- message is the newest already-read message from the other participant, so the
-- computed unread count matches the old is_read semantics.
INSERT INTO chat_user_state (chat_id, user_id, last_read_message_id, created_at, updated_at)
SELECT
    c.id,
    p.user_id,
    (
        SELECT MAX(m.id)
        FROM messages m
        WHERE m.chat_id = c.id
          AND m.sender_id <> p.user_id
          AND m.is_read = TRUE
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM chats c
CROSS JOIN LATERAL (VALUES (c.user1_id), (c.user2_id)) AS p(user_id)
ON CONFLICT (chat_id, user_id) DO NOTHING;

-- ============================================================
-- 3. SOFT-DELETED MESSAGES
-- ============================================================
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_by_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_messages_chat_id_id
    ON messages (chat_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_messages_active_chat
    ON messages (chat_id, id DESC)
    WHERE deleted_at IS NULL;
