# Распределённые лимиты запросов и защита входа (Block 12)

Документ описывает серверный rate limiting и временную блокировку входа после
V49. Главное требование — согласованность между несколькими экземплярами
приложения: счётчики и блокировки хранятся не в памяти процесса, а в общей
PostgreSQL, которую приложение и так использует.

## 1. Состав

| Компонент | Назначение |
| --- | --- |
| `filter/RateLimiterFilter` | Быстрый HTTP-лимитер внутри security chain; отдаёт 429 до контроллера. |
| `service/ratelimit/DistributedRateLimiter` | Общий API инкремента и сброса счётчика. |
| `service/ratelimit/RateLimitStore` | Интерфейс распределённого хранилища. |
| `service/ratelimit/JdbcRateLimitStore` | PostgreSQL-реализация: атомарный upsert, блокировки, cleanup. |
| `service/ratelimit/RateLimitKeys` | Нормализация и хеширование ключей. |
| `service/ratelimit/LoginProtectionService` | Политика неудачных входов и lockout. |
| `scheduler/RateLimitCleanupScheduler` | Bounded-удаление истёкших строк. |
| `config/RateLimitConfig` | `app.rate-limit.*`. |
| `config/LoginProtectionProperties` | `app.login-protection.*`. |
| `service/ChatRateLimiter`, `service/CommentRateLimiter` | Пользовательские лимиты чата и комментариев через тот же store. |
| `service/AuthService` | Проверяет lockout и фиксирует успех/неудачу входа. |

## 2. Хранилище

V49 создаёт две аддитивные таблицы:

- `rate_limit_buckets` — fixed-window счётчики;
- `login_locks` — временные блокировки пары (IP, аккаунт).

Инкремент выполняется одним `INSERT ... ON CONFLICT (bucket_key) DO UPDATE`:

- если окно истекло (`expires_at <= LOCALTIMESTAMP`), счётчик начинается с 1,
  а окно сдвигается;
- иначе `request_count` увеличивается на 1;
- `RETURNING` сразу отдаёт новое значение и остаток окна, поэтому отдельного
  чтения и гонки между чтением и записью нет.

`JdbcRateLimitStore` выполняет все изменяющие операции в
`Propagation.REQUIRES_NEW`. Это важно для логина и комментариев: если бизнес-
транзакция затем откатится, уже учтённая попытка не исчезнет из счётчика.
Иначе клиент мог бы бесконечно сбрасывать лимит, отправляя заведомо неверные
запросы.

TTL:

- `expires_at` (счётчики) и `locked_until` (блокировки) — логический TTL;
  просроченная строка игнорируется сразу, без ожидания cleanup;
- `RateLimitCleanupScheduler` физически удаляет истёкшие строки раз в 5 минут
  батчами (`cleanup-batch-size`, не более 10 батчей за запуск). Один запуск
  ограничен, чтобы не держать долгую транзакцию и не раздувать WAL.

## 3. Ключи

HTTP-фильтр строит ключ:

```
rl:<route>:<client-ip>:<account>
```

- `route` — стабильное имя правила, а не сырой URL с идентификаторами.
  Числовые и UUID-сегменты заменяются на `{id}`
  (`/api/posts/123` → `/api/posts/{id}`), поэтому один и тот же ресурс не
  создаёт бесконечное число buckets.
- `client-ip` — `ClientInfoResolver.resolveIp`; при отсутствии адреса —
  `unknown`.
- `account` — `u<userId>` после `JwtAuthFilter` или `anon`.

Account-часть обязательна: несколько пользователей за одним NAT/прокси не
должны делить один счётчик после аутентификации. Для анонимных маршрутов
(login, register) остаётся только IP.

`RateLimitKeys.normalize` обрезает ключ до `VARCHAR(255)`; слишком длинный
ключ хешируется (`sha256:...`). Логин-ключи хешируются всегда, чтобы email не
попадал в таблицу.

## 4. Правила HTTP-фильтра

Значения по умолчанию (окно — `app.rate-limit.window-seconds`, 60 секунд):

