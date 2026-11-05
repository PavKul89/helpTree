# Миграции базы данных (Block 6–11)

Документ описывает политику миграций, восстановление схемы после V33,
безопасную стратегию для V3, порядок проверки/отката, проекцию статистики
рейтинга (V43), серверную карту/поиск (V46), индексы производительности
Block 10 (V47) и владение объектами изображений Block 11 (V48).

## 1. Политика

1. Уже применённые миграции считаются неизменяемыми: любое изменение файла
   меняет Flyway checksum и ломает `flyway:validate` на существующих базах.
2. Исправления выполняются только вперёд, новыми версиями (`V41`, `V42`,
   `V43`, ...).
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

## 5. Проекция статистики рейтинга (V43)

V43 не меняет существующие значения вручную: она превращает
`user_rating_stats` в проекцию фактов из `helps` и `reviews` и помечает все
строки к пересчёту (`last_calculated = NULL`).

Добавленные колонки:

- `active_helps_given` — `ACCEPTED` или `COMPLETED` записи помощи;
- `review_count` — неудалённые отзывы, полученные пользователем;
- `average_review_rating` — средняя оценка по этим отзывам.

Семантика существующих колонок закреплена явно:

- `total_helps_given` — все неудалённые записи помощи помощника;
- `successful_helps` — `CONFIRMED` акты;
- `cancelled_helps` — `CANCELLED` записи;
- `total_helps_received` — `CONFIRMED` акты, полученные пользователем.

Миграция пересчитывает эти значения из фактов и создаёт индексы для
агрегатов рейтинга и глобального admin-списка отзывов:

- `idx_helps_helper_status_active`, `idx_helps_receiver_status_active`
  (partial `deleted = false`);
- `idx_helps_status_confirmed_at` (partial `deleted = false`);
- `idx_reviews_to_user_created`, `idx_reviews_rating_created`,
  `idx_reviews_created_at` (partial `deleted = false`);
- `idx_user_rating_stats_last_calculated`.

Проверка: `scripts/fixtures/verify_block7_schema.sql` проверяет колонки,
partial-индексы и совпадение проекции с `helps`/`reviews`.

### 5.1. Покрытие всех пользователей (V44)

V43 обновляла только уже существующие строки. V44 добавляет недостающие
строки `user_rating_stats` для всех неудалённых пользователей и сразу считает
их по той же формуле, что и приложение (база `3.0`, подтверждения, отмены,
отзывы, долг, блокировка, clamp `1..5`, округление до `0.1`). Существующие
строки не перезаписываются (`ON CONFLICT (user_id) DO NOTHING`).

После V44 у каждого существующего пользователя есть проекция, поэтому
топ-рейтинг и `GET /rating` больше не зависят от того, запускался ли для него
пересчёт раньше. Новые пользователи без активности по-прежнему не получают
строку до первого события или первого чтения рейтинга: `RatingService`
материализует проекцию из фактов при чтении (self-heal).

### 5.2. Устойчивые обсуждения (V45)

V45 переводит комментарии на soft-delete и добавляет пагинацию дерева. Она
аддитивна для существующих строк:

- `comments.deleted`, `deleted_at`, `deleted_by_id`, `deleted_by_author`,
  `deleted_reason` — tombstone. Физический `DELETE` приложением больше не
  используется, поэтому `ON DELETE CASCADE` у `parent_comment_id` не срабатывает
  и ветка сохраняет ответы.
- `comments.depth` — 0 для корня; backfill считает глубину рекурсивно по
  `parent_comment_id` (защитный предел 20 для legacy-циклов). Приложение
  ограничивает новые ответы значением `app.comments.max-depth`.
- `comments.updated_at` — заполняется `created_at` для старых строк.
- `posts.comments_count` — проекция числа неудалённых комментариев; backfill
  выполняется в той же миграции, дальше счётчик меняется атомарными `UPDATE`
  в транзакции создания/удаления комментария.

Ограничения:

