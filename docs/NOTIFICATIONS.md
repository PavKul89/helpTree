# Централизованные уведомления (Block 17)

Block 17 вводит единый домен уведомлений: каноническая запись события, настройки
каналов доставки и гарантированная доставка через outbox. Миграция — **V54**
(`V54__centralized_notifications.sql`).

Ключевой принцип: уведомление — это следствие уже случившегося backend-события.
Строка создаётся в той же транзакции, что и бизнес-факт, поэтому откат операции
не оставляет «фантомных» уведомлений. Наружу (WebSocket, email, Telegram)
уведомление уходит только после успешного коммита.

## 1. Модель данных

### `notifications` (V42, расширена V54)

| Колонка | Тип | Назначение |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Primary key. |
| `user_id` | `BIGINT` | Получатель, `FK → users(id) ON DELETE CASCADE`. |
| `type` | `VARCHAR(50)` | Тип события из `NotificationType`. |
| `title` | `VARCHAR(200)` | Заголовок. |
| `body` | `TEXT` | Текст уведомления. |
| `payload_json` | `JSONB` | Структурированные данные события (V54 переименовала `data`). |
| `dedupe_key` | `VARCHAR(200) NULL` | Детерминированный ключ идемпотентности в рамках пользователя. |
| `read_at` | `TIMESTAMP NULL` | `NULL` = непрочитано. |
| `created_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP`. |

Индексы: `idx_notifications_user_created`, частичный `idx_notifications_user_unread`
и уникальный частичный `uq_notifications_user_dedupe (user_id, dedupe_key)
WHERE dedupe_key IS NOT NULL`.

Каноническая строка создаётся **всегда**, даже если все внешние каналы выключены.
Настройки управляют только доставкой, но не историей.

### `notification_preferences` (V54)

| Колонка | Тип | Назначение |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Primary key. |
| `user_id` | `BIGINT` | `FK → users(id) ON DELETE CASCADE`. |
| `type` | `VARCHAR(50)` | Тип события. |
| `channel` | `VARCHAR(20)` | `IN_APP`, `EMAIL`, `TELEGRAM`. |
| `enabled` | `BOOLEAN` | `DEFAULT TRUE`. |
| `created_at`, `updated_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP`. |

Ограничения: `uq_notification_preferences_user_type_channel
(user_id, type, channel)`, `chk_notification_preferences_channel`,
`chk_notification_preferences_type_not_blank`.

Дефолты эффективной матрицы: **IN_APP — включён, EMAIL и TELEGRAM — выключены**.
Отсутствие строки означает дефолт; `GET /api/notifications/preferences` всегда
возвращает полную матрицу, чтобы клиент не угадывал значения.

### `account_notification_preferences`

V42 создавал `notification_preferences` как account-wide настройки (email/push/
in-app/digest/quiet hours) для Block 14. V54 **переименовывает** таблицу (не
удаляет), PK-индекс и CHECK-констрейнты в `account_*`. Старые настройки остаются
доступны через `/api/account/notifications`.

### `outbox_events` (V42)

Durable intent. Статусы: `PENDING`, `PROCESSING`, `PUBLISHED`, `FAILED`, `DEAD`.
Индекс диспетчеризации в V54 заменён на `(status, available_at, id)`, чтобы выборка
очереди была index-only. Уникальный частичный индекс `uq_outbox_events_dedupe`
не даёт создать два одинаковых intent-а.

## 2. События и их источники

