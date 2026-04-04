CREATE TABLE coin_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL,
    description VARCHAR(500),
    related_user_id BIGINT,
    related_post_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_coin_user_id (user_id),
    INDEX idx_coin_created_at (created_at)
);
