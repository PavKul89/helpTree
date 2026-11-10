# Производительность (Block 10)

Документ фиксирует границы, которые сервер накладывает на тяжёлые операции,
и способы измерить соблюдение этих границ. Все лимиты заданы на сервере:
клиент может запросить меньше, но не больше.

## 1. Цели и общие правила

- Ни один публичный список не выгружается целиком. Пагинация считается в
  PostgreSQL; ответ ограничен `app.pagination.max-page-size` (по умолчанию
  100).
- Тяжёлые операции (граф, лента, retention, загрузка изображений) всегда имеют
  верхнюю границу по объёму работы. Лимит — часть контракта, а не подсказка
  клиенту.
- N+1 устраняется на уровне модели: `@BatchSize(size = 50)` на сущностях,
  которые загружаются через ленивые `@ManyToOne`, и `@EntityGraph` на
  выборках, которым нужны связи сразу.
  `spring.jpa.properties.hibernate.default_batch_fetch_size=50` страхует
  остальные ленивые связи.
- Публичные GET используют ETag; кэширование профилей и рейтингов
  инвалидируется после commit, а не до него.

## 2. Лента активности

`ActivityRepository` собирает ленту одним native-запросом с `UNION ALL` по
источникам `HELP_GIVEN`, `HELP_RECEIVED`, `POST_CREATED`,
`ACHIEVEMENT_EARNED`, `USER_BLOCKED`.

- `ORDER BY event_time DESC, event_id DESC` и `LIMIT/OFFSET` выполняются в
  базе. В памяти нет слияния нескольких top-N списков.
- `countQuery` считает только строки, прошедшие те же visibility-предикаты,
  поэтому `totalElements` соответствует выдаче.
- `ActivityController` ограничивает `size` значением
  `PaginationConfig.maxPageSize`; отрицательная страница приводится к `0`;
  legacy-параметр `limit` работает как алиас `size`.
- Тип события валидируется до SQL. Неизвестный тип возвращает `400`, а не
  пустой результат.
- Публичный зритель видит только `CONFIRMED` помощи, публичные статусы
  постов (`OPEN`, `IN_PROGRESS`, `COMPLETED`) и достижения. Имена и id
  участников, а также `USER_BLOCKED`, возвращаются только владельцу или
  администратору (`includePrivate`).
- Индексы V47: `idx_helps_helper_activity`, `idx_helps_receiver_activity`,
  `idx_posts_user_activity`, `idx_achievements_user_earned`.

Ожидаемый объём: не более `size` строк на страницу и не более
`max-page-size` строк на запрос. Время — см. раздел 9.

## 3. Граф помощи

`HelpService.getHelpGraph` больше не загружает все подтверждённые помощи в
память. Пользовательский граф считает `WITH RECURSIVE ... CYCLE` в
PostgreSQL, глобальный — одним `SELECT` с `LIMIT`.

- `app.graph.default-max-edges` (500) — лимит по умолчанию;
  `app.graph.max-edges` (2000) — жёсткий потолок, который нельзя превысить
  параметром запроса.
- `app.graph.max-depth` (10) — максимальная глубина обхода. `CYCLE id SET
  is_cycle` защищает от зацикливания на некорректных данных.
- `totalHelps` считается отдельным `COUNT` по тому же подграфу, поэтому
  усечение выдачи не искажает общее число.
- Узлы загружаются одним `IN`-запросом `UserRepository.findGraphUsersByIds`
  и только публичными полями (`GraphUserProjection`): id, имя, аватар,
  количество помощи, долг, рейтинг. Email/телефон и другие PII в ответ не
  попадают.
- `HelpGraphMetrics` пишет размер и длительность построения
  (`helptree.graph.*`), поэтому усечение и деградация видны без профилировщика.

## 4. JPA и N+1

