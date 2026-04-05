-- =====================================================
-- V11: Добавить колонку role в таблицу users
-- =====================================================

ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'USER' NOT NULL;

COMMENT ON COLUMN users.role IS 'USER или ADMIN';
