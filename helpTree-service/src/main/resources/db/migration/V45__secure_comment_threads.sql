-- V45: make comment threads durable, paginable and moderatable.
--
-- Forward-only migration. Existing rows stay valid:
--   * comments become tombstones instead of physical deletes, so replies keep
--     their parent and the discussion tree survives moderation;
--   * depth is backfilled for already existing replies (the application
--     enforces app.comments.max-depth for new replies);
--   * posts.comments_count is a projection of non-deleted comments.
--
-- The old fk_comment_parent ON DELETE CASCADE is intentionally left in place:
-- it only fires on a physical DELETE, which the application no longer issues.

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS deleted_by_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS deleted_by_author BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_reason VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS depth INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- Backfill the depth of replies that predate this migration. Depth 0 is the
-- safe default for a cycle or a missing parent; a cycle is not created by the
-- application, this is only a defensive bound for legacy data.
WITH RECURSIVE comment_depths AS (
    SELECT id, 0 AS depth
    FROM comments
    WHERE parent_comment_id IS NULL
    UNION ALL
    SELECT c.id, d.depth + 1
    FROM comments c
    JOIN comment_depths d ON c.parent_comment_id = d.id
    WHERE d.depth < 20
)
UPDATE comments c
SET depth = d.depth
FROM comment_depths d
WHERE c.id = d.id
  AND c.depth <> d.depth;

UPDATE comments
SET updated_at = created_at
WHERE updated_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_comments_deleted_by'
    ) THEN
        ALTER TABLE comments
            ADD CONSTRAINT fk_comments_deleted_by
            FOREIGN KEY (deleted_by_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_comments_depth'
    ) THEN
        ALTER TABLE comments
            ADD CONSTRAINT chk_comments_depth CHECK (depth >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_comments_deleted_state'
    ) THEN
        ALTER TABLE comments
            ADD CONSTRAINT chk_comments_deleted_state CHECK (
                (deleted = FALSE AND deleted_at IS NULL)
                OR (deleted = TRUE AND deleted_at IS NOT NULL)
            );
    END IF;
END $$;

-- Root threads are paginated; descendants are loaded per selected root page.
CREATE INDEX IF NOT EXISTS idx_comments_post_roots_created
    ON comments (post_id, created_at, id)
    WHERE parent_comment_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_comments_post_thread
    ON comments (post_id, parent_comment_id, created_at, id);

CREATE INDEX IF NOT EXISTS idx_comments_post_deleted_created
    ON comments (post_id, deleted, created_at, id);

-- Comment counter on the post. Backfill before adding the non-negative guard.
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS comments_count INTEGER NOT NULL DEFAULT 0;

UPDATE posts p
SET comments_count = (
    SELECT COUNT(*)
    FROM comments c
    WHERE c.post_id = p.id
      AND c.deleted = FALSE
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_posts_comments_count'
    ) THEN
        ALTER TABLE posts
            ADD CONSTRAINT chk_posts_comments_count CHECK (comments_count >= 0);
    END IF;
END $$;
