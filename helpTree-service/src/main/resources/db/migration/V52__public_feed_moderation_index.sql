-- V52: give the moderated public feed its own index.
--
-- V51 tried to add `idx_posts_public_feed` for the public, moderation-aware feed,
-- but V46 had already registered that name. `CREATE INDEX IF NOT EXISTS` then
-- silently kept the older definition, which does not include
-- `moderation_status`. PostgreSQL reports success while the intended index does
-- not exist.
--
-- Applied migrations must stay immutable, so V52 adds the missing index under a
-- distinct name instead of rewriting V51. The V46 index is kept: it still backs
-- queries that do not filter by moderation status (for example the admin feed).
CREATE INDEX IF NOT EXISTS idx_posts_public_moderated_feed
    ON posts (created_at DESC, id DESC)
    WHERE moderation_status = 'ACTIVE'
      AND COALESCE(deleted, FALSE) = FALSE
      AND status <> 'CANCELLED';