- `@BatchSize(50)` добавлен к сущностям `User`, `Post` и `Help`, чтобы ленивые
  `@ManyToOne`-связи инициализировались пакетом, а не по одной; на коллекции
  `Post.imageUrls` аннотация стоит на самой коллекции. Hibernate не разрешает
  `@BatchSize` на property `@ManyToOne`, поэтому класс-цель — правильное место.
- `HelpRepository` и `PostRepository` используют `@EntityGraph` для выборок,
  которым сразу нужны автор/помощник/пост; это заменяет серию одиночных
  `SELECT` одним join.
- `UserRepository.findGraphUsersByIds` — проекция, а не сущности: меньше
  данных, нет лишних managed-объектов.
- `RatingService` и `ScheduledRatingService` работают пакетами
  (`rating.calculation.batch-size`, по умолчанию 100) и используют
  cursor-пагинацию, а не `findAll()`.

## 5. Кэши

### 5.1. Публичный профиль

`UserService.getUserPublicById` помечен
`@Cacheable("publicUserProfile")`. Кэш — Caffeine, `maximumSize=1000`,
`expireAfterWrite=60s`.

Инвалидация выполняется через `PublicProfileCache.evictAfterCommit`:

- после commit, иначе конкурентный читатель может перезаписать кэш старым
  значением;
- во всех write-путях профиля: изменение данных, удаление/восстановление,
  смена роли, блокировка/разблокировка, долг, рейтинг, аватар;
- `WalletService` инвалидирует при изменении долга, ника и покупке
  разблокировки;
- `RatingCalculationService` инвалидирует и `userRating`, и
  `publicUserProfile`.

TTL в 60 секунд — страховка на случай пропущенной инвалидации, а не основной
механизм согласованности.

### 5.2. Геокодинг

`geocoding_cache` (V46) — durable-кэш и очередь. HTTP-запрос только читает
`FOUND`-строку или ставит `PENDING`; сетевой вызов Nominatim делает
`GeocodingScheduler` с rate limit и повторными попытками. Это одновременно
кэш и outbox для асинхронного side effect: падение worker-а не теряет запрос,
а запрос пользователя не ждёт внешнюю сеть.

### 5.3. HTTP-кэш

`PublicCachePolicy` — белый список путей, для которых включаются ETag и
`Cache-Control`. `ShallowEtagHeaderFilter` буферизует ответ, поэтому он
зарегистрирован только для публичных GET.

- анонимные проекции: `public, max-age=30, stale-while-revalidate=30`;
- auth-aware (`/api/users/{id}/public`, `/api/activities`,
  `/api/users/{id}/activities`): `private, no-cache` + `Vary:
  Authorization, Cookie`, потому что видимость зависит от владельца/админа;
- запись, загрузки, админские списки и остальные авторизованные эндпоинты не
  кэшируются и не проходят через ETag-буфер.

## 6. Очистка истории рейтингов

`ScheduledRatingService.cleanupOldHistory` вызывает
`RatingHistoryCleanupService.deleteBatch` в цикле.

- `rating.history.retention-days` (365) — что можно удалять;
- `rating.history.cleanup-batch-size` (1000) — сколько строк удаляется в одной
  транзакции;
- `rating.history.max-batches-per-run` (1000) — верхняя граница работы за
  запуск;
- `RatingHistoryRepository.deleteOldBatch` использует
  `DELETE ... WHERE id IN (SELECT id ... ORDER BY calculated_at LIMIT :n)`,
  поэтому блокировка и WAL ограничены батчем;
- счётчик `helptree.rating.history.deleted` показывает фактический объём
  удаления. Нулевые батчи не создают шумовых сэмплов.

## 7. Загрузка изображений

`ImageService.uploadAsync` больше не полагается на `@Async`: вызов из
`uploadMultipleAsync` был self-invocation и обходил Spring proxy. Теперь задача
явно отправляется в `imageUploadExecutor`
(`CompletableFuture.supplyAsync(..., executor)`).

