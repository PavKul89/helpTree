-- Создание таблицы users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    city VARCHAR(100),
    helped_count INTEGER DEFAULT 0,
    debt_count INTEGER DEFAULT 0,
    rating DOUBLE PRECISION DEFAULT 0,
    status VARCHAR(20) DEFAULT 'NEWBIE',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
    );

-- Индексы
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_city ON users(city);

-- Комментарии
COMMENT ON TABLE users IS 'Пользователи системы';
COMMENT ON COLUMN users.helped_count IS 'Скольким людям помог';
COMMENT ON COLUMN users.debt_count IS 'Скольким должен помочь (по правилу)';
COMMENT ON COLUMN users.status IS 'NEWBIE, HELPER, ACTIVE, DEBTOR';