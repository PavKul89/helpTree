-- ============================================================
-- Block 18: administrative audit, deleted-object lists and SLA
-- ============================================================
-- The audit table itself (V42) already holds every column the audit UI needs.
-- This migration only adds the composite indexes required by the new
-- server-side filters, so no existing data is rewritten.
--
-- Every statement is idempotent: a re-run on an environment where a previous
-- attempt failed halfway must be safe.

-- Global audit search filters by action and orders by created_at DESC.
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action_created
    ON admin_audit_log (action, created_at DESC);

-- Global audit search filters by entity type without an entity id.
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_entity_type_created
    ON admin_audit_log (entity_type, created_at DESC);

-- "Deleted requests" list: only soft-deleted rows, newest deletion first.
CREATE INDEX IF NOT EXISTS idx_posts_deleted_at
    ON posts (deleted_at DESC)
    WHERE deleted = TRUE;

-- Review moderation lists soft-deleted reviews explicitly.
CREATE INDEX IF NOT EXISTS idx_reviews_deleted_at
    ON reviews (deleted_at DESC)
    WHERE deleted = TRUE;

-- SLA dashboard: open queue age and per-priority counts only touch open rows.
CREATE INDEX IF NOT EXISTS idx_reports_open_queue
    ON reports (created_at)
    WHERE status IN ('OPEN', 'IN_REVIEW');

-- Active session list per user, newest first.
CREATE INDEX IF NOT EXISTS idx_auth_sessions_active_user_created
    ON auth_sessions (user_id, created_at DESC)
    WHERE revoked_at IS NULL;
