-- Создание таблицы posts
CREATE TABLE IF NOT EXISTS posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    author_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
    );

-- Индекс для быстрого поиска по дате создания
CREATE INDEX idx_posts_created_at ON posts(created_at);

-- Индекс для поиска по автору
CREATE INDEX idx_posts_author_name ON posts(author_name);

-- Комментарий к таблице
COMMENT ON TABLE posts IS 'Посты с просьбами о помощи';
COMMENT ON COLUMN posts.id IS 'Уникальный идентификатор';
COMMENT ON COLUMN posts.title IS 'Заголовок просьбы';
COMMENT ON COLUMN posts.description IS 'Подробное описание';
COMMENT ON COLUMN posts.author_name IS 'Имя автора';
COMMENT ON COLUMN posts.created_at IS 'Дата создания';
COMMENT ON COLUMN posts.updated_at IS 'Дата последнего обновления';