# Миграции базы данных (Block 6)

Документ описывает политику миграций, восстановление схемы после V33,
безопасную стратегию для V3 и порядок проверки/отката.

## 1. Политика

1. Уже применённые миграции считаются неизменяемыми: любое изменение файла
   меняет Flyway checksum и ломает `flyway:validate` на существующих базах.
2. Исправления выполняются только вперёд, новыми версиями (`V41`, `V42`, ...).
3. Перед destructive-миграцией обязателен backup (см. раздел 6).
4. Каждая миграция проверяется минимум дважды:
   - на пустой базе;
   - на копии существующей базы с данными.
5. `spring.flyway.validate-on-migrate=true`, `clean-disabled=true`,
   `out-of-order=false` включены в `application.yaml`.
6. CI workflow `.github/workflows/flyway-migrations.yml` выполняет
   `flyway:migrate` на пустой БД, затем `flyway:validate`, а также миграцию
   копии V40-данных с проверкой сохранности строк. Отдельный `validate` на
   пустой схеме не запускается: без `flyway_schema_history` все миграции
   законно считаются pending.

## 2. Почему V33 и V3 не переписаны

### V33

V33 уже применена. Она удалила индексы, которые были ошибочно признаны
лишними. Переписывание V33 изменило бы checksum и потребовало бы ручного
`flyway repair` на каждой базе. Вместо этого V41 возвращает индексы:

- `idx_users_status`
- `idx_users_city`
- `idx_posts_author_name`
- `idx_comments_created_at`
- `idx_messages_created`
- `idx_achievements_user_type` (уникальный, прежнее имя V19)

`refresh_tokens(expires_at)` отдельно не восстанавливается: V36 осознанно
удалила таблицу `refresh_tokens` и заменила её на `auth_refresh_tokens`.
Эквивалентный индекс — `idx_auth_refresh_tokens_expires_at`; V41 проверяет
его наличие.

Дополнительно V41 возвращает soft-delete индексы `idx_posts_deleted`,
`idx_helps_deleted`, `idx_messages_deleted_at`.

### V3

V3 содержит литеральные `UPDATE posts SET user_id = 1..10 WHERE id = 1..10`.
Она также уже применена, поэтому:

- на чистой базе V3 выполняется до появления данных: `posts` пуста, ни один
  пост не привязывается к фиксированным ID;
- на существующей базе V3 уже отработала, её исторические связи сохраняются,
  повторно она не выполняется;
- переписывание V3 не исправило бы уже существующие строки, но сломало бы
  Flyway checksum.

Поэтому V41 содержит явную защиту: если в `posts.user_id` есть `NULL` или
ссылка на несуществующего пользователя, миграция останавливается с понятной
ошибкой. Она не подставляет фиксированные ID и не удаляет посты. Владельца
нужно назначить явно (или сохранить такие посты в архив) до миграции.

Для аудита исторических данных можно выполнить:

```sql
-- Посты, похожие на результат старого фиксированного backfill.
SELECT id, user_id, author_name
FROM posts
WHERE id BETWEEN 1 AND 10
  AND user_id = ((id - 1) % 5) + 1
ORDER BY id;
```

Этот запрос только показывает кандидатов на ручную проверку и ничего не меняет.

## 3. Ограничения целостности (V41)

- `users.rating`: значения вне `1..5` нормализуются (legacy `0` → `3.0`,
  значения `> 5` → `5.0`), затем `NOT NULL`, `DEFAULT 3.0` и
  `CHECK (rating BETWEEN 1 AND 5)`.
- `users.debt_count`: `NULL` → `0`, `NOT NULL`, `DEFAULT 0`,
  `CHECK (debt_count >= 0)`.
- `users.help_coins >= 0`: constraint V39 проверяется/восстанавливается.
- `uq_users_email_normalized` — уникальность `lower(btrim(email))`.
- `uq_helps_active_post` — одна активная помощь на пост, теперь включая
  `CONFIRMED` (ранее только `ACCEPTED`/`COMPLETED`).
- `uq_chat_users` и `uq_chats_normalized_pair` — уникальность чатов;
  добавлены `CHECK (user1_id <> user2_id)` и
  `CHECK (user1_id < user2_id)` (канонический порядок, который уже
  поддерживает `ChatService`).
- `idx_achievements_user_type` — уникальность `(user_id, type)`.

Перед созданием уникальных индексов V41 проверяет дубли и при их наличии
прерывается с сообщением. Дубли не удаляются автоматически.

## 4. Новые таблицы (V42)

Добавлены без изменения существующих данных:

- `reports`
- `report_events`
- `moderation_actions`
- `user_blocks`
- `notifications`
- `notification_preferences`
- `admin_audit_log`
- `outbox_events`