- `fk_comments_deleted_by` → `users(id)` c `ON DELETE SET NULL`;
- `chk_comments_depth` (`depth >= 0`);
- `chk_comments_deleted_state` (`deleted = FALSE` ⇔ `deleted_at IS NULL`);
- `chk_posts_comments_count` (`comments_count >= 0`).

Индексы:

- `idx_comments_post_roots_created` — partial по корням поста для пагинации;
- `idx_comments_post_thread` — выборка ветки по `parent_comment_id`;
- `idx_comments_post_deleted_created` — подсчёт и модерация.

Проверка: `scripts/fixtures/verify_block8_schema.sql` проверяет колонки,
constraints, индексы, корректность `depth` у корней и ответов, согласованность
tombstone-полей и совпадение `posts.comments_count` с фактическими строками.

### 5.3. Серверная карта и поиск (V46)

V46 — аддитивная миграция Block 9. Она не удаляет и не переписывает данные,
кроме двух безопасных backfill-операций:

- `CREATE EXTENSION pg_trgm` и case-insensitive триграммные GIN-индексы:
  `idx_posts_title_trgm`, `idx_posts_description_trgm`,
  `idx_posts_category_trgm`, `idx_users_name_trgm`, `idx_users_city_trgm`,
  `idx_reviews_comment_trgm`. Они обслуживают `ILIKE '%…%'` в
  `PostSpecification.mapFilter` и `SearchRepository`.
- Partial-индексы для публичных выборок: `idx_posts_map_active` (координаты
  есть, `deleted = FALSE`, `status <> 'CANCELLED'`),
  `idx_posts_map_category` (категория + статус + координаты),
  `idx_posts_public_feed` (`created_at DESC, id DESC`),
  `idx_users_public_rating`, `idx_helps_created`.
- `geocoding_cache` — durable очередь и кэш геокодирования. Запросы только
  читают `FOUND`-строки и добавляют `PENDING`; сетевой вызов Nominatim делает
  только `GeocodingScheduler` (батч, rate limit, повтор до
  `app.geocoding.max-attempts`, переобработка зависших `PROCESSING`).
- Backfill координат: `posts` без координат получают координаты автора, если у
  него они уже есть. Вручную заданные координаты поста не перезаписываются.
- Очередь предзаполняется городами пользователей без координат; ключ
  нормализуется так же, как `GeocodingService.normalizeKey` (lowercase,
  схлопывание пробелов, удаление ведущего `г.`/`г`).

Ограничения V46: `geocoding_cache_status_chk` (допустимые статусы) и
`geocoding_cache_attempts_chk` (`attempts >= 0`). Индекс
`idx_geocoding_cache_queue` упорядочивает выборку worker-а по
`status, attempts, updated_at`.

Проверка: `scripts/fixtures/verify_block9_schema.sql` проверяет расширение,
типы и partial-условия индексов, структуру `geocoding_cache`, constraints и
инварианты очереди (нормализованный ключ, `FOUND` с координатами), а также
наличие backfill-координат у видимых постов.

### 5.4. Индексы производительности (V47)

V47 — аддитивная миграция Block 10. Она не переписывает и не удаляет строки;
единственное изменение схемы — индексы, обслуживающие ленту активности,
рекурсивный граф помощи и retention истории рейтингов.

Лента активности (`ActivityRepository`):

- `idx_helps_helper_activity` — `(helper_id, COALESCE(confirmed_at,
  completed_at, accepted_at, created_at) DESC, id DESC)`, partial по
  `COALESCE(deleted, FALSE) = FALSE`;
- `idx_helps_receiver_activity` — тот же порядок для `receiver_id`;
- `idx_posts_user_activity` — `(user_id, created_at DESC, id DESC)`, partial;
- `idx_achievements_user_earned` — `(user_id, earned_at DESC)`, partial по
  `earned_at IS NOT NULL`.

Граф помощи (`HelpRepository.findUserGraphEdges` / `countUserGraphEdges`):

- `idx_helps_confirmed_edges` — `(helper_id, receiver_id, id)`, partial по
  `status = 'CONFIRMED' AND COALESCE(deleted, FALSE) = FALSE`;
