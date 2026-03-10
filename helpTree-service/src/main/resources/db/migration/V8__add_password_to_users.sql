-- src/main/resources/db/migration/V8__add_password_to_users.sql
ALTER TABLE users
    ADD COLUMN password VARCHAR(200);

UPDATE users SET password = '' WHERE password IS NULL;
ALTER TABLE users ALTER COLUMN password SET NOT NULL;
