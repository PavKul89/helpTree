-- V57: Block 21.2 — SLA reminders for accepted helps.
--
-- The stale-help sweep already cancels an ACCEPTED help once the configured
-- timeout expires. This migration adds the storage for the two reminder
-- thresholds (50% and 80% of the same timeout) so a sweep can notify the
-- participants exactly once per threshold.
--
-- Additive and idempotent.

ALTER TABLE helps
    ADD COLUMN IF NOT EXISTS reminder_50_sent_at TIMESTAMP NULL;

ALTER TABLE helps
    ADD COLUMN IF NOT EXISTS reminder_80_sent_at TIMESTAMP NULL;

-- The reminder sweep only looks at live ACCEPTED helps ordered by accepted_at.
-- idx_helps_status_accepted_at (V38) already covers that scan; this partial
-- index additionally keeps rows that already have both reminders out.
CREATE INDEX IF NOT EXISTS idx_helps_accepted_reminders
    ON helps (accepted_at)
    WHERE status = 'ACCEPTED'
      AND deleted = FALSE
      AND (reminder_50_sent_at IS NULL OR reminder_80_sent_at IS NULL);
