-- V41: restore indexes removed by V33 and enforce data integrity.
--
-- This migration is intentionally forward-only:
--   * V33 is already applied. Editing it would change its Flyway checksum and
--     break validate on every existing database.
--   * V3 is also already applied. It contains literal user ids 1..10, but that
--     block is harmless on a clean database (posts is empty when V3 runs) and
--     cannot be replayed on an existing database. Its historical ownership
--     links are preserved. The guard at the end of this migration refuses to
--     continue if a NULL post owner is found instead of inventing a fixed id.
--
-- Data changes in this migration are limited to values that violate the new
-- invariants:
--   * users.rating outside 1..5 (the legacy "unrated" value 0) becomes 3.0;
--   * users.debt_count NULL becomes 0.
-- Duplicate emails, chats, achievements and active helps are NOT deleted here.
-- They abort the migration with an actionable error so an operator can review
-- the data first.

-- ============================================================
-- 1. USER RATING AND DEBT
-- ============================================================
UPDATE users
SET rating = CASE
    WHEN rating IS NULL OR rating < 1 THEN 3.0
    ELSE 5.0
END
WHERE rating IS NULL OR rating < 1 OR rating > 5;

ALTER TABLE users ALTER COLUMN rating SET DEFAULT 3.0;
ALTER TABLE users ALTER COLUMN rating SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_rating_range'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_rating_range CHECK (rating >= 1 AND rating <= 5);
    END IF;
END $$;

UPDATE users SET debt_count = 0 WHERE debt_count IS NULL;

ALTER TABLE users ALTER COLUMN debt_count SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN debt_count SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_debt_count_non_negative'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_debt_count_non_negative CHECK (debt_count >= 0);
    END IF;
END $$;

-- V39 already added this constraint; keep the guard so a partially restored
-- schema is repaired by the same migration.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_help_coins_non_negative'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_help_coins_non_negative CHECK (help_coins >= 0);
    END IF;
END $$;