| Правило | Маршрут | Лимит | Ключ |
| --- | --- | --- | --- |
| `auth.login` | `POST /api/auth/login` | 10/мин | IP + `anon` |
| `auth.register` | `POST /api/auth/register` | 5/мин | IP + `anon` |
| `auth.token` | `POST /api/auth/password/reset/*`, `POST /api/auth/email/*` | 5/мин | IP + account |
| `upload` | `POST /api/images`, `/api/images/multiple`, `POST /api/users/{id}/avatar` | 20/мин | IP + account |
| `auth` | остальные `/api/auth/**` | 60/мин | IP + account |
| `write:<path>` | POST/PUT/PATCH/DELETE по posts/comments/helps/reviews/chats/reports/users | 60/мин | IP + account |
| `route:<METHOD>:<path>` | все остальные маршруты | 600/мин | IP + account |

Фильтр пропускает без учёта:

- `OPTIONS` (CORS preflight);
- `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`;
- `/ws/**` (WebSocket handshake и STOMP);
- `/error`, `/favicon.ico`.

Порядок в security chain:

```
JwtAuthFilter → RateLimiterFilter → authorization → controller
```

`JwtAuthFilter` заполняет `X-User-Id`, поэтому лимитер видит аккаунт.
Servlet-авторегистрация обоих фильтров отключена (`FilterRegistrationBean`
с `enabled=false`), иначе фильтр выполнился бы второй раз вне chain.

Ответ при превышении — быстрый 429 без входа в контроллер:

- `Retry-After` — секунды до конца текущего окна;
- `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`;
- `Cache-Control: no-store`;
- JSON `{status, error, message, path}`.

Ограничения чата и комментариев используют те же таблицы:

- `chat:user:<id>` — `app.chat.messages-per-minute`;
- `comment:user:<id>` — `app.comments.per-minute`.

`RateLimitExceededException` из сервисов обрабатывается
`GlobalExceptionHandler` и также отдаёт 429 + `Retry-After`.

## 5. Защита входа

Политика по умолчанию:

- 5 неудачных попыток за 15 минут → блокировка пары (IP, нормализованный email)
  на 15 минут;
- успешный вход сбрасывает и счётчик неудач, и блокировку;
- истёкшая блокировка снимается автоматически по `locked_until`;
- во время блокировки `AuthService` отвечает 429 + `Retry-After`, не обращаясь
  к таблице пользователей.

Ключ блокировки — `login-lock:<sha256(ip, email)>`, ключ неудач —
`login-failure:<sha256(ip, email)>`. Email в открытом виде не хранится.

Почему (IP, email), а не email:

- атакующий не может заблокировать чужой аккаунт, зная только адрес: его
  неудачи считаются для его собственного IP;
- легитимный пользователь с другого IP не страдает от чужого перебора;
- цена — атакующий с большим пулом адресов может перебирать медленнее; это
  дополнительно ограничивает `auth.login` (10/мин на IP).

Неизвестный email и неверный пароль возвращают одинаковый
`401 Unauthorized` с текстом «Неверный email или пароль». Для неизвестного
email всё равно выполняется проверка по dummy-хешу, чтобы время ответа было
сопоставимым.

Блокировка пары не является блокировкой аккаунта: после истечения TTL вход
снова доступен без администратора. `users.blocked_until` — отдельный механизм
модерации, не связанный с rate limiting.

## 6. Proxy headers

`ClientInfoResolver` читает `X-Forwarded-For` (первый адрес) и `X-Real-IP`
**только** при `app.auth.trust-forwarded-headers=true`
(`TRUST_FORWARDED_HEADERS`). Иначе используется `request.getRemoteAddr()`.

Требования к reverse proxy:

- прокси должен **перезаписывать** `X-Forwarded-For`, а не дописывать
  клиентское значение, иначе клиент подставит любой IP и выберет себе ключ;
- доверять заголовку можно только если приложение недоступно в обход прокси;
- при нескольких прокси первый адрес в цепочке должен быть доверенным.

Если доверять заголовкам нельзя, оставьте `TRUST_FORWARDED_HEADERS=false`:
все клиенты за прокси будут выглядеть как один IP. Account-часть всё равно
разделяет аутентифицированных пользователей, но анонимные login/register
будут делить IP-лимит.

## 7. Отказоустойчивость и несколько экземпляров

- Все экземпляры должны смотреть в одну базу. Тогда два инстанса видят один
  `request_count` и одну блокировку; это и есть критерий согласованности.
- При ошибке store фильтр и `LoginProtectionService` работают **fail-open**:
  запрос пропускается, в лог пишется ERROR. Это осознанный выбор в пользу
  доступности API; при длительном отказе БД защита от перебора ослабевает.
- Cleanup не обязателен для корректности: истёкшие строки и так игнорируются.
  Без cleanup таблицы будут расти, поэтому job не отключайте надолго.
