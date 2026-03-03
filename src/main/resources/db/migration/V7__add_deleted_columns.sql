-- Добавляет поля deleted и deleted_at для soft-delete
ALTER TABLE users
  ADD COLUMN deleted boolean DEFAULT false NOT NULL,
  ADD COLUMN deleted_at timestamp;

ALTER TABLE posts
  ADD COLUMN deleted boolean DEFAULT false NOT NULL,
  ADD COLUMN deleted_at timestamp;

ALTER TABLE helps
  ADD COLUMN deleted boolean DEFAULT false NOT NULL,
  ADD COLUMN deleted_at timestamp;