Пул: core 4, max 8, очередь 50, `CallerRunsPolicy`. При переполнении работа
выполняется в вызывающем потоке — это осознанный backpressure вместо
неограниченной очереди. Тест `ImageServiceTest` проверяет, что задача
действительно уходит в переданный executor.

## 8. Инфраструктура

- HTTP-сжатие включено для JSON/XML/HTML/CSS/JS, минимальный размер ответа —
  1024 байта (`SERVER_COMPRESSION_*`).
- Hikari: `maximum-pool-size=20`, `minimum-idle=5`,
  `connection-timeout=30s`, `idle-timeout=10m`, `max-lifetime=30m`
  (`DB_POOL_*`, `DB_*_TIMEOUT_MS`). Пул на инстанс; при горизонтальном
  масштабировании суммарное число соединений должно оставаться ниже
  `max_connections` PostgreSQL.
- JDBC query timeout — 30 секунд (`DB_QUERY_TIMEOUT_SECONDS`). Это аварийный
  предохранитель, а не целевое время: тяжёлые запросы обязаны быть ограничены
  лимитами выше.
- `open-in-view=false`: сессия закрывается после сервисного слоя, случайные
  ленивые загрузки в контроллере невозможны.
- WebSocket: `app.websocket.relay-enabled` включает внешний STOMP relay
  (по умолчанию выключен). При нескольких инстансах приложения relay
  обязателен, иначе сообщения видят только клиенты одного инстанса. Block 21
  добавляет health-индикатор `websocketBroker`: при включённом relay он
  проверяет доступность брокера, не открывая новое соединение на каждый
  запрос. Relay — единственный поддерживаемый способ горизонтального
  масштабирования чата; sticky sessions не заменяют его.
- Outbox: `outbox_events` (V42, партиционирована в V62) — durable-очередь
  асинхронных side effects. Диспетчер забирает батчи через
  `FOR UPDATE SKIP LOCKED`, поэтому несколько экземпляров делят очередь без
  внешней блокировки. Геокодинг использует отдельную `geocoding_cache` (V46).
- Партиционирование (Block 21, V62): `messages` и `outbox_events` разбиты по
  месяцам `created_at`. Это ограничивает размер индексов, ускоряет vacuum и
  позволяет удалять старые данные одним `DROP TABLE` вместо построчного
  `DELETE`. `PartitionMaintenanceScheduler` заранее создаёт текущий и три
  будущих месяца; default-партиция должна оставаться пустой. Retention
  сообщений (`CHAT_RETENTION_DAYS`, по умолчанию 90) выполняется батчами, а
  партиции старше `PARTITIONS_RETENTION_MONTHS` (24) удаляются только если это
  не противоречит retention чата.
- Ретеншн чата: физическое удаление батчами по `created_at`/`id`
  (`retention-batch-size` × `retention-max-batches-per-run`, по умолчанию
  500 × 20) не держит длинную транзакцию и не блокирует отправку новых
  сообщений. Вложения освобождаются отдельно, после исчезновения последней
  живой ссылки.

## 9. Как измерять

### 9.1. Границы объёма (проверяются кодом и тестами)

| Операция | Верхняя граница |
| --- | --- |
| Лента активности | `app.pagination.max-page-size` = 100 строк |
| Граф, пользовательский | `app.graph.max-edges` = 2000 рёбер, `max-depth` = 10 |
| Граф, глобальный | `app.graph.max-edges` = 2000 рёбер |
| Пересчёт рейтингов | `rating.calculation.batch-size` = 100 строк |
| Очистка истории | `cleanup-batch-size` × `max-batches-per-run`, но не более одной транзакции на батч |
| Загрузка изображений | очередь 50, max 8 потоков, затем `CallerRunsPolicy` |
| Экспорт транзакций | `app.wallet.export-max-rows` = 50 000 строк |
| Retention чата | `CHAT_RETENTION_BATCH_SIZE` × `CHAT_RETENTION_MAX_BATCHES_PER_RUN` = 500 × 20 |
| Вложения одного сообщения | `CHAT_MAX_ATTACHMENTS` = 3 |

