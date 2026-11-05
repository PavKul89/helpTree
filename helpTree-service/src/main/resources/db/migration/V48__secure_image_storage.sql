-- V48: Block 11 — ownership and lifecycle for images stored in Silo.
--
-- Before this migration the bucket contained objects that no database row
-- referenced. That made it impossible to enforce per-user quotas, to decide who
-- may delete an object, or to distinguish an in-flight upload from an orphan.
--
-- The table is deliberately additive: existing bucket objects stay untouched.
-- Rows for avatars and post images that already point at the default bucket are
-- backfilled so the runtime orphan sweep cannot delete images that are still in
-- use. A custom SILO_BUCKET needs the equivalent backfill with that bucket name
-- (see docs/MIGRATIONS.md).
--
-- Sha256, dimensions and the real byte size are only known for objects uploaded
-- after this migration. Legacy rows keep NULL dimensions/sha256 and size_bytes
-- 0; they still count as one object towards the quota and are protected from
-- the orphan sweep while a user/post references their URL.

CREATE TABLE IF NOT EXISTS image_objects (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    width INTEGER,
    height INTEGER,
    sha256 VARCHAR(64),
    purpose VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_image_objects_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_image_objects_object_key UNIQUE (object_key),
    CONSTRAINT chk_image_objects_size CHECK (size_bytes >= 0),
    CONSTRAINT chk_image_objects_dimensions CHECK (
        (width IS NULL OR width > 0) AND (height IS NULL OR height > 0)
    ),
    CONSTRAINT chk_image_objects_purpose CHECK (purpose IN ('AVATAR', 'POST', 'GENERAL')),
    CONSTRAINT chk_image_objects_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_image_objects_deleted_state CHECK (
        (status = 'ACTIVE' AND deleted_at IS NULL)
        OR (status = 'DELETED' AND deleted_at IS NOT NULL)
    )
);

-- Quota checks lock the owner row and sum the active objects. A partial index
-- keeps deleted rows out of that scan.
CREATE INDEX IF NOT EXISTS idx_image_objects_owner_active
    ON image_objects (owner_id)
    WHERE status = 'ACTIVE';

-- Deletion cleanup walks DELETED rows oldest-first.
CREATE INDEX IF NOT EXISTS idx_image_objects_deleted_cleanup
    ON image_objects (status, deleted_at)
    WHERE status = 'DELETED';

-- Orphan candidates are active rows that were created before the TTL. The
-- reference checks happen against users.avatar_url and post_images.image_url.
CREATE INDEX IF NOT EXISTS idx_image_objects_orphan_candidates
    ON image_objects (created_at, id)
    WHERE status = 'ACTIVE';

-- Lookup by public URL is used when the client asks to delete an image.
CREATE INDEX IF NOT EXISTS idx_image_objects_url
    ON image_objects (url)
    WHERE status = 'ACTIVE';

-- === Backfill: avatars =====================================================
INSERT INTO image_objects (
    owner_id, object_key, url, content_type, size_bytes, purpose, status, created_at
)
SELECT
    u.id,
    substring(u.avatar_url FROM '/helptree-images/([^/?#]+)$'),
    u.avatar_url,
    CASE
        WHEN lower(u.avatar_url) ~ '\.png([?#].*)?$' THEN 'image/png'
        WHEN lower(u.avatar_url) ~ '\.gif([?#].*)?$' THEN 'image/gif'
        ELSE 'image/jpeg'
    END,
    0,
    'AVATAR',
    'ACTIVE',
    COALESCE(u.created_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.avatar_url ~ '/helptree-images/[^/?#]+$'
  AND u.avatar_url !~ '/\.\.?(/|$)'
ON CONFLICT (object_key) DO NOTHING;

-- === Backfill: post images =================================================
INSERT INTO image_objects (
    owner_id, object_key, url, content_type, size_bytes, purpose, status, created_at
)
SELECT
    p.user_id,
    substring(pi.image_url FROM '/helptree-images/([^/?#]+)$'),
    pi.image_url,
    CASE
        WHEN lower(pi.image_url) ~ '\.png([?#].*)?$' THEN 'image/png'
        WHEN lower(pi.image_url) ~ '\.gif([?#].*)?$' THEN 'image/gif'
        ELSE 'image/jpeg'
    END,
    0,
    'POST',
    'ACTIVE',
    COALESCE(p.created_at, CURRENT_TIMESTAMP)
FROM post_images pi
JOIN posts p ON p.id = pi.post_id
WHERE p.user_id IS NOT NULL
  AND pi.image_url ~ '/helptree-images/[^/?#]+$'
  AND pi.image_url !~ '/\.\.?(/|$)'
ON CONFLICT (object_key) DO NOTHING;
