-- V53: user-to-user blocks (Block 16).
--
-- The V42 `user_blocks` table is a moderation/debt journal: it is owned by the
-- platform and describes an account-level sanction. A user-initiated block is a
-- different domain object with a different lifecycle (the user creates and
-- removes it) and a different contract (`scope`, `expires_at`). It therefore
-- gets its own table instead of overloading the moderation journal.
--
-- The pair (blocker_id, blocked_id) is unique: one row per ordered pair. The
-- service treats POST as an upsert so a client can change the scope or expiry of
-- an existing block without creating a second row.
CREATE TABLE IF NOT EXISTS user_blocks_relations (
    id BIGSERIAL PRIMARY KEY,
    blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(20) NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    CONSTRAINT chk_user_blocks_relations_scope
        CHECK (scope IN ('ALL', 'MESSAGES', 'PROFILE', 'HELP_REQUESTS')),
    CONSTRAINT chk_user_blocks_relations_self
        CHECK (blocker_id <> blocked_id),
    CONSTRAINT chk_user_blocks_relations_period
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT uq_user_blocks_relations_pair
        UNIQUE (blocker_id, blocked_id)
);

-- A block is resolved in both directions: "did I block them" and "did they
-- block me". These indexes keep each side a single index range scan; the
-- expiry column is part of the index because every read filters it.
CREATE INDEX IF NOT EXISTS idx_user_blocks_relations_blocker
    ON user_blocks_relations (blocker_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_user_blocks_relations_blocked
    ON user_blocks_relations (blocked_id, expires_at);
