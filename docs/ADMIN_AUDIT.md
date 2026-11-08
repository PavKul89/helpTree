# Административный аудит и расширение админ-панели (Block 18)

Документ описывает серверную модель аудита административных действий и
административные списки, добавленные в Block 18. Главный принцип: **каждое
критичное действие администратора можно восстановить по истории** — кто, когда,
над каким объектом и что изменил.

## 1. Источник истины

Аудит хранится в append-only таблице `admin_audit_log`, созданной в V42.
Схема не переписывалась: V55 добавляет только композитные индексы под новые
серверные фильтры.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigserial` | Первичный ключ. |
| `admin_id` | `bigint null` | Администратор; `ON DELETE SET NULL`, чтобы история пережила удаление аккаунта. |
| `action` | `varchar(80)` | Стабильное имя действия из `AdminAuditAction`. |
| `entity_type` | `varchar(40)` | Тип объекта: `USER`, `POST`, `REVIEW`, `REPORT`, `WALLET`, `SESSION`, `HELP`, `MODERATION`. |
| `entity_id` | `bigint null` | Идентификатор объекта на момент действия. |
| `before_state` | `jsonb null` | Снимок значимых полей до изменения. |
| `after_state` | `jsonb null` | Снимок после изменения. |
| `reason` | `varchar(1000) null` | Причина, введённая администратором. |
| `ip_address` | `varchar(64) null` | IP из запроса (`ClientInfoResolver`). |
| `user_agent` | `varchar(512) null` | User-Agent запроса. |
| `request_id` | `varchar(100) null` | Заголовок `X-Request-Id`, если клиент его передал. |
| `created_at` | `timestamp not null default now()` | Время записи. |

Ограничения `chk_admin_audit_action_not_blank` и
`chk_admin_audit_entity_not_blank` не дают записать пустое действие или тип.

Таблица не обновляется и не удаляется сервисным кодом. Единственный writer —
`AdminAuditService.record(...)`; единственный reader для UI —
`AdminAuditService.search/stats/exportCsv` и локальная история в
`AdminUserService.auditHistory` / `AdminAuditService.history`.

## 2. Каталог действий

`AdminAuditAction` — единый список допустимых значений. Он специально не
содержит свободных строк, чтобы опечатка не создала «дыру» в истории.

| Действие | Когда пишется |
| --- | --- |
| `USER_CREATED`, `USER_INVITED` | Создание пользователя вручную или приглашением. |
| `USER_ROLE_CHANGED` | Смена роли. |
| `USER_DELETED`, `USER_RESTORED` | Soft-delete и восстановление аккаунта. |
| `USER_ANONYMIZED` | Необратимая анонимизация. |
| `USER_DATA_EXPORTED` | Выгрузка данных пользователя. |
| `POST_DELETED`, `POST_RESTORED` | Удаление и восстановление заявки. |
| `POST_STATUS_CHANGED` | Ручная смена статуса заявки. |
| `REVIEW_DELETED` | Удаление отзыва (одиночное или массовое). |
| `REPORT_ASSIGNED` | Назначение жалобы модератору. |
| `REPORT_UPDATED`, `REPORT_STATUS_CHANGED` | Правка и смена статуса жалобы. |
| `MODERATION_ACTION` | Применённое модерационное решение. |
| `COINS_ADJUSTED` | Начисление или списание HelpCoins. |
| `RATING_CHANGED` | Ручная корректировка рейтинга. |
| `SESSION_REVOKED`, `SESSION_REVOKED_ALL` | Отзыв одной сессии или всех сессий. |
| `HELP_OVERRIDE` | Административный override операции помощи. |
| `BULK_OPERATION` | Итог массовой операции. |

`before`/`after` содержат только те поля, которые нужны для восстановления
смысла изменения (статус, роль, баланс, рейтинг, ID транзакции и т. п.), а не
полные ORM-сущности. Это делает записи стабильными при изменении модели.

## 3. Обязательная причина

Для критичных действий причина обязательна и проверяется на сервере, а не
только в UI:

- удаление/восстановление заявки;
- удаление отзыва;
- удаление/восстановление/анонимизация пользователя;
- смена роли;
- корректировка рейтинга;
- начисление/списание монет;
- отзыв сессии;
- административный override помощи.

Пустая или пробельная строка отклоняется с `400`. Причина нормализуется
(`trim`) и обрезается до 1000 символов при записи в аудит; для доменных
таблиц действуют свои лимиты.

Массовые операции требуют одну причину на весь пакет. Каждый элемент
обрабатывается отдельно: ошибка одного не откатывает уже применённые
элементы, а результат возвращается в `AdminBulkResultDto`
(`requested`, `succeeded`, `failed`, `items[]` с `id`, `success`, `message`).
Итоговая запись `BULK_OPERATION` фиксирует действие, количество элементов и
причину.

## 4. Административные endpoint-ы

Все маршруты требуют роль `ADMIN`; проверка выполняется и в контроллере, и в
сервисе.

### Пользователи — `/api/admin/users`

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/` | Поиск с фильтрами `q`, `role`, `status`, `blocked`, `deleted`. |
| `GET` | `/deleted` | Список удалённых (включая анонимизированных). |
| `GET` | `/{id}` | Карточка пользователя. |
| `POST` | `/` | Создание с паролем. |
| `POST` | `/invite` | Приглашение. |
| `PATCH` | `/{id}/role` | Смена роли, причина обязательна. |
| `DELETE` | `/{id}?reason=...` | Soft-delete, причина обязательна. |
| `POST` | `/{id}/restore` | Восстановление, причина обязательна. |
| `POST` | `/{id}/anonymize` | Анонимизация с подтверждением email. |
| `PUT` | `/{id}/rating` | Корректировка рейтинга, причина обязательна. |
| `GET` | `/{id}/sessions` | Активные сессии. |
| `DELETE` | `/{id}/sessions/{sessionId}?reason=...` | Отзыв сессии. |
| `DELETE` | `/{id}/sessions?reason=...` | Отзыв всех сессий. |
| `POST` | `/bulk` | `DELETE` / `RESTORE` / `CHANGE_ROLE` с причиной. |
| `GET` | `/{id}/export?reason=...` | Экспорт данных, причина обязательна. |
| `GET` | `/{id}/audit` | Локальная история по пользователю. |

