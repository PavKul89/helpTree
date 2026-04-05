CREATE TABLE IF NOT EXISTS user_rating_stats (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    total_helps_given BIGINT DEFAULT 0,
    total_helps_received BIGINT DEFAULT 0,
    successful_helps BIGINT DEFAULT 0,
    cancelled_helps BIGINT DEFAULT 0,
    average_response_time DOUBLE PRECISION,
    last_calculated TIMESTAMP,
    current_rating DOUBLE PRECISION DEFAULT 0.0,
    rating_trend VARCHAR(20) DEFAULT 'STABLE',
    updated_at TIMESTAMP
);

CREATE INDEX idx_user_rating_stats_user_id ON user_rating_stats(user_id);
CREATE INDEX idx_user_rating_stats_rating ON user_rating_stats(current_rating DESC);

CREATE TABLE IF NOT EXISTS rating_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    old_rating DOUBLE PRECISION,
    new_rating DOUBLE PRECISION NOT NULL,
    calculated_at TIMESTAMP NOT NULL,
    reason VARCHAR(500),
    help_id BIGINT,
    rating_components VARCHAR(1000)
);

CREATE INDEX idx_rating_history_user_id ON rating_history(user_id);
CREATE INDEX idx_rating_history_calculated_at ON rating_history(calculated_at DESC);
