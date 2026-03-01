-- Таблица для фиксации фактов помощи
CREATE TABLE IF NOT EXISTS helps (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,            -- Какой пост
    helper_id BIGINT NOT NULL,          -- Кто помогает
    receiver_id BIGINT NOT NULL,         -- Кому помогают (автор поста)
    status VARCHAR(20) NOT NULL,         -- статус: ACCEPTED, COMPLETED, CONFIRMED, CANCELLED
    accepted_at TIMESTAMP,               -- когда откликнулся
    completed_at TIMESTAMP,              -- когда отметил как выполненное
    confirmed_at TIMESTAMP,              -- когда автор подтвердил
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_helps_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_helps_helper FOREIGN KEY (helper_id) REFERENCES users(id),
    CONSTRAINT fk_helps_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
    );

-- Индексы
CREATE INDEX idx_helps_post ON helps(post_id);
CREATE INDEX idx_helps_helper ON helps(helper_id);
CREATE INDEX idx_helps_receiver ON helps(receiver_id);
CREATE INDEX idx_helps_status ON helps(status);

-- Комментарии
COMMENT ON TABLE helps IS 'Факты оказанной помощи';
COMMENT ON COLUMN helps.helper_id IS 'Кто помогает';
COMMENT ON COLUMN helps.receiver_id IS 'Кому помогают (автор поста)';
COMMENT ON COLUMN helps.status IS 'ACCEPTED, COMPLETED, CONFIRMED, CANCELLED';