### Заявки — `/api/admin/posts`

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/deleted` | Удалённые заявки, сортировка по `deletedAt`. |
| `DELETE` | `/{id}?reason=...` | Soft-delete. |
| `POST` | `/{id}/restore` | Восстановление. |
| `PATCH` | `/{id}/status` | Смена статуса, причина обязательна. |
| `POST` | `/bulk` | `DELETE` / `RESTORE` / `CHANGE_STATUS`. |

### Отзывы — `/api/admin/reviews`

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/` | Глобальный список с фильтрами `userId`, `rating`, `from`, `to`. |
| `DELETE` | `/{id}?reason=...` | Удаление отзыва. |
| `POST` | `/bulk` | Массовое удаление с одной причиной. |

### Жалобы — `/api/admin/reports`

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/` | Очередь с фильтрами по статусу, цели, причине, приоритету, модератору, автору, датам и тексту. |
| `GET` | `/sla` | Дашборд SLA модерации. |
| `GET` | `/{reportId}` | Карточка жалобы. |
| `PATCH` | `/{reportId}` | Правка полей. |
| `POST` | `/{reportId}/assign` | Назначение модератора. |
| `POST` | `/{reportId}/actions` | Модерационное решение. |
| `POST` | `/{reportId}/resolve` | Разрешение жалобы. |

### Кошелёк — `/api/admin/wallet`

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/accounts` | Кошельки с фильтрами `q`, `minBalance`, `maxBalance`, `vip`, `blocked`, `debtor`, сортировкой и пагинацией. |
| `GET` | `/reconciliation` | Аномалии баланса. |
| `GET` | `/audits` | Журнал `admin_coin_audits`. |

Начисления и списания выполняются в карточке пользователя. Каждая операция
пишет строку в `admin_coin_audits` (кто, сколько, баланс до/после, причина,
ID транзакции) и запись `COINS_ADJUSTED` в общий аудит.

