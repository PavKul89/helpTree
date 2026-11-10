-- V60: Block 21.5 — transaction export, paid feature history and expiry reminders.
--
-- paid_feature_periods records every time-limited paid feature (VIP, post
-- boosts, nickname colour) as an append-only period. It is the source of the
-- "history" endpoint and of the 7/3/1-day VIP expiry reminders. There is no
-- automatic renewal: a period only ends when its end timestamp passes.
--
-- Additive and idempotent.

CREATE TABLE IF NOT EXISTS paid_feature_periods (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feature VARCHAR(30) NOT NULL,
    reference_id BIGINT NULL,
    started_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_paid_feature_periods_feature CHECK (
        feature IN ('VIP', 'BOOST', 'NICKNAME_COLOR')
    ),
    CONSTRAINT chk_paid_feature_periods_period CHECK (
        ends_at IS NULL OR ends_at > started_at
    )
);

CREATE INDEX IF NOT EXISTS idx_paid_feature_periods_user_started
    ON paid_feature_periods (user_id, started_at DESC);

-- Expiry reminder sweep: only periods that end in the future and have not
-- ended yet are candidates.
CREATE INDEX IF NOT EXISTS idx_paid_feature_periods_active_end
    ON paid_feature_periods (ends_at)
    WHERE ends_at IS NOT NULL;

-- Transaction export filters by user and date range.
CREATE INDEX IF NOT EXISTS idx_coin_user_created
    ON coin_transactions (user_id, created_at DESC);

-- VIP expiry reminder sweep.
CREATE INDEX IF NOT EXISTS idx_users_vip_until
    ON users (vip_until)
    WHERE vip_until IS NOT NULL;
