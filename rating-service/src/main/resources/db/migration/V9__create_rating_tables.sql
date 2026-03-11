-- Таблица для хранения статистики рейтинга пользователей
CREATE TABLE IF NOT EXISTS user_rating_stats (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 user_id BIGINT NOT NULL UNIQUE,
                                                 total_helps_given BIGINT DEFAULT 0,
                                                 total_helps_received BIGINT DEFAULT 0,
                                                 successful_helps BIGINT DEFAULT 0,
                                                 cancelled_helps BIGINT DEFAULT 0,
                                                 average_response_time DOUBLE PRECISION,
                                                 last_calculated TIMESTAMP,
                                                 current_rating DOUBLE PRECISION DEFAULT 3.0,
                                                 rating_trend VARCHAR(10) DEFAULT 'STABLE',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Индексы для user_rating_stats
CREATE INDEX idx_user_rating_stats_user_id ON user_rating_stats(user_id);
CREATE INDEX idx_user_rating_stats_rating ON user_rating_stats(current_rating DESC);
CREATE INDEX idx_user_rating_stats_last_calculated ON user_rating_stats(last_calculated);

-- Таблица для хранения истории изменений рейтинга
CREATE TABLE IF NOT EXISTS rating_history (
                                              id BIGSERIAL PRIMARY KEY,
                                              user_id BIGINT NOT NULL,
                                              old_rating DOUBLE PRECISION,
                                              new_rating DOUBLE PRECISION NOT NULL,
                                              calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              reason VARCHAR(500),
    help_id BIGINT,
    rating_components TEXT
    );

-- Индексы для rating_history
CREATE INDEX idx_rating_history_user_id ON rating_history(user_id);
CREATE INDEX idx_rating_history_calculated_at ON rating_history(calculated_at DESC);
CREATE INDEX idx_rating_history_user_calculated ON rating_history(user_id, calculated_at DESC);

-- Комментарии к таблицам
COMMENT ON TABLE user_rating_stats IS 'Статистика рейтинга пользователей';
COMMENT ON COLUMN user_rating_stats.user_id IS 'ID пользователя из основного сервиса';
COMMENT ON COLUMN user_rating_stats.total_helps_given IS 'Всего оказано помощи';
COMMENT ON COLUMN user_rating_stats.total_helps_received IS 'Всего получено помощи';
COMMENT ON COLUMN user_rating_stats.successful_helps IS 'Успешно завершенных помощей';
COMMENT ON COLUMN user_rating_stats.cancelled_helps IS 'Отмененных помощей';
COMMENT ON COLUMN user_rating_stats.average_response_time IS 'Среднее время реакции (в минутах)';
COMMENT ON COLUMN user_rating_stats.current_rating IS 'Текущий рейтинг';
COMMENT ON COLUMN user_rating_stats.rating_trend IS 'Тренд рейтинга (UP/DOWN/STABLE)';

COMMENT ON TABLE rating_history IS 'История изменений рейтинга';
COMMENT ON COLUMN rating_history.help_id IS 'ID помощи, вызвавшей изменение';
COMMENT ON COLUMN rating_history.rating_components IS 'JSON с компонентами рейтинга';