### Аудит — `/api/admin/audit`

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/` | Поиск по `adminId`, `action`, `entityType`, `entityId`, `from`, `to`, `q`; сортировка по белому списку. |
| `GET` | `/stats` | Агрегаты за окно (по умолчанию последние 30 дней). |
| `GET` | `/export` | CSV с теми же фильтрами. |

## 5. Статистика и экспорт

`GET /api/admin/audit/stats` возвращает:

- `total` — число записей в окне;
- `from`, `to` — фактически применённое окно;
- `byAction`, `byEntityType`, `byAdmin` — распределения.

Агрегаты считаются native-запросами в БД; окно всегда ограничено, поэтому
дашборд не сканирует всю таблицу. Если `from`/`to` не переданы, используется
`[now − 30 дней, now]`; `from > to` отклоняется.

`GET /api/admin/audit/export` отдаёт UTF-8 CSV с BOM (чтобы Excel корректно
открывал кириллицу), заголовком
`id,createdAt,adminId,action,entityType,entityId,reason,ipAddress,requestId,beforeState,afterState`
и экранированием кавычек. Экспорт ограничен 50 000 последних строк,
отсортированных по `createdAt DESC, id DESC`.

## 6. SLA модерации

`GET /api/admin/reports/sla` считает показатели из `reports` и
`report_events`:

- `openReports`, `inReviewReports`, `resolvedReports`, `rejectedReports`,
  `totalReports`;
- `openByPriority` — распределение открытых жалоб по приоритету;
- `averageFirstResponseHours` — среднее время до первого ответа модератора;
- `averageResolutionHours` — среднее время до разрешения/отклонения;
- `oldestOpenCreatedAt`, `oldestOpenAgeHours` — самая старая открытая жалоба.

Период ограничивает выборку по времени создания жалобы. Индекс
`idx_reports_open_queue` ускоряет подсчёт открытой очереди.

## 7. Атомарность и идемпотентность

- Доменное изменение и аудит пишутся в одной транзакции: либо есть и
  изменение, и запись, либо нет ни того, ни другого.
- Поиск и экспорт аудита — read-only транзакции.
- Начисление монет идемпотентно по `reference_id`
  (`admin:<idempotency-key>`). Повтор не создаёт вторую транзакцию и вторую
  аудит-запись.
- Отзыв сессии идемпотентен: повторный отзыв уже отозванной сессии не создаёт
  новую запись.
- Массовая операция фиксирует частичный успех честно: `AdminBulkResultDto`
  перечисляет каждый элемент, а `BULK_OPERATION` хранит сводку.

## 8. Индексы V55

| Индекс | Зачем |
| --- | --- |
| `idx_admin_audit_log_action_created (action, created_at DESC)` | Фильтр по действию в общем журнале. |
| `idx_admin_audit_log_entity_type_created (entity_type, created_at DESC)` | Журнал по типу объекта без ID. |
| `idx_posts_deleted_at` (partial `deleted = TRUE`) | Список удалённых заявок. |
| `idx_reviews_deleted_at` (partial `deleted = TRUE`) | Модерация удалённых отзывов. |
| `idx_reports_open_queue` (partial `status IN ('OPEN','IN_REVIEW')`) | SLA и очередь. |
| `idx_auth_sessions_active_user_created` (partial `revoked_at IS NULL`) | Активные сессии пользователя. |

Все индексы создаются через `IF NOT EXISTS`, миграция идемпотентна.

## 9. Frontend

| Маршрут | Страница | Назначение |
| --- | --- | --- |
| `/admin/audit` | `AdminAuditPage` | Фильтры, статистика, таблица и CSV. |
| `/admin/sla` | `AdminSlaPage` | SLA модерации и очередь по приоритетам. |
| `/admin/wallet` | `AdminWalletPage` | Кошельки с серверными фильтрами. |
| `/admin/requests` | `AdminRequestsPage` | Активные/удалённые заявки, причины, массовые операции. |
| `/admin/reviews` | `AdminReviewsPage` | Глобальные отзывы, удаление с причиной. |
| `/admin/users` | `AdminUsersPage` | Массовые удаление/восстановление/смена роли. |
| `/admin/users/:id` | `AdminUserDetailPage` | Рейтинг, сессии, причины, локальный аудит. |

## 10. Проверка

`scripts/fixtures/verify_block18_schema.sql` проверяет колонки, ограничения,
внешний ключ и все индексы V55. Запускать после `flyway:migrate` на пустой
базе и на копии существующей базы.

`AdminAuditIntegrationTest` (профиль `INTEGRATION_TEST=true`) проверяет
`stats`, `search` и `exportCsv` на реальной PostgreSQL в фиксированном окне:
строки получают уникальный `request_id`, поэтому агрегаты не пересекаются с
другими тестами, а после прогона удаляются. Тест доказывает, что:
`from`/`to` применяются ровно; фильтр по действию нормализуется (`trim`,
upper-case); CSV экранирует кавычки и содержит только отфильтрованные строки.
