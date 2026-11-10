-- V59: Block 21.4 — post view/response counters, duplicate guard, link checks.
--
-- * views_count is incremented once per viewer per calendar day. Anonymous
--   viewers are keyed by a server-side hash of IP + User-Agent, signed-in users
--   by their user id. The uniqueness rule lives in post_views.
-- * responses_count mirrors the number of live help responses for the post.
--   The application recomputes it from helps after every state transition.
-- * content_fingerprint is a SHA-256 of the normalised title + description and
--   backs the 24-hour duplicate-post guard.
--
-- Additive and idempotent.

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS views_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS responses_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS content_fingerprint VARCHAR(64) NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_posts_views_count'
    ) THEN
        ALTER TABLE posts
            ADD CONSTRAINT chk_posts_views_count CHECK (views_count >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_posts_responses_count'
    ) THEN
        ALTER TABLE posts
            ADD CONSTRAINT chk_posts_responses_count CHECK (responses_count >= 0);
    END IF;
END $$;

-- Backfill the response counter from the same definition the service uses.
UPDATE posts p
SET responses_count = COALESCE((
    SELECT COUNT(*)
    FROM helps h
    WHERE h.post_id = p.id
      AND h.status IN ('ACCEPTED', 'COMPLETED', 'CONFIRMED')
      AND h.deleted = FALSE
), 0);

-- Backfill the duplicate fingerprint for existing posts. The service uses the
-- same normalisation: trim, collapse whitespace, lower-case, joined by \n.
UPDATE posts
SET content_fingerprint = encode(sha256(convert_to(
    lower(regexp_replace(btrim(COALESCE(title, '')), '\s+', ' ', 'g'))
    || E'\n' ||
    lower(regexp_replace(btrim(COALESCE(description, '')), '\s+', ' ', 'g')),
    'UTF8'
)), 'hex')
WHERE content_fingerprint IS NULL;

CREATE INDEX IF NOT EXISTS idx_posts_author_fingerprint
    ON posts (user_id, content_fingerprint, created_at DESC);

-- Daily unique views. viewer_key is either 'u:{userId}' or 'h:{sha256}' so the
-- same table serves authenticated and anonymous traffic.
CREATE TABLE IF NOT EXISTS post_views (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    viewer_key VARCHAR(80) NOT NULL,
    view_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_post_views_viewer_key_not_blank CHECK (length(btrim(viewer_key)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_post_views_daily
    ON post_views (post_id, viewer_key, view_date);

-- "Views for a post on a date" and retention/analytics scans.
CREATE INDEX IF NOT EXISTS idx_post_views_post_date
    ON post_views (post_id, view_date DESC);
