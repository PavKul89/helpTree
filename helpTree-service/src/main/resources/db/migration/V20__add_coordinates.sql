ALTER TABLE users ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE posts ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE posts ADD COLUMN longitude DOUBLE PRECISION;

CREATE INDEX idx_users_coordinates ON users(latitude, longitude);
CREATE INDEX idx_posts_coordinates ON posts(latitude, longitude);
