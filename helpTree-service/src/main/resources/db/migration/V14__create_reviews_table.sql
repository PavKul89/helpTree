-- V14: Create reviews table
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    help_id BIGINT NOT NULL REFERENCES helps(id),
    from_user_id BIGINT NOT NULL REFERENCES users(id),
    to_user_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(help_id, from_user_id)
);

CREATE INDEX idx_reviews_to_user ON reviews(to_user_id);
CREATE INDEX idx_reviews_help ON reviews(help_id);
