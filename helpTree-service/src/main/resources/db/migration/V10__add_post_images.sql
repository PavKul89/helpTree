-- =====================================================
-- V10: Добавление таблицы для изображений постов
-- =====================================================

CREATE TABLE post_images (
    post_id BIGINT NOT NULL,
    image_url TEXT NOT NULL,
    CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE INDEX idx_post_images_post_id ON post_images(post_id);
