-- V58: Block 21.3 — message editing, attachments and retention.
--
-- * A sender may edit their own message for 15 minutes. edited_at/edit_count
--   are persisted so clients can show an "edited" marker.
-- * A message may carry up to three image attachments. The URLs are stored as
--   a JSON array in a TEXT column so the mapping does not depend on a JSONB
--   driver type and the future partitioned table stays simple.
-- * A retention sweep physically deletes messages older than the configured
--   number of days. The index on created_at keeps that sweep cheap.
--
-- Additive and idempotent.

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP NULL;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS attachment_urls TEXT NOT NULL DEFAULT '[]';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_messages_edit_count'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT chk_messages_edit_count CHECK (edit_count >= 0);
    END IF;
END $$;

-- Retention sweep: oldest messages first, bounded batches.
CREATE INDEX IF NOT EXISTS idx_messages_retention
    ON messages (created_at, id);

-- Chat attachments are first-class image objects. V48 restricted the purpose
-- column to AVATAR/POST/GENERAL; extend it with CHAT.
ALTER TABLE image_objects DROP CONSTRAINT IF EXISTS chk_image_objects_purpose;
ALTER TABLE image_objects
    ADD CONSTRAINT chk_image_objects_purpose
    CHECK (purpose IN ('AVATAR', 'POST', 'GENERAL', 'CHAT'));