| Тип | Кто получает | Источник | Dedupe key |
| --- | --- | --- | --- |
| `NEW_HELP_RESPONSE` | автор заявки | `HelpService` | `help:{id}:accepted:receiver` |
| `HELP_ACCEPTED` | helper | `HelpService` | `help:{id}:accepted:helper` |
| `HELP_COMPLETED` | receiver | `HelpService` | `help:{id}:completed:receiver` |
| `HELP_CONFIRMED` | helper | `HelpService` | `help:{id}:confirmed:helper` |
| `HELP_CANCELLED` | участники, кроме инициатора | `HelpService` | `help:{id}:cancelled:{userId}` |
| `NEW_REVIEW` | получатель отзыва | `ReviewService` | `review:{reviewId}` |
| `REVIEW_REPLIED` | автор отзыва | `ReviewService` | `review:{reviewId}:reply` |
| `REVIEW_UPDATED` | получатель отзыва | `ReviewService` | `review:{reviewId}:edit:{editCount}` |
| `HELP_SLA_REMINDER` | помощник и получатель | `HelpService` | `help:{id}:reminder:{percent}:{userId}` |
| `PAID_FEATURE_EXPIRING` | владелец VIP | `PaidFeatureExpiryScheduler` | `vip-expiry:{userId}:{vipUntil}:{days}` |
| `NEW_REPORT` | активные администраторы | `ReportService` | `report:{id}:new:admin:{adminId}` |
| `REPORT_ACCEPTED` / `REPORT_REJECTED` | автор жалобы | `ReportService` | `report:{id}:reporter` |
| `ACCOUNT_BLOCKED` / `ACCOUNT_UNBLOCKED` | владелец аккаунта | `UserService`, `ReportService` | `account:*`, `report:{id}:target:{userId}` |
| `MODERATION_ACTION` | владелец контента | `ReportService` | `report:{id}:target:{userId}` |
| `COINS_EARNED` / `COINS_SPENT` | владелец кошелька | `WalletService.applyDelta` | `wallet:{transactionId}` |
| `ACHIEVEMENT_EARNED` | получатель достижения | `AchievementService` | `achievement:{userId}:{type}` |

Правила:

- Пользовательские блокировки (`UserBlockService`) остаются «тихими» — это
  инструмент приватности, а не санкция. Уведомления о блокировке/разблокировке
  аккаунта приходят из account-level `UserService` и решений модерации.
- Монеты уведомляют только при реально применённой операции (`applied == true`),
  достижения — только при `insertIfAbsent > 0`.
- `ReportService.applyAction` — единственный владелец уведомлений по жалобам:
  reporter получает решение, владелец контента/аккаунта — применённую санкцию.

Block 21 добавляет четыре события, и все они идемпотентны по построению:

- `REVIEW_REPLIED` создаётся только при первом ответе получателя; повторное
  редактирование ответа в 24-часовом окне уведомлений не порождает.
- `REVIEW_UPDATED` создаётся на каждую правку отзыва, поэтому `editCount`
  входит в dedupe key: две правки подряд — два разных события, retry одной
  правки — одно.
- `HELP_SLA_REMINDER` отправляется обоим участникам на порогах 50% и 80% от
  `acceptedTimeoutHours`. Ключ включает `percent` и `userId`; отметки
  `reminder_50_sent_at`/`reminder_80_sent_at` на строке помощи закрывают
  повторный прогон даже при рестарте или нескольких экземплярах.
- `PAID_FEATURE_EXPIRING` — только напоминание о конце VIP (7/3/1 день по
  умолчанию). Автопродления нет: ключ включает точный `vipUntil`, поэтому
  продление создаёт новый период и новый набор напоминаний.

## 3. API

Все endpoint-ы требуют авторизации и всегда работают с текущим пользователем из
`AuthorizationService`; `user_id` из запроса не принимается.

| Метод | Путь | Ответ |
| --- | --- | --- |
| `GET` | `/api/notifications?page&size&unreadOnly` | `PageResponse<NotificationDto>` |
| `GET` | `/api/notifications/unread-count` | `{ "unreadCount": N }` |
| `POST` | `/api/notifications/{id}/read` | `{ "unreadCount": N }` |
| `POST` | `/api/notifications/read-all` | `{ "updated": N, "unreadCount": N }` |
| `GET` | `/api/notifications/preferences` | полная матрица + списки `types`/`channels` |
| `PUT` | `/api/notifications/preferences` | обновлённая матрица |

`PUT` — partial patch: тело содержит только изменённые пары
`{ "preferences": [{ "type": "...", "channel": "...", "enabled": false }] }`.
Каждая пара — серверный upsert `ON CONFLICT (user_id, type, channel)`, поэтому две
открытые вкладки настроек не теряют изменения.

`read`/`read-all` идемпотентны: повторный вызов не меняет уже прочитанные строки.
Чужое уведомление по `id` возвращает `404`, а не раскрывает факт его существования.

## 4. Гарантии доставки

1. **Атомарность.** `NotificationService.notify` пишет каноническую строку и
   outbox-intent в транзакции вызывающего бизнес-метода. Откат транзакции
   откатывает и уведомление, и intent.
2. **После коммита.** WebSocket-push и немедленная попытка outbox регистрируются
   через `TransactionSynchronization.afterCommit`. До коммита наружу ничего не
   уходит. Если синхронизация недоступна, действие выполняется сразу.
3. **Outbox.** `PENDING`/`FAILED` события забираются батчем через
   `FOR UPDATE SKIP LOCKED`. Claim фиксируется отдельной транзакцией
   (`REQUIRES_NEW`), доставка — тоже отдельной, поэтому сетевой вызов не держит
   блокировку.
