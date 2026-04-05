-- Добавляем поле status в таблицу posts
ALTER TABLE posts
    ADD COLUMN status VARCHAR(20);

-- Для существующих постов ставим OPEN
UPDATE posts SET status = 'OPEN' WHERE status IS NULL;

-- Делаем поле обязательным
ALTER TABLE posts ALTER COLUMN status SET NOT NULL;