- `idx_helps_confirmed_order` — `(status, confirmed_at ASC, id ASC)`, partial по
  живым строкам; используется глобальным графом и count-запросом.

Retention истории рейтингов:

- `idx_rating_history_retention` — `(calculated_at ASC, id ASC)`; позволяет
  подзапросу `DELETE ... LIMIT` выбирать идентификаторы без heap-fetch.

Индексы намеренно partial: legacy-строки могут иметь `deleted = NULL`, поэтому
условие записано как `COALESCE(deleted, FALSE) = FALSE`, а не `deleted =
FALSE`. Это совпадает с предикатами приложения и исключает soft-deleted
историю из индекса.

Проверка: `scripts/fixtures/verify_block10_schema.sql` проверяет наличие всех
индексов V47 и их partial-условия/порядок колонок.

### 5.5. Владение изображениями (V48)

V48 — аддитивная миграция Block 11. Она создаёт `image_objects` — реестр
объектов, которые приложение хранит в Silo, — и не удаляет существующие
данные.

Схема:

- `owner_id` → `users(id)` с `ON DELETE CASCADE`;
- `object_key` уникален и генерируется только сервером (UUID + расширение);
- `url` хранится отдельно от ключа, чтобы клиент не мог подменить адрес
  объекта;
- `content_type`, `size_bytes`, `width`, `height`, `sha256` описывают уже
  перекодированные байты, которые реально лежат в bucket;
- `purpose` (`AVATAR`, `POST`, `GENERAL`) и `status` (`ACTIVE`, `DELETED`)
  вместе с `deleted_at` образуют tombstone-модель;
- `chk_image_objects_deleted_state` запрещает `ACTIVE` с `deleted_at` и
  `DELETED` без него.

Индексы:

- `idx_image_objects_owner_active` — подсчёт квоты по `owner_id` только для
  `ACTIVE`;
- `idx_image_objects_deleted_cleanup` — выборка tombstone по `deleted_at`;
- `idx_image_objects_orphan_candidates` — кандидаты на очистку по
  `created_at, id` только для `ACTIVE`;
- `idx_image_objects_url` — поиск активного объекта по публичному URL при
  удалении.

Backfill:

- аватары `users.avatar_url` и изображения `post_images.image_url`, которые
  указывают на стандартный bucket `helptree-images`, получают `ACTIVE`-строки;
- существующие объекты в bucket не изменяются и не удаляются;
- для legacy-строк размер неизвестен (`size_bytes = 0`), размеры и SHA-256
  остаются `NULL`, но такая строка всё равно считается одним объектом квоты;
- **важно:** при кастомном `SILO_BUCKET` backfill нужно повторить с этим именем
  bucket, иначе cleanup сочтёт старые объекты бесхозными. Предикат V48 намеренно
  ограничен `/helptree-images/`.

Поведение приложения:

- загрузка проверяет magic bytes (JPEG/PNG/GIF), лимиты размера, разрешения и
  пикселей; JPEG/PNG перекодируются с удалением EXIF/ICC/XMP, GIF сохраняется
  без изменений после проверки кадров;
- квота проверяется под `SELECT ... FOR UPDATE` строки пользователя, запись
  создаётся в отдельной транзакции `ImageObjectService`;
- пост может ссылаться только на активные изображения того же автора;
- удаление доступно владельцу или администратору: сначала отвязываются
  `users.avatar_url`/`post_images`, затем строка становится `DELETED`, а объект
  удаляется из Silo после commit;
- `ImageCleanupService` батчами удаляет tombstone, бесхозные `ACTIVE`-строки
  старше TTL и объекты в bucket без строки владения.

Проверка: `scripts/fixtures/verify_block11_schema.sql` проверяет таблицу,
типы, constraints, partial-индексы, инварианты состояния/ключей и покрытие
backfill для стандартного bucket.

## 6. Проверка миграций
### 6.1 Чистая база

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