4. **Повторы.** Transient-ошибка переводит событие в `FAILED` с экспоненциальным
   backoff (`retry-base-seconds`, `retry-max-seconds`). После `max-attempts`
   событие становится `DEAD` и больше не повторяется.
5. **Permanent-ошибки** (нет получателя, удалённый аккаунт, неизвестный канал)
   сразу переводят событие в `DEAD` — бессмысленные повторы не выполняются.
6. **Зависшие PROCESSING.** Планировщик перед каждым проходом возвращает в
   `FAILED` строки, у которых `locked_at` старше `processing-timeout-minutes`.
   Попытка уже учтена при claim, поэтому бесконечного цикла нет.
7. **Повторная проверка настроек.** В момент доставки preference проверяется
   заново. Если пользователь выключил канал после enqueue, событие помечается
   `PUBLISHED` без отправки (`last_error = 'Доставка не требуется'`).
8. **Доступность транспорта.** outbox-intent для email/Telegram создаётся только
   если транспорт настроен (`EmailService.isAvailable()`, наличие bot token).
   Если конфигурацию убрали позже, доставка повторится с backoff.

Планировщик (`NotificationOutboxScheduler`) запускается по
`app.notifications.dispatch-cron`; глобальный выключатель —
`app.notifications.enabled`.

## 5. WebSocket

Пользовательский канал: `/user/queue/notifications`, добавлен в whitelist
`StompAuthChannelInterceptor.PRIVATE_USER_QUEUES`.

Envelope:

```json
{
  "type": "NOTIFICATION",
  "notification": { "id": 1, "userId": 7, "type": "NEW_REVIEW", "title": "...", "body": "...", "read": false, "createdAt": "..." },
  "unreadCount": 3,
  "timestamp": "2026-01-01T12:00:00"
}
```

Push выполняется в `REQUIRES_NEW` read-only транзакции: unread-count видит уже
закоммиченную строку, а сбой брокера не откатывает бизнес-операцию. REST-лента
остаётся источником истины; потерянный push восстанавливается при следующей
загрузке.

## 6. Идемпотентность

Ключ `dedupe_key` детерминирован для бизнес-факта. Вставка выполняется native
`INSERT ... ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO
NOTHING`; при конфликте сервис возвращает существующую строку и **не** создаёт
второй outbox-intent и не отправляет второй push. Это защищает от retry на
уровне API, повторной обработки события и гонки двух запросов.

## 7. Frontend

- `notificationApi.ts` — REST-клиент.
- `useWebSocketChat` слушает `/user/queue/notifications` и вызывает
  `onNotification`.
- Одно STOMP-соединение принадлежит `Navbar`; страница получает события через
  `window`-событие `helptree:notification`, а счётчик — через
  `helptree:notification-count`. Второе соединение не открывается.
- Лента дедуплицирует по `notification.id`, поэтому повторная доставка не
  показывает дубль.
- Матрица настроек рендерится по ответу сервера (`types × channels`), без
  локально захардкоженных дефолтов.

## 8. Проверки

- `NotificationServiceTest` — after-commit dispatch, dedupe, kill switch,
  матрица настроек, read/read-all.
- `OutboxDeliveryServiceTest` — успех, skip по настройкам, backoff, DEAD,
  permanent-ошибки.
- `OutboxDispatcherTest` — sweep, батчи, изоляция ошибок, немедленный dispatch.
- `NotificationControllerTest` — авторизация, пагинация, делегирование.
- `NotificationIntegrationTest` (при `INTEGRATION_TEST=true`) — реальный откат
  транзакции без строк, dedupe, read/read-all, upsert настроек, эксклюзивный
  claim, возврат зависших событий, skip и DEAD.
- `scripts/fixtures/verify_block17_schema.sql` — контракт V54 в CI (чистая БД и
  копия данных V40).

## 9. Ограничения и эксплуатация

- Уведомления не являются транзакционным журналом аудита: источник истины —
  бизнес-таблицы. `notifications` можно безопасно чистить по `created_at`.
- `DEAD`-события требуют ручного разбора: причина в `last_error`, полезная
  нагрузка — в `payload`.
- При нескольких экземплярах приложения `SKIP LOCKED` распределяет батчи между
  ними; отдельная блокировка на уровне приложения не нужна.
- Миграция V54 только переименовывает/добавляет объекты и не удаляет строки.