- Redis не требуется. Если в будущем появится отдельный Redis, реализуйте
  `RateLimitStore` поверх него и замените bean: фильтр и сервисы не знают о
  конкретной реализации. Схема V49 при этом останется для совместимости или
  может быть удалена отдельной миграцией после перехода.

## 8. Настройки

`app.rate-limit.*`:

| Свойство | ENV | По умолчанию |
| --- | --- | --- |
| `enabled` | `RATE_LIMIT_ENABLED` | `true` |
| `window-seconds` | `RATE_LIMIT_WINDOW_SECONDS` | `60` |
| `default-limit` | `RATE_LIMIT_DEFAULT` | `600` |
| `auth-limit` | `RATE_LIMIT_AUTH` | `60` |
| `login-limit` | `RATE_LIMIT_LOGIN` | `10` |
| `register-limit` | `RATE_LIMIT_REGISTER` | `5` |
| `password-reset-limit` | `RATE_LIMIT_PASSWORD_RESET` | `5` |
| `upload-limit` | `RATE_LIMIT_UPLOAD` | `20` |
| `create-limit` | `RATE_LIMIT_CREATE` | `60` |
| `cleanup-cron` | `RATE_LIMIT_CLEANUP_CRON` | `0 */5 * * * *` |
| `cleanup-batch-size` | `RATE_LIMIT_CLEANUP_BATCH_SIZE` | `1000` |

`app.login-protection.*`:

| Свойство | ENV | По умолчанию |
| --- | --- | --- |
| `enabled` | `LOGIN_PROTECTION_ENABLED` | `true` |
| `max-attempts` | `LOGIN_MAX_ATTEMPTS` | `5` |
| `failure-window-minutes` | `LOGIN_FAILURE_WINDOW_MINUTES` | `15` |
| `lockout-minutes` | `LOGIN_LOCKOUT_MINUTES` | `15` |

В тестовом `application.yaml` оба механизма выключены: unit- и контекстные
тесты не должны зависеть от общего состояния реальной базы.

## 9. Наблюдаемость

- 429 содержит `Retry-After` и `X-RateLimit-*`; их можно показывать в клиенте.
- В лог пишется route, IP и лимит при срабатывании; текст сообщения не
  содержит email.
- Ошибки store логируются как ERROR с контекстом операции. Рост таких ошибок
  означает проблему с БД, а не с лимитером.

## 10. Проверка

Unit-тесты:

- `RateLimiterFilterTest` — ключи (route/IP/account), 429, заголовки, skip,
  fail-open, disabled;
- `LoginProtectionServiceTest` — порог, TTL, ключ по (IP, email), сброс;
- `AuthServiceTest` — одинаковый ответ для неизвестного email, lockout до
  обращения к БД, сброс после успеха;
- `ChatRateLimiterTest`, `CommentRateLimiterTest` — лимиты и независимость
  пользователей;
- `SecurityConfigTest` — фильтры зарегистрированы только внутри chain.

Интеграционный тест (реальная PostgreSQL, `INTEGRATION_TEST=true`):

- `RateLimitStoreIntegrationTest` — 60 конкурентных инкрементов с двух
  экземпляров store дают 60 различных значений и итоговый `request_count = 60`;
  превышение лимита отклоняется; блокировка видна второму экземпляру и
  автоматически истекает.

Проверка схемы:

```sql
-- scripts/fixtures/verify_block12_schema.sql
\set ON_ERROR_STOP on
```

```powershell
docker cp scripts/fixtures/verify_block12_schema.sql postgres:/tmp/verify_block12_schema.sql
docker exec postgres psql -U helpTree -d helpTree_db `
  -v ON_ERROR_STOP=1 -f /tmp/verify_block12_schema.sql
```

## 11. Rollout и откат

- Миграция V49 аддитивная; применяется на работающей базе.
- Новые экземпляры можно запускать по одному: состояние общее, поэтому
  достаточно, чтобы все они использовали одну БД.
- При rolling-откате старые экземпляры снова используют in-memory лимитеры и
  не видят блокировки новых. На время отката лимиты считаются раздельно;
  уменьшите окно несоответствия или откатывайте весь пул сразу.
- Удаление таблиц V49 сбрасывает счётчики и временные блокировки, но не
  затрагивает пользователей и данные приложения. Повторное применение V49
  создаёт пустые таблицы заново.
