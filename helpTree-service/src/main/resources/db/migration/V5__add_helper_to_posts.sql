-- Добавление колонки helper_id для связи с пользователем-помощником (безопасная миграция)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'posts' AND column_name = 'helper_id') THEN
        ALTER TABLE posts ADD COLUMN helper_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_posts_helper_id ON posts(helper_id);