### 9.2. Время

- `HelpGraphMetrics` уже пишет длительность и размер графа. Смотрите
  `helptree.graph.build.duration` и `helptree.graph.edges`.
- Для activity-запроса и профиля используйте `EXPLAIN (ANALYZE, BUFFERS)` на
  репрезентативной копии базы:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM (
  -- подставить UNION из ActivityRepository
) a
WHERE a.event_type = 'ALL'
ORDER BY a.event_time DESC, a.event_id DESC
LIMIT 20 OFFSET 0;
```

- Для графа:

```sql
EXPLAIN (ANALYZE, BUFFERS)
WITH RECURSIVE reachable(id, depth) AS (
  SELECT CAST(1 AS bigint), 0
  UNION ALL
  SELECT h.receiver_id, r.depth + 1
  FROM helps h
  JOIN reachable r ON h.helper_id = r.id
  WHERE h.status = 'CONFIRMED'
    AND COALESCE(h.deleted, FALSE) = FALSE
    AND r.depth < 10
)
CYCLE id SET is_cycle USING path
SELECT h.id
FROM helps h
WHERE h.status = 'CONFIRMED'
  AND COALESCE(h.deleted, FALSE) = FALSE
  AND h.helper_id IN (SELECT id FROM reachable)
  AND h.receiver_id IN (SELECT id FROM reachable)
ORDER BY h.confirmed_at ASC, h.id ASC
LIMIT 2000;
```

Целевые значения на копии production-подобных данных:

- activity page (20 строк): p95 < 150 мс;
- публичный профиль из кэша: p95 < 20 мс;
- граф с 2000 рёбрами: p95 < 300 мс;
- retention-батч 1000 строк: p95 < 500 мс.

Значения — SLO для проверки регрессий, а не гарантия на любом железе.
Отклонение фиксируется планом (`Seq Scan` вместо индекса, рост `Buffers`,
появление временных файлов) и объёмом (`rows`, `actual rows`).

### 9.3. Проверка индексов

`scripts/fixtures/verify_block10_schema.sql` проверяет наличие и форму
индексов V47. После миграции:

```powershell
docker cp scripts/fixtures/verify_block10_schema.sql postgres:/tmp/verify_block10_schema.sql
docker exec postgres psql -U helpTree -d helpTree_db `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block10_schema.sql
```

Полезные запросы:

```sql
-- Неиспользуемые индексы (после прогрева, не сразу после миграции).
SELECT relname, indexrelname, idx_scan
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC
LIMIT 20;

-- Размеры таблиц и индексов.
SELECT relname,
       pg_size_pretty(pg_total_relation_size(relid)) AS total,
       pg_size_pretty(pg_relation_size(relid)) AS heap
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 20;
```

## 10. Ограничения и дальнейшие шаги

- Индексы V47 созданы обычным `CREATE INDEX`. На большой production-базе
  применяйте эквивалентные `CREATE INDEX CONCURRENTLY` вручную, чтобы не
  блокировать запись; см. `docs/MIGRATIONS.md`.
- Партиционирование `messages`/`outbox_events` внедрено в V62. Порог для
  следующих таблиц (`helps`, `rating_history`, `notifications`) остаётся
  прежним: ~50 млн строк или выход retention-батча за целевое окно. V62 —
  конверсионная миграция и требует окна обслуживания.
- `outbox_events` обслуживается `NotificationOutboxScheduler`; отдельного
  dispatcher-а для остальных side effects нет — они выполняются в транзакции
  или через специализированные таблицы (`geocoding_cache`).
- Relay WebSocket нужно включать в конфигурации при развёртывании более одного
  инстанса. Health-индикатор `websocketBroker` сообщает о недоступности
  брокера, но не переключает приложение в standalone-режим автоматически.
- `MapPage` пока не передаёт `bbox` из viewport; серверная фильтрация по
  `q/category/city` уже используется, `bbox/radius` покрыты интеграционными
  тестами.
