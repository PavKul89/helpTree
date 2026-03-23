-- V16: Add parent_comment_id column for nested comments
ALTER TABLE comments ADD COLUMN parent_comment_id BIGINT;

ALTER TABLE comments ADD CONSTRAINT fk_comment_parent 
    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE;

CREATE INDEX idx_comments_parent_comment_id ON comments(parent_comment_id);