`chat_user_state` уже создана в V37; V42 добавляет недостающий FK
`last_read_message_id → messages(id)`.

## 5. Проверка миграций

### 5.1 Чистая база

```powershell
docker exec postgres psql -U helpTree -d postgres -c "CREATE DATABASE helptree_clean_test;"

$env:DB_URL='jdbc:postgresql://localhost:5432/helptree_clean_test'
$env:DB_USER='helpTree'
$env:DB_PASS='helpTree'

.\mvnw.cmd -B -pl helpTree-service flyway:migrate
.\mvnw.cmd -B -pl helpTree-service flyway:validate

docker cp scripts/fixtures/verify_block6_schema.sql postgres:/tmp/verify_block6_schema.sql
docker exec postgres psql -U helpTree -d helptree_clean_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block6_schema.sql
```

### 5.2 Копия существующей базы

```powershell
docker exec postgres pg_dump -U helpTree -d helpTree_db -Fc -f /tmp/helpTree_db.dump
docker exec postgres psql -U helpTree -d postgres -c "DROP DATABASE IF EXISTS helptree_copy_test;"
docker exec postgres psql -U helpTree -d postgres -c "CREATE DATABASE helptree_copy_test;"
docker exec postgres pg_restore -U helpTree -d helptree_copy_test /tmp/helpTree_db.dump

$env:DB_URL='jdbc:postgresql://localhost:5432/helptree_copy_test'
$env:DB_USER='helpTree'
$env:DB_PASS='helpTree'

.\mvnw.cmd -B -pl helpTree-service flyway:migrate
.\mvnw.cmd -B -pl helpTree-service flyway:validate

docker cp scripts/fixtures/verify_block6_schema.sql postgres:/tmp/verify_block6_schema.sql
docker exec postgres psql -U helpTree -d helptree_copy_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block6_schema.sql
```

Проверьте, что количество строк в `users`, `posts`, `helps`, `chats`,
`messages`, `comments`, `achievements`, `reviews`, `coin_transactions`
совпадает с исходной базой.

### 5.3 CI / повторяемый fixture

`scripts/fixtures/legacy_v40_data.sql` создаёт репрезентативные данные V40,
`verify_block6_schema.sql` проверяет схему, `verify_block6_legacy.sql` —
сохранность строк и нормализацию, `verify_block6_constraints.sql` — что
невалидные записи действительно отклоняются. Эти же файлы используются в
GitHub Actions:

```bash
./mvnw -B -pl helpTree-service flyway:migrate -Dflyway.target=40
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/legacy_v40_data.sql
./mvnw -B -pl helpTree-service flyway:migrate
./mvnw -B -pl helpTree-service flyway:validate
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block6_schema.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block6_legacy.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block6_constraints.sql
```

## 6. Backup перед миграцией

Для локального docker-окружения:

```powershell
docker exec postgres pg_dump -U helpTree -d helpTree_db -Fc -f /tmp/helpTree_db_pre_v41.dump
docker cp postgres:/tmp/helpTree_db_pre_v41.dump .\helpTree_db_pre_v41.dump
```

Для production используйте штатный backup PostgreSQL (`pg_basebackup` или
`pg_dump -Fc`) и проверяйте restore на отдельной базе. Не запускайте V41/V42
без проверенной точки восстановления.

## 7. Rollback / recovery

Flyway не поддерживает автоматический down-migration. Основной план
восстановления — restore из backup. Emergency-скрипт для V41/V42:
`docs/sql/rollback_v41_v42.sql`.

Он:

- удаляет таблицы V42;
- удаляет constraints/indexes, добавленные V41;
- возвращает имя `uq_achievements_user_type` и старое определение
  `uq_helps_active_post` (без `CONFIRMED`);
- удаляет записи V41/V42 из `flyway_schema_history`.

Важно: нормализация `rating` и `debt_count` не откатывается автоматически.
Если нужно точное состояние до V41, восстановите backup. Перед запуском
rollback-скрипта сделайте backup текущего состояния и проверьте скрипт на
копии базы.

Цикл «rollback → повторный `flyway:migrate` → `validate` → все три
verify-скрипта» проверен на копии fixture-базы: скрипт возвращает схему к
V40, а V41/V42 затем применяются повторно без ошибок.

## 8. Ограничения и замечания

- `V9`, `V21`, `V22` отсутствуют в истории; это существовавшая нумерация,
  Flyway её принимает.
- `V33` больше не считается единственным источником истины по индексам:
  V41 — компенсирующая миграция.
- Пользователь с legacy `rating = 0` (например, admin) после V41 получит
  `3.0`. Это документированная нормализация, а не потеря данных.
- Все новые таблицы пока не используются кодом; они подготовлены для
  следующих доменных блоков.
