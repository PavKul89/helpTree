-- V39: Wallet and economy integrity.
--
-- Goals:
--   * help_coins is never NULL and can never become negative in the database;
--   * every money movement has a stable business/idempotency reference;
--   * administrative coin corrections have an append-only audit trail;
--   * historical ledger/balance drift is detected, but never silently "repaired"
--     by inventing coins.
--
-- Existing daily-login duplicates (created before V39 by the old rolling-24h
-- check) are intentionally left untouched. WalletReconciliationService reports
-- them so an administrator can decide how to correct the data.

-- ============================================================
-- 1. NON-NEGATIVE BALANCE
-- ============================================================
UPDATE users SET help_coins = 0 WHERE help_coins IS NULL;

ALTER TABLE users ALTER COLUMN help_coins SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN help_coins SET NOT NULL;

DO $$
DECLARE
    bad_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO bad_count FROM users WHERE help_coins < 0;
    IF bad_count > 0 THEN
        RAISE EXCEPTION
            'V39: % users have negative help_coins; resolve the data manually before applying the constraint',
            bad_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_help_coins_non_negative'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_help_coins_non_negative CHECK (help_coins >= 0);
    END IF;
END $$;

-- ============================================================
-- 2. LEDGER REFERENCES AND IDEMPOTENCY
-- ============================================================
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS reference_id VARCHAR(120) NULL;

-- Backfill deterministic references for help awards. The existing partial
-- unique indexes already guarantee uniqueness; the new reference makes the
-- idempotency key explicit for every future retry.
UPDATE coin_transactions
SET reference_id = 'help-given:post:' || related_post_id
WHERE type = 'HELP_GIVEN'
  AND related_post_id IS NOT NULL
  AND reference_id IS NULL;

UPDATE coin_transactions
SET reference_id = 'help-received:post:' || related_post_id
WHERE type = 'HELP_RECEIVED'
  AND related_post_id IS NOT NULL
  AND reference_id IS NULL;

UPDATE coin_transactions
SET reference_id = 'first-help:post:' || related_post_id
WHERE type = 'FIRST_HELP'
  AND related_post_id IS NOT NULL
  AND reference_id IS NULL;

-- One ledger row per (user, operation type, business/idempotency reference).
CREATE UNIQUE INDEX IF NOT EXISTS uq_coin_user_type_reference
    ON coin_transactions (user_id, type, reference_id)
    WHERE reference_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_coin_amount_non_zero'
    ) THEN
        ALTER TABLE coin_transactions
            ADD CONSTRAINT chk_coin_amount_non_zero CHECK (amount <> 0);
    END IF;
END $$;

UPDATE coin_transactions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE coin_transactions ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE coin_transactions ALTER COLUMN created_at SET NOT NULL;

-- ============================================================
-- 3. ADMINISTRATIVE AUDIT
-- ============================================================
CREATE TABLE IF NOT EXISTS admin_coin_audits (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NULL,
    user_id BIGINT NULL,
    amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    transaction_id BIGINT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_admin_coin_audit_amount_non_zero CHECK (amount <> 0),
    CONSTRAINT chk_admin_coin_audit_reason_not_blank CHECK (length(btrim(reason)) > 0)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_admin_coin_audit_admin'
    ) THEN
        ALTER TABLE admin_coin_audits
            ADD CONSTRAINT fk_admin_coin_audit_admin
            FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_admin_coin_audit_user'
    ) THEN
        ALTER TABLE admin_coin_audits
            ADD CONSTRAINT fk_admin_coin_audit_user
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_admin_coin_audit_transaction'
    ) THEN
        ALTER TABLE admin_coin_audits
            ADD CONSTRAINT fk_admin_coin_audit_transaction
            FOREIGN KEY (transaction_id) REFERENCES coin_transactions(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_admin_coin_audits_user
    ON admin_coin_audits (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_coin_audits_admin
    ON admin_coin_audits (admin_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_coin_audits_created
    ON admin_coin_audits (created_at DESC);
