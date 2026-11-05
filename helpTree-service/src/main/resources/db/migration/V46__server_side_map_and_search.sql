-- V46: Block 9 — server-side map/search support.
--
-- This migration is forward-only. It adds:
--   * pg_trgm and expression trigram GIN indexes for the case-insensitive
--     substring search used by PostgreSQLSpecification / SearchRepository;
--   * partial indexes for the public map and feed scans;
--   * the durable geocoding cache/queue consumed by GeocodingScheduler;
--   * a coordinate backfill from already geocoded users to their posts.
--
-- Nothing is deleted. Existing rows are only touched where the new invariants
-- say a value is missing (posts without coordinates whose author already has
-- them). Applied migrations are never edited.

-- === Trigram search ========================================================
-- pg_trgm is a trusted extension, so the application owner can install it.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_posts_title_trgm
    ON posts USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_posts_description_trgm
    ON posts USING gin (lower(description) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_posts_category_trgm
    ON posts USING gin (lower(category) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_name_trgm
    ON users USING gin (lower(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_city_trgm
    ON users USING gin (lower(city) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_reviews_comment_trgm
    ON reviews USING gin (lower(comment) gin_trgm_ops);

-- === Map / feed indexes ====================================================
-- Coordinates are mandatory on the map, so the index can omit useless rows.
CREATE INDEX IF NOT EXISTS idx_posts_map_active
    ON posts (latitude, longitude)
    WHERE COALESCE(deleted, FALSE) = FALSE
      AND status <> 'CANCELLED'
      AND latitude IS NOT NULL
      AND longitude IS NOT NULL;

-- Category + status narrows the map before the coordinate predicate.
CREATE INDEX IF NOT EXISTS idx_posts_map_category
    ON posts (category, status, latitude, longitude)
    WHERE COALESCE(deleted, FALSE) = FALSE
      AND status <> 'CANCELLED'
      AND latitude IS NOT NULL
      AND longitude IS NOT NULL;

-- Deterministic newest-first feed scans (the id tiebreaker backs keyset/deep
-- pagination requests).
CREATE INDEX IF NOT EXISTS idx_posts_public_feed
    ON posts (created_at DESC, id DESC)
    WHERE COALESCE(deleted, FALSE) = FALSE
      AND status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_users_public_rating
    ON users (rating DESC, id DESC)
    WHERE COALESCE(deleted, FALSE) = FALSE;

CREATE INDEX IF NOT EXISTS idx_helps_created
    ON helps (created_at DESC, id DESC)
    WHERE COALESCE(deleted, FALSE) = FALSE;

-- === Async geocoding queue =================================================
CREATE TABLE IF NOT EXISTS geocoding_cache (
    city_key    VARCHAR(255) PRIMARY KEY,
    query_city  VARCHAR(255) NOT NULL,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts    INTEGER NOT NULL DEFAULT 0,
    last_error  VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT geocoding_cache_status_chk
        CHECK (status IN ('PENDING', 'PROCESSING', 'FOUND', 'FAILED')),
    CONSTRAINT geocoding_cache_attempts_chk CHECK (attempts >= 0)
);

-- The worker claims the oldest PENDING/FAILED row (or a stale PROCESSING row).
CREATE INDEX IF NOT EXISTS idx_geocoding_cache_queue
    ON geocoding_cache (status, attempts, updated_at);

-- Seed the queue with cities that still have no coordinates. Keys mirror
-- GeocodingService.normalizeKey: lowercase, collapse spaces, drop a leading
-- "г."/"г" prefix.
INSERT INTO geocoding_cache (city_key, query_city, status, attempts, created_at, updated_at)
SELECT lower(regexp_replace(regexp_replace(btrim(u.city), '^г\.?\s*', ''), '\s+', ' ', 'g')),
       min(btrim(u.city)),
       'PENDING',
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM users u
WHERE COALESCE(u.deleted, FALSE) = FALSE
  AND u.city IS NOT NULL
  AND btrim(u.city) <> ''
  AND (u.latitude IS NULL OR u.longitude IS NULL)
GROUP BY lower(regexp_replace(regexp_replace(btrim(u.city), '^г\.?\s*', ''), '\s+', ' ', 'g'))
ON CONFLICT (city_key) DO NOTHING;

-- === Coordinate backfill ===================================================
-- Posts inherit the author's already resolved coordinates. Manually set post
-- coordinates are never overwritten.
UPDATE posts p
SET latitude = u.latitude,
    longitude = u.longitude,
    updated_at = CURRENT_TIMESTAMP
FROM users u
WHERE p.user_id = u.id
  AND COALESCE(p.deleted, FALSE) = FALSE
  AND p.latitude IS NULL
  AND p.longitude IS NULL
  AND u.latitude IS NOT NULL
  AND u.longitude IS NOT NULL;