docker cp scripts/fixtures/verify_block7_schema.sql postgres:/tmp/verify_block7_schema.sql
docker exec postgres psql -U helpTree -d helptree_clean_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block7_schema.sql

docker cp scripts/fixtures/verify_block8_schema.sql postgres:/tmp/verify_block8_schema.sql
docker exec postgres psql -U helpTree -d helptree_clean_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block8_schema.sql

docker cp scripts/fixtures/verify_block9_schema.sql postgres:/tmp/verify_block9_schema.sql
docker exec postgres psql -U helpTree -d helptree_clean_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block9_schema.sql

docker cp scripts/fixtures/verify_block10_schema.sql postgres:/tmp/verify_block10_schema.sql
docker exec postgres psql -U helpTree -d helptree_clean_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block10_schema.sql
```

### 6.2 Копия существующей базы

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

docker cp scripts/fixtures/verify_block7_schema.sql postgres:/tmp/verify_block7_schema.sql
docker exec postgres psql -U helpTree -d helptree_copy_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block7_schema.sql

docker cp scripts/fixtures/verify_block8_schema.sql postgres:/tmp/verify_block8_schema.sql
docker exec postgres psql -U helpTree -d helptree_copy_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block8_schema.sql

docker cp scripts/fixtures/verify_block9_schema.sql postgres:/tmp/verify_block9_schema.sql
docker exec postgres psql -U helpTree -d helptree_copy_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block9_schema.sql

docker cp scripts/fixtures/verify_block10_schema.sql postgres:/tmp/verify_block10_schema.sql
docker exec postgres psql -U helpTree -d helptree_copy_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block10_schema.sql

docker cp scripts/fixtures/verify_block11_schema.sql postgres:/tmp/verify_block11_schema.sql
docker exec postgres psql -U helpTree -d helptree_copy_test `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block11_schema.sql
```

Проверьте, что количество строк в `users`, `posts`, `helps`, `chats`,
`messages`, `comments`, `achievements`, `reviews`, `coin_transactions`
совпадает с исходной базой.

### 6.3 CI / повторяемый fixture

`scripts/fixtures/legacy_v40_data.sql` создаёт репрезентативные данные V40,
`verify_block6_schema.sql` проверяет схему, `verify_block6_legacy.sql` —
сохранность строк и нормализацию, `verify_block6_constraints.sql` — что
невалидные записи действительно отклоняются, `verify_block7_schema.sql` —
колонки/индексы V43 и совпадение проекции с фактами. Эти же файлы
используются в GitHub Actions:

```bash
./mvnw -B -pl helpTree-service flyway:migrate -Dflyway.target=40
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/legacy_v40_data.sql
./mvnw -B -pl helpTree-service flyway:migrate
./mvnw -B -pl helpTree-service flyway:validate
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block6_schema.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block6_legacy.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block6_constraints.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block7_schema.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block8_schema.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block9_schema.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block10_schema.sql
psql -v ON_ERROR_STOP=1 -f scripts/fixtures/verify_block11_schema.sql
```

## 7. Backup перед миграцией

Для локального docker-окружения:

```powershell
docker exec postgres pg_dump -U helpTree -d helpTree_db -Fc -f /tmp/helpTree_db_pre_v41.dump
docker cp postgres:/tmp/helpTree_db_pre_v41.dump .\helpTree_db_pre_v41.dump
```

Для production используйте штатный backup PostgreSQL (`pg_basebackup` или
`pg_dump -Fc`) и проверяйте restore на отдельной базе. Не запускайте
V41–V47 без проверенной точки восстановления.

## 8. Rollback / recovery

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

V43 — аддитивная миграция (колонки, backfill, partial-индексы), отдельного
rollback-скрипта не имеет. V44 — только данные (`INSERT ... ON CONFLICT DO
NOTHING`), откат не требуется: удаление строк не безопасно и не нужно.
V45 — тоже аддитивная (колонки, constraints, индексы, backfill `depth` и
`comments_count`); после отката кода старый физический delete продолжит
каскадно удалять ответы, поэтому откатывать приложение и схему следует вместе
или восстанавливать backup. Оставленные после отката кода колонки безопасны:
они заполнены и не используются старой версией приложения. V46 — аддитивная
(расширение, индексы, очередь `geocoding_cache` и backfill координат);
отдельного rollback-скрипта не имеет. Старый код не обращается к новым
таблицам/индексам, поэтому откат приложения безопасен; удаление
`geocoding_cache` приведёт лишь к повторному заполнению очереди при следующей
миграции. V47 — только индексы; отдельного rollback-скрипта не имеет. Откат
кода безопасен: старые версии не зависят от новых индексов, а `DROP INDEX`
не требуется для работы приложения. V48 — аддитивная (таблица `image_objects`,
индексы и backfill); отдельного rollback-скрипта не имеет. Перед откатом кода
отключите `upload.cleanup.enabled`: старая версия не знает о реестре и не
обновляет его. Удаление таблицы не удаляет объекты из Silo, но теряет
информацию о владельцах и размерах; после повторного применения V48 backfill
восстановит только строки стандартного bucket. Для точного состояния до V43
восстановите backup.

## 9. Ограничения и замечания

- `V9`, `V21`, `V22` отсутствуют в истории; это существовавшая нумерация,
  Flyway её принимает.
- `V33` больше не считается единственным источником истины по индексам:
  V41 — компенсирующая миграция.
- Пользователь с legacy `rating = 0` (например, admin) после V41 получит
  `3.0`. Это документированная нормализация, а не потеря данных.
- Новые таблицы V42 пока не используются кодом; Block 7 использует только
  `user_rating_stats`, `helps` и `reviews`.
- После V43 все строки `user_rating_stats` помечены `last_calculated = NULL`.
  Периодический пересчёт и первые операции confirm/cancel/review пересоберут
  их из фактов; до этого колонки уже содержат корректный backfill.
- V44 добавляет строки для пользователей, у которых их не было, и сразу
  заполняет проекцию по формуле. Блокировка/разблокировка, погашение долга и
  платная разблокировка теперь пересчитывают рейтинг в той же транзакции.
- V45 закрепляет soft-delete комментариев: `DELETE /comments/{id}` создаёт
  tombstone, а `ON DELETE CASCADE` остаётся только защитой от физического
  удаления вне приложения. Жалобы Block 8 хранятся в таблицах V42 `reports`.
- V46 требует `pg_trgm`; на managed PostgreSQL расширение должно быть
  разрешено для роли приложения (расширение trusted, но политика провайдера
  может отличаться). Геокодирование после V46 полностью асинхронное: запрос
  никогда не ходит в Nominatim, поэтому при отключённом worker-е
  (`app.geocoding.enabled=false`) новые города не получат координаты до
  повторного включения. Wildcard-символы `%`/`_` в поиске вырезаются до
  передачи в SQL.
- V47 создаёт только индексы и не меняет данные. На большой базе
  `CREATE INDEX` (без `CONCURRENTLY`) берёт блокировку записи на время
  построения; для production предпочтите выполнить эквивалентные
  `CREATE INDEX CONCURRENTLY` вручную, затем `flyway repair` не требуется,
  так как имена и определения совпадают. Retention-удаление истории рейтингов
  выполняется приложением батчами (`RatingHistoryCleanupService`), а не внутри
  миграции.
- V48 индексирует таблицу владения изображениями, но сам bucket живёт вне
  транзакций PostgreSQL. Согласованность обеспечивается порядком операций
  (Silo → commit строки, при ошибке — компенсация) и cleanup-джобом; строгой
  распределённой транзакции между БД и Silo нет. Legacy-строки имеют
  `size_bytes = 0` и `NULL` в размерах/SHA-256, поэтому квота для них считается
  по количеству объектов, а не по байтам. Bucket scan удаляет только объекты
  без строки владения; если bucket используется другим приложением, его нужно
  отключить (`upload.cleanup.bucket-scan-enabled=false`).
