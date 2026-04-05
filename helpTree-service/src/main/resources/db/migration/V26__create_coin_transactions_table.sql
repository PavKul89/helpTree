CREATE TABLE IF NOT EXISTS coin_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL,
    description VARCHAR(500),
    related_user_id BIGINT,
    related_post_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_coin_user_id ON coin_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_coin_created_at ON coin_transactions(created_at);
