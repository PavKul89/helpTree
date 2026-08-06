-- V33: Fix schema gaps — add missing columns, fix indexes to match entity annotations
-- Audit against current JPA entities (2026-08-06)

-- ============================================================
-- 1. ADD MISSING COLUMNS
-- ============================================================

-- users.birth_date — User.java line 64
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date TIMESTAMP NULL;

-- users.blocked_at — User.java line 97
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMP NULL;

-- users.avatar_url — User.java line 107
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT NULL;

-- posts.category — Post.java line 30
ALTER TABLE posts ADD COLUMN IF NOT EXISTS category VARCHAR(100) NULL;

-- ============================================================
-- 2. ADD MISSING INDEXES
-- ============================================================

-- idx_users_deleted — User.java line 20
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users(deleted);

-- idx_posts_status — Post.java line 15
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts(status);

-- idx_messages_is_read — Message.java line 16
CREATE INDEX IF NOT EXISTS idx_messages_is_read ON messages(is_read);

-- ============================================================
-- 3. RENAME MISMATCHED INDEXES (drop old, create correct name)
-- ============================================================

-- chats indexes: idx_chats_user1 → idx_chats_user1_id
DROP INDEX IF EXISTS idx_chats_user1;
CREATE INDEX IF NOT EXISTS idx_chats_user1_id ON chats(user1_id);

-- chats indexes: idx_chats_user2 → idx_chats_user2_id
DROP INDEX IF EXISTS idx_chats_user2;
CREATE INDEX IF NOT EXISTS idx_chats_user2_id ON chats(user2_id);

-- chats indexes: idx_chats_last_message → idx_chats_last_message_at
DROP INDEX IF EXISTS idx_chats_last_message;
CREATE INDEX IF NOT EXISTS idx_chats_last_message_at ON chats(last_message_at DESC);

-- messages indexes: idx_messages_chat → idx_messages_chat_id
DROP INDEX IF EXISTS idx_messages_chat;
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);

-- messages indexes: idx_messages_sender → idx_messages_sender_id
DROP INDEX IF EXISTS idx_messages_sender;
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);

-- reviews indexes: idx_reviews_help → idx_reviews_help_id
DROP INDEX IF EXISTS idx_reviews_help;
CREATE INDEX IF NOT EXISTS idx_reviews_help_id ON reviews(help_id);

-- reviews indexes: idx_reviews_to_user → idx_reviews_to_user_id
DROP INDEX IF EXISTS idx_reviews_to_user;
CREATE INDEX IF NOT EXISTS idx_reviews_to_user_id ON reviews(to_user_id);

-- rating_history indexes: idx_rating_history_user_id → idx_user_id
DROP INDEX IF EXISTS idx_rating_history_user_id;
CREATE INDEX IF NOT EXISTS idx_user_id ON rating_history(user_id);

-- rating_history indexes: idx_rating_history_calculated_at → idx_calculated_at
DROP INDEX IF EXISTS idx_rating_history_calculated_at;
CREATE INDEX IF NOT EXISTS idx_calculated_at ON rating_history(calculated_at DESC);

-- ============================================================
-- 4. DROP EXTRA INDEXES (not declared in entity @Index annotations)
-- ============================================================

DROP INDEX IF EXISTS idx_users_status;
DROP INDEX IF EXISTS idx_users_city;
DROP INDEX IF EXISTS idx_posts_author_name;
DROP INDEX IF EXISTS idx_refresh_token_expires_at;
DROP INDEX IF EXISTS idx_achievements_user_type;
DROP INDEX IF EXISTS idx_comments_created_at;
DROP INDEX IF EXISTS idx_messages_created;