-- ============================================================
-- 2. NORMALIZED EMAIL UNIQUENESS
-- ============================================================
DO $$
DECLARE
    duplicate_groups BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicate_groups
    FROM (
        SELECT lower(btrim(email))
        FROM users
        GROUP BY 1
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION
            'V41: % normalized email group(s) contain duplicates. Resolve them before applying uq_users_email_normalized',
            duplicate_groups;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_normalized
    ON users (lower(btrim(email)));

-- ============================================================
-- 3. RESTORE INDEXES DROPPED BY V33
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_city ON users (city);
CREATE INDEX IF NOT EXISTS idx_posts_author_name ON posts (author_name);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON comments (created_at);
CREATE INDEX IF NOT EXISTS idx_messages_created ON messages (created_at);

-- V36 replaced refresh_tokens with auth_refresh_tokens. The old
-- refresh_tokens(expires_at) index is obsolete; the replacement table already
-- carries the equivalent index. Re-assert it for restored/partial schemas.
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_expires_at
    ON auth_refresh_tokens (expires_at);

-- ============================================================
-- 4. SOFT-DELETE INDEXES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_posts_deleted ON posts (deleted);
CREATE INDEX IF NOT EXISTS idx_helps_deleted ON helps (deleted);
CREATE INDEX IF NOT EXISTS idx_messages_deleted_at
    ON messages (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- users.deleted and reviews.deleted already have indexes (V33/V30).
-- chat_user_state.deleted_at is covered by idx_chat_user_state_user.

-- ============================================================
-- 5. ACHIEVEMENT UNIQUENESS (RESTORE THE V19 INDEX NAME)
-- ============================================================
-- V19 created the unique index as idx_achievements_user_type, V33 dropped it,
-- and V38 re-created it as uq_achievements_user_type. Rename it back without
-- rebuilding the index when possible.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class
        WHERE relname = 'uq_achievements_user_type' AND relkind = 'i'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_class
        WHERE relname = 'idx_achievements_user_type' AND relkind = 'i'
    ) THEN
        ALTER INDEX uq_achievements_user_type RENAME TO idx_achievements_user_type;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_groups BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicate_groups
    FROM (
        SELECT user_id, type
        FROM achievements
        GROUP BY user_id, type
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION
            'V41: % duplicate achievement (user_id, type) group(s) found. Resolve them before applying idx_achievements_user_type',
            duplicate_groups;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_achievements_user_type
    ON achievements (user_id, type);

DROP INDEX IF EXISTS uq_achievements_user_type;

-- ============================================================
-- 6. CHAT PAIR INTEGRITY
-- ============================================================
DO $$
DECLARE
    duplicate_pairs BIGINT;
    self_pairs BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicate_pairs
    FROM (
        SELECT LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id)
        FROM chats
        GROUP BY 1, 2
        HAVING COUNT(*) > 1
    ) duplicates;

    SELECT COUNT(*) INTO self_pairs FROM chats WHERE user1_id = user2_id;

    IF duplicate_pairs > 0 THEN
        RAISE EXCEPTION
            'V41: % duplicate unordered chat pair(s) found. Resolve them before applying chat constraints',
            duplicate_pairs;
    END IF;
    IF self_pairs > 0 THEN
        RAISE EXCEPTION
            'V41: % chat(s) connect a user to themselves; fix them before applying chat constraints',
            self_pairs;
    END IF;
END $$;

-- V37 canonicalized existing rows and created both unique indexes. Keep them
-- idempotent and add the explicit canonical-order guard used by ChatService.
CREATE UNIQUE INDEX IF NOT EXISTS uq_chats_normalized_pair
    ON chats (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_chat_users'
    ) THEN
        ALTER TABLE chats
            ADD CONSTRAINT uq_chat_users UNIQUE (user1_id, user2_id);
    END IF;
END $$;

ALTER TABLE chats DROP CONSTRAINT IF EXISTS chk_chats_distinct_users;
ALTER TABLE chats
    ADD CONSTRAINT chk_chats_distinct_users CHECK (user1_id <> user2_id);

ALTER TABLE chats DROP CONSTRAINT IF EXISTS chk_chats_canonical_order;
ALTER TABLE chats
    ADD CONSTRAINT chk_chats_canonical_order CHECK (user1_id < user2_id);

-- ============================================================
-- 7. ACTIVE HELP UNIQUENESS
-- ============================================================
DO $$
DECLARE
    duplicate_posts BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicate_posts
    FROM (
        SELECT post_id
        FROM helps
        WHERE status IN ('ACCEPTED', 'COMPLETED', 'CONFIRMED')
          AND deleted = FALSE
        GROUP BY post_id
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_posts > 0 THEN
        RAISE EXCEPTION
            'V41: % post(s) have more than one active help. Cancel the duplicates before applying uq_helps_active_post',
            duplicate_posts;
    END IF;
END $$;

DROP INDEX IF EXISTS uq_helps_active_post;
CREATE UNIQUE INDEX uq_helps_active_post
    ON helps (post_id)
    WHERE status IN ('ACCEPTED', 'COMPLETED', 'CONFIRMED')
      AND deleted = FALSE;

-- ============================================================
-- 8. POST OWNERSHIP GUARD (V3 REMEDIATION)
-- ============================================================
DO $$
DECLARE
    null_owners BIGINT;
    orphan_owners BIGINT;
BEGIN
    SELECT COUNT(*) INTO null_owners FROM posts WHERE user_id IS NULL;

    IF null_owners > 0 THEN
        RAISE EXCEPTION
            'V41: % post(s) have user_id = NULL. V3 must not bind them to fixed ids; assign an explicit owner or preserve them in an archive before migrating',
            null_owners;
    END IF;

    SELECT COUNT(*) INTO orphan_owners
    FROM posts p
    WHERE p.user_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id);

    IF orphan_owners > 0 THEN
        RAISE EXCEPTION
            'V41: % post(s) reference a missing user_id. Repair the foreign key data before migrating',
            orphan_owners;
    END IF;
END $$;

ALTER TABLE posts ALTER COLUMN user_id SET NOT NULL;
