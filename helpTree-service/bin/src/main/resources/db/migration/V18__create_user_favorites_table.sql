-- V18__create_user_favorites_table.sql
CREATE TABLE IF NOT EXISTS user_favorites (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, post_id),
    CONSTRAINT fk_user_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_favorites_user_id ON user_favorites(user_id);
