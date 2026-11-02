-- V36: secure authentication sessions, hashed refresh tokens and account security tokens.
--
-- Refresh tokens are never stored in plaintext anymore. Existing plaintext tokens are
-- discarded on purpose: they cannot be validated by the new code and every client must
-- obtain a fresh session after deployment.

-- ============================================================
-- 1. USER SECURITY FIELDS
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;

-- Accounts that existed before this migration are grandfathered as verified so that
-- introducing the verification flow does not lock out the current user base.
UPDATE users SET email_verified = true WHERE email_verified = false;

-- ============================================================
-- 2. AUTH SESSIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(50),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_active_user
    ON auth_sessions(user_id) WHERE revoked_at IS NULL;

-- ============================================================
-- 3. HASHED REFRESH TOKENS (ONE ACTIVE TOKEN PER ROTATION)
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_session_id ON auth_refresh_tokens(session_id);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_user_id ON auth_refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_expires_at ON auth_refresh_tokens(expires_at);

-- The old table stored raw UUID tokens and cannot be migrated safely.
DROP TABLE IF EXISTS refresh_tokens CASCADE;

-- ============================================================
-- 4. SINGLE-USE ACCOUNT TOKENS (EMAIL VERIFY / RESET / EMAIL CHANGE)
-- ============================================================
CREATE TABLE IF NOT EXISTS account_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    payload VARCHAR(320),
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_tokens_user_type ON account_tokens(user_id, type);
CREATE INDEX IF NOT EXISTS idx_account_tokens_expires_at ON account_tokens(expires_at);
