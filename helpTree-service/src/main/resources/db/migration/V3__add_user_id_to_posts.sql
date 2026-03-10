-- Добавляем связь с пользователями
ALTER TABLE posts
    ADD COLUMN user_id BIGINT,
ADD COLUMN helper_id BIGINT;

-- Добавляем внешние ключи
ALTER TABLE posts
    ADD CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_helper
        FOREIGN KEY (helper_id) REFERENCES users(id) ON DELETE SET NULL;

-- Индексы для быстрого поиска
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_helper_id ON posts(helper_id);

-- Обновляем существующие посты (привязываем к случайным пользователям)
-- Это только для тестовых данных
UPDATE posts SET user_id = 1 WHERE id = 1;
UPDATE posts SET user_id = 2 WHERE id = 2;
UPDATE posts SET user_id = 3 WHERE id = 3;
UPDATE posts SET user_id = 4 WHERE id = 4;
UPDATE posts SET user_id = 5 WHERE id = 5;
UPDATE posts SET user_id = 1 WHERE id = 6;
UPDATE posts SET user_id = 2 WHERE id = 7;
UPDATE posts SET user_id = 3 WHERE id = 8;
UPDATE posts SET user_id = 4 WHERE id = 9;
UPDATE posts SET user_id = 5 WHERE id = 10;

-- Делаем поле user_id обязательным
ALTER TABLE posts ALTER COLUMN user_id SET NOT NULL;