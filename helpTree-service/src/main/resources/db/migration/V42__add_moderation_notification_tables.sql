-- V42: domain tables for moderation, notifications, audit and reliable events.
--
-- These tables are intentionally additive. They prepare the next domain blocks
-- without changing existing rows. All timestamps use the same TIMESTAMP type as
-- the rest of the schema so JPA LocalDateTime mapping stays consistent.
--
-- chat_user_state already exists from V37; this migration only re-asserts its
-- missing message foreign key on partially restored schemas.

-- ============================================================
-- 1. REPORTS
-- ============================================================
CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    details TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    assigned_to_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    resolution VARCHAR(1000) NULL,
    resolved_by_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reports_target_type CHECK (
        target_type IN ('POST', 'USER', 'COMMENT', 'MESSAGE', 'HELP', 'CHAT')
    ),
    CONSTRAINT chk_reports_status CHECK (
        status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED', 'DUPLICATE')
    ),
    CONSTRAINT chk_reports_priority CHECK (
        priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    ),
    CONSTRAINT chk_reports_reason_not_blank CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_reports_resolution CHECK (
        (status IN ('RESOLVED', 'REJECTED', 'DUPLICATE') AND resolved_at IS NOT NULL)
        OR (status IN ('OPEN', 'IN_REVIEW') AND resolved_at IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_reports_status_created
    ON reports (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reports_target
    ON reports (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_reports_reporter_created
    ON reports (reporter_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reports_assigned
    ON reports (assigned_to_id)
    WHERE assigned_to_id IS NOT NULL;

-- A reporter cannot keep two open reports for the same object. Closed reports
-- remain in history, so a user may report the same object again later.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reports_open_reporter_target
    ON reports (reporter_id, target_type, target_id)
    WHERE reporter_id IS NOT NULL
      AND status IN ('OPEN', 'IN_REVIEW');

-- ============================================================
-- 2. REPORT EVENTS (APPEND-ONLY)
-- ============================================================
CREATE TABLE IF NOT EXISTS report_events (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    actor_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    note TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_report_events_type CHECK (
        event_type IN ('CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'NOTE',
                       'ESCALATED', 'RESOLVED', 'REOPENED')
    ),
    CONSTRAINT chk_report_events_status CHECK (
        (from_status IS NULL OR from_status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED', 'DUPLICATE'))
        AND (to_status IS NULL OR to_status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED', 'DUPLICATE'))
    )
);

CREATE INDEX IF NOT EXISTS idx_report_events_report_created
    ON report_events (report_id, created_at);

CREATE INDEX IF NOT EXISTS idx_report_events_actor_created
    ON report_events (actor_id, created_at DESC)
    WHERE actor_id IS NOT NULL;

-- ============================================================
-- 3. MODERATION ACTIONS (APPEND-ONLY)
-- ============================================================
CREATE TABLE IF NOT EXISTS moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NULL REFERENCES reports(id) ON DELETE SET NULL,
    moderator_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    expires_at TIMESTAMP NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_moderation_actions_target_type CHECK (
        target_type IN ('POST', 'USER', 'COMMENT', 'MESSAGE', 'HELP', 'CHAT', 'REPORT')
    ),
    CONSTRAINT chk_moderation_actions_type CHECK (
        action_type IN ('WARN', 'MUTE', 'UNMUTE', 'BAN', 'UNBAN', 'DELETE_CONTENT',
                        'RESTORE_CONTENT', 'DISMISS_REPORT', 'ESCALATE', 'OTHER')
    ),
    CONSTRAINT chk_moderation_actions_reason_not_blank CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_target
    ON moderation_actions (target_type, target_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_moderator
    ON moderation_actions (moderator_id, created_at DESC)
    WHERE moderator_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_moderation_actions_report
    ON moderation_actions (report_id)
    WHERE report_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_moderation_actions_created
    ON moderation_actions (created_at DESC);

-- ============================================================
-- 4. USER BLOCKS
-- ============================================================
CREATE TABLE IF NOT EXISTS user_blocks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_by_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    reason VARCHAR(1000) NOT NULL,
    starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at TIMESTAMP NULL,
    lifted_at TIMESTAMP NULL,
    lifted_by_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    lift_reason VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_blocks_source CHECK (
        source IN ('ADMIN', 'SYSTEM', 'MODERATION', 'AUTO_DEBT')
    ),
    CONSTRAINT chk_user_blocks_reason_not_blank CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_user_blocks_period CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_user_blocks_lifted CHECK (lifted_at IS NULL OR lifted_at >= starts_at)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_user_started
    ON user_blocks (user_id, starts_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_blocks_active
    ON user_blocks (user_id, ends_at)
    WHERE lifted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked_by
    ON user_blocks (blocked_by_id, created_at DESC)
    WHERE blocked_by_id IS NOT NULL;

-- ============================================================
-- 5. NOTIFICATIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key VARCHAR(200) NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_type_not_blank CHECK (length(btrim(type)) > 0),
    CONSTRAINT chk_notifications_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT chk_notifications_body_not_blank CHECK (length(btrim(body)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_dedupe
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- ============================================================
-- 6. NOTIFICATION PREFERENCES
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    digest_frequency VARCHAR(20) NOT NULL DEFAULT 'INSTANT',
    quiet_hours_start TIME NULL,
    quiet_hours_end TIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notification_preferences_digest CHECK (
        digest_frequency IN ('INSTANT', 'DAILY', 'WEEKLY', 'OFF')
    ),
    CONSTRAINT chk_notification_preferences_quiet_hours CHECK (
        (quiet_hours_start IS NULL AND quiet_hours_end IS NULL)
        OR (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL)
    )
);

-- ============================================================
-- 7. ADMIN AUDIT LOG (APPEND-ONLY)
-- ============================================================
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NULL,
    before_state JSONB NULL,
    after_state JSONB NULL,
    reason VARCHAR(1000) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    request_id VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_admin_audit_action_not_blank CHECK (length(btrim(action)) > 0),
    CONSTRAINT chk_admin_audit_entity_not_blank CHECK (length(btrim(entity_type)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_created
    ON admin_audit_log (admin_id, created_at DESC)
    WHERE admin_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_entity
    ON admin_audit_log (entity_type, entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created
    ON admin_audit_log (created_at DESC);

-- ============================================================
-- 8. OUTBOX EVENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    dedupe_key VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP NULL,
    locked_by VARCHAR(100) NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    CONSTRAINT chk_outbox_events_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD')
    ),
    CONSTRAINT chk_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_events_aggregate_type_not_blank CHECK (length(btrim(aggregate_type)) > 0),
    CONSTRAINT chk_outbox_events_event_type_not_blank CHECK (length(btrim(event_type)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_events_dedupe
    ON outbox_events (dedupe_key)
    WHERE dedupe_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_events_dispatch
    ON outbox_events (status, available_at);

CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_events_created
    ON outbox_events (created_at DESC);

-- ============================================================
-- 9. CHAT USER STATE GUARD
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_user_state_last_read'
    ) THEN
        ALTER TABLE chat_user_state
            ADD CONSTRAINT fk_chat_user_state_last_read
            FOREIGN KEY (last_read_message_id) REFERENCES messages(id) ON DELETE SET NULL;
    END IF;
END $$;
