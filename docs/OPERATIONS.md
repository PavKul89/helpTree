# Эксплуатация: конфигурация, логи, метрики и алерты (Block 20)

Документ описывает production-контур helpTree: обязательную конфигурацию,
безопасный первый запуск, Actuator, структурированные логи, метрики,
feature flags и реакцию на алерты. Миграции Block 20 не добавляет: схема
остаётся на **V55**.

Главный принцип: приложение не содержит «удобных» fallback-секретов и
default-администратора. Ошибка конфигурации должна остановить запуск, а не
тихо превратиться в инцидент безопасности.

---

## 1. Профили и обязательные переменные

| Профиль | Назначение | Секреты |
| --- | --- | --- |
| `dev` | локальный запуск | обязательны явные значения из `.env` |
| `prod` | production | обязательны, дополнительно проверяются `ProductionConfigValidator` |

Без профиля `prod` приложение тоже не подставляет секреты: в
`application.yaml` используются `${DB_PASS}`, `${JWT_SECRET}`,
`${SILO_ACCESS_KEY}` без значений по умолчанию. Отсутствующая переменная
ломает старт с понятной ошибкой Spring.

### 1.1. Обязательны всегда

| Переменная | Требование |
| --- | --- |
| `DB_URL` | JDBC URL PostgreSQL |
| `DB_USER` | пользователь БД |
| `DB_PASS` | в `prod` — минимум 12 символов, не sample/default |
| `JWT_SECRET` | в `prod` — минимум 64 случайных символа |
| `SILO_ENDPOINT` | внутренний endpoint Silo |
| `SILO_PUBLIC_ENDPOINT` | endpoint, который видят браузеры |
| `SILO_ACCESS_KEY` | не `minioadmin`/`helptree` |
| `SILO_SECRET_KEY` | в `prod` — минимум 16 символов |

Генерация секретов:

```powershell
# JWT_SECRET
openssl rand -base64 64
# DB_PASS / SILO_SECRET_KEY
openssl rand -base64 32
```

`SILO_PUBLIC_ENDPOINT` должен быть доступен клиенту, а `SILO_ENDPOINT` —
серверу. Нельзя выдавать наружу внутренний адрес Docker-сети.

### 1.2. Что проверяет `ProductionConfigValidator`

Bean активен только с профилем `prod` и падает до открытия порта, если:

- `JWT_SECRET`, `DB_PASS`, `SILO_SECRET_KEY` или `SILO_ACCESS_KEY` пустые,
  короткие или содержат маркер примера (`change-me`, `dev-only`, `example`,
  `password`, `minioadmin` и т. п.);
- `PROMETHEUS_SCRAPE_PASSWORD` короче 16 символов или содержит маркер примера;
- включён `WS_RELAY_ENABLED`, но не заданы `WS_RELAY_CLIENT_PASSCODE` и
  `WS_RELAY_SYSTEM_PASSCODE`;
- `ADMIN_BOOTSTRAP_ENABLED=true`, а `ADMIN_EMAIL`/`ADMIN_PASSWORD` не проходят
  проверку;
- в `management.endpoints.web.exposure.include` есть `*`, `env`,
  `configprops`, `heapdump`, `threaddump` или `shutdown`;
- `CORS_ORIGINS` или `WS_ALLOWED_ORIGINS` содержит `*`;
- положительные лимиты (`RATE_LIMIT_*`, `JWT_EXPIRATION_MS`, `UPLOAD_*`,
  `REPORTS_SLA_HOURS` и др.) меньше или равны нулю.

Предупреждения (не блокируют старт): `localhost` в origin, отключённый
rate limit/login protection/reconciliation, `MAIL_ENABLED=true` с
`MAIL_HOST=localhost`, `FRONTEND_BASE_URL` без `https://`.

Секреты никогда не печатаются в сообщении об ошибке: указывается только имя
переменной.

### 1.3. Проверка перед production

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:DB_URL = 'jdbc:postgresql://db:5432/helpTree_db'
$env:DB_USER = 'helpTree'
$env:DB_PASS = '<strong password>'
$env:JWT_SECRET = '<64+ random chars>'
$env:SILO_ENDPOINT = 'http://silo:9000'
$env:SILO_PUBLIC_ENDPOINT = 'https://images.example.org'
$env:SILO_ACCESS_KEY = '<storage user>'
$env:SILO_SECRET_KEY = '<strong storage secret>'

.\mvnw.cmd -B -pl helpTree-service spring-boot:run
```

Если конфигурация неверна, приложение завершится с сообщением
`Production configuration is invalid` и перечнем переменных. Исправьте все
пункты сразу: запуск с частично исправленной конфигурацией снова упадёт.

---

## 2. Первый администратор

Администратор **не создаётся по умолчанию**. На пустой базе приложение
запускается без ADMIN и пишет предупреждение в лог.

Одноразовый bootstrap:

```dotenv
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=admin@example.org
ADMIN_PASSWORD=<12+ chars, 3 character classes, not a sample>
```

Что гарантирует `AdminBootstrapService`:

- вся проверка и вставка идут в одной транзакции;
- `pg_advisory_xact_lock` сериализует несколько экземпляров, поэтому два
  одновременно стартующих pod'а не создадут двух администраторов;
- если активный ADMIN уже есть, bootstrap пропускается (идемпотентность);
- если email уже занят другой учётной записью, bootstrap падает и **не**
  перезаписывает её;
- email маскируется в логах;
- пароль проверяется тем же `SecretRules`, что и production-конфигурация.

После успешного первого старта:

1. убедитесь, что вход под администратором работает;
2. выставьте `ADMIN_BOOTSTRAP_ENABLED=false`;
3. перезапустите приложение.

Не оставляйте bootstrap включённым: это не «постоянный» механизм, а
одноразовая операция.

---

## 3. Actuator и доступ

Actuator закрыт по умолчанию. Публично доступны только:

| Endpoint | Доступ | Назначение |
| --- | --- | --- |
| `/actuator/health` | все | агрегированный статус, без деталей |
| `/actuator/health/liveness`, `/readiness` | все | probes |
| `/actuator/info` | все | версия приложения |
| `/actuator/prometheus` | Basic Auth scrape-пользователя | scrape Prometheus |

`/actuator/metrics` требует `ROLE_ADMIN`. `/actuator/loggers` не включён в
web exposure. Остальные доступные actuator endpoint'ы требуют `ROLE_ADMIN`
(правило `/actuator/**` в `SecurityConfig`). Детали health
(`show-details: when_authorized`) тоже видны только администратору.

Для скрейпера задайте одинаковые `PROMETHEUS_SCRAPE_USERNAME` и
`PROMETHEUS_SCRAPE_PASSWORD` приложению и контейнеру Prometheus через окружение
или `.env`; пароль не хранится в репозитории. Путь
`/actuator/prometheus` принимает только эти отдельные Basic Auth данные, а не
пользовательские JWT.

```bash
# Публичный статус
curl -s https://api.example.org/actuator/health

# Административные endpoint'ы — с JWT администратора
curl -s -H "Authorization: Bearer <admin-token>" \
  https://api.example.org/actuator/metrics/helptree.outbox.backlog

```

Дополнительная изоляция: вынесите Actuator на отдельный порт
(`MANAGEMENT_PORT=8081`) и не публикуйте его через ingress или Docker `ports`.
Тогда
даже ошибка в правилах безопасности не откроет метрики наружу.

В dev-профиле management использует тот же порт, что и приложение, чтобы
Prometheus мог скрейпить локальный backend. В текущем Compose backend запускается
на хосте, поэтому `PROMETHEUS_TARGET` по умолчанию равен
`host.docker.internal:8080`; при переносе backend в Docker укажите
`<имя-контейнера-бэкенда>:8081`.

Swagger и OpenAPI выключены по умолчанию (`SWAGGER_ENABLED=false`) и включаются
в dev-профиле. Список exposure задан явно
(`health, metrics, info, prometheus`) и
исключает `env`, `configprops`, `heapdump`, `threaddump`, `shutdown`.
`ProductionConfigValidator` запрещает вернуть их обратно.

---

## 4. Логи

### 4.1. Формат

- профиль `prod` — одна JSON-строка на запись (`StructuredLogLayout`);
- остальные профили — читаемый pattern (`MaskingPatternLayout`).

Оба варианта проходят через `SensitiveDataMasker`.

Поля production-записи:

| Поле | Источник |
| --- | --- |
| `time` | ISO-8601 UTC |
| `level` | уровень SLF4J |
| `component` | logger name |
| `thread` | имя потока |
| `traceId`, `spanId` | Micrometer Tracing |
| `requestId` | `X-Request-Id` или сгенерированный id |
| `userId` | числовой id из JWT |
| `errorType` | стабильный `ErrorType` |
| `message` | сообщение после маскирования |
| `exception` | stack trace после маскирования |

`TraceIdFilter` кладёт correlation id в MDC и очищает MDC в `finally`,
поэтому идентификаторы не «протекают» между запросами одного потока.
`JwtAuthFilter` добавляет `userId`, `GlobalExceptionHandler` и
`ApiErrorWriter` — `errorType`.

### 4.2. Маскирование

`SensitiveDataMasker` — вторая линия защиты. Даже если код случайно залогирует
чувствительное значение, layout заменит:

- email → `a***@example.com`;
- телефон → `***67`;
- `Bearer ...` → `Bearer [REDACTED]`;
- JWT → `[REDACTED_JWT]`;
- `password`, `secret`, `token`, `refreshToken`, `api_key` в `key=value` /
  `key: value`;
- текст жалобы (`description`, `reason` с свободным текстом) → `[REDACTED]`.

Правило для разработчика: не логировать тело запроса, заголовки, query string,
сырой `User-Agent`, email, телефон, токены и текст жалоб. Используйте
идентификаторы (`userId`, `postId`, `reportId`) и стабильные enum-значения.

Пример production-строки:

```json
{"time":"2026-11-08T16:40:12.481Z","level":"INFO","component":"org.example.helptreeservice.filter.RequestLoggingFilter","thread":"http-nio-8080-exec-3","traceId":"4f2c...","requestId":"9d1a...","userId":"42","message":"http_request method=GET path=/api/helps status=200 durationMs=18 clientIp=10.0.0.5 requestId=9d1a..."}
```

Клиентский IP остаётся в логе как сетевой идентификатор. Если политика
запрещает хранить IP, удалите поле из `RequestLoggingFilter`; маскировать его
как телефон нельзя — это сломает диагностику.

---

## 5. Метрики

Все метрики регистрируются в `HelpTreeMetrics` и доступны на
`/actuator/prometheus`. Теги ограничены: `method`, `uri` (шаблон, не сырой
путь), `status` (группа `2xx`/`4xx`/`5xx`), `reason`, `purpose`, `outcome`,
`action`, `feature`. Идентификаторы пользователей и свободный текст в теги не
попадают.

| Метрика | Тип | Что показывает |
| --- | --- | --- |
| `helptree_api_duration_seconds` | timer | длительность запросов, p95 по `uri` |
| `helptree_api_response_size_bytes` | summary | размер ответа |
| `helptree_api_authorization_errors_total` | counter | 401/403 по статусу |
| `helptree_api_server_errors_total` | counter | 5xx по статусу |
| `helptree_reports_created_total` | counter | созданные жалобы |
| `helptree_reports_open` | gauge | открытые жалобы |
| `helptree_reports_sla_breaches` | gauge | жалобы старше `REPORTS_SLA_HOURS` |
| `helptree_moderation_queue` | gauge | OPEN + IN_REVIEW |
| `helptree_moderation_actions_total` | counter | действия модерации по типу |
| `helptree_wallet_balance_mismatches` | gauge | расхождения ledger и `help_coins` |
| `helptree_outbox_backlog` | gauge | PENDING + PROCESSING + FAILED |
| `helptree_outbox_oldest_age_seconds` | gauge | возраст старейшего неотправленного события |
| `helptree_outbox_delivered_total` | counter | успешные доставки |
| `helptree_outbox_failed_total` | counter | исчерпанные retry |
| `helptree_database_up` | gauge | 1/0 по результату `SELECT 1` |
| `helptree_websocket_sessions` | gauge | открытые STOMP-сессии |
| `helptree_websocket_subscriptions_total` | counter | SUBSCRIBE-кадры |
| `helptree_websocket_disconnections_total` | counter | DISCONNECT-события |
| `helptree_auth_login_success_total` | counter | успешные входы |
| `helptree_auth_login_failures_total` | counter | неудачи по `reason` |
| `helptree_images_uploads_total` | counter | загрузки по `purpose`/`outcome` |
| `helptree_feature_disabled_total` | counter | отказы 503 по `feature` |

Gauge'и очередей и инвариантов обновляет `OperationalMetricsCollector` раз в
`METRICS_REFRESH_MS` (по умолчанию 60 с), а не каждый scrape. Поэтому scrape
дешёвый, а тяжёлый агрегат не может задержать Prometheus.

`helptree.database.up=0` означает, что последний `SELECT 1` не удался; при
этом предыдущие значения gauge'ов сохраняются до следующего успешного
обновления.

---

## 6. Алерты

Правила лежат в `monitoring/alerts.yml`, маршрутизация — в
`monitoring/alertmanager.yml`. Prometheus читает их из
`/etc/prometheus/alerts.yml` (volume в `docker-compose.yml`), Alertmanager
доступен на `:9093`.

| Алерт | Условие | Severity |
| --- | --- | --- |
| `HelpTreeServiceDown` | `up{job="helpTree-app"} == 0` 2 мин | critical |
| `HelpTreeDatabaseUnavailable` | `helptree_database_up == 0` 2 мин | critical |
| `HelpTreeServerErrorRateHigh` | доля 5xx > 5% 5 мин | critical |
| `HelpTreeApiLatencyP95High` | p95 > 1.5 с 10 мин | warning |
| `HelpTreeAuthorizationErrorSpike` | > 1 401/403 в секунду 10 мин | warning |
| `HelpTreeLoginFailureSpike` | > 0.5 неудач входа в секунду 10 мин | warning |
| `HelpTreeReportSpike` | > 20 жалоб за 10 мин | warning |
| `HelpTreeModerationQueueBacklog` | очередь > 100, 15 мин | warning |
| `HelpTreeReportSlaBreach` | SLA breach > 0, 30 мин | warning |
| `HelpTreeOutboxBacklog` | backlog > 100, 15 мин | warning |
| `HelpTreeOutboxOldestEventStale` | возраст > 900 с, 10 мин | critical |
| `HelpTreeOutboxDeliveryFailures` | > 0.1 failed/s, 10 мин | warning |
| `HelpTreeImageUploadFailureRate` | > 20% ошибок загрузок, 10 мин | warning |
| `HelpTreeWebSocketChurn` | > 5 disconnect/s, 10 мин | warning |
| `HelpTreeWebSocketConnectionsHigh` | > 5000 сессий, 5 мин | warning |
| `HelpTreeWalletBalanceMismatch` | расхождений > 0, 5 мин | critical |

Пороги — стартовые. Меняйте их по фактическому трафику, но не отключайте
critical-правила: они покрывают безопасность, целостность денег и доступность.

### 6.1. Маршрутизация

`alertmanager.yml` отправляет всё в `default-webhook`. Замените URL на
корпоративный incident-bridge (Slack/PagerDuty/Opsgenie/email). Critical
повторяется раз в час, warning — раз в 4 часа. `send_resolved: true`
закрывает инцидент автоматически. Секреты вебхука храните в secret store, а
не в файле.

Пример email-получателя (раскомментируйте и настройте `global.smtp_*`):

```yaml
receivers:
  - name: oncall-email
    email_configs:
      - to: 'oncall@example.org'
        send_resolved: true
```

---

## 7. Feature flags

Флаги задаются в `app.features.*` и дублируются переменными окружения:

| Флаг | Переменная | По умолчанию |
| --- | --- | --- |
| `reports` | `FEATURE_REPORTS_ENABLED` | `true` |
| `notifications` | `NOTIFICATIONS_ENABLED` | `true` |
| `user-blocks` | `FEATURE_USER_BLOCKS_ENABLED` | `true` |
| `moderation` | `FEATURE_MODERATION_ENABLED` | `true` |

Семантика:

- проверка выполняется в **сервисах**, поэтому HTTP-запрос, scheduled job и
  внутренний вызов видят одно и то же состояние;
- отключённая пользовательская функция отвечает
  `503 FEATURE_DISABLED` в конверте `ErrorResponse` Block 19 (не RFC 7807);
- `notifications` — это AND продуктового флага и существующего
  `app.notifications.enabled`. `NotificationService.notify()` при выключенных
  уведомлениях возвращает `null` и не бросает исключение, чтобы бизнес-факт
  не откатывался;
- `OutboxDispatcher` при выключенных уведомлениях не забирает события;
- `moderation.restoreExpiredHiddenContent()` при выключенной модерации
  возвращает 0: контент не должен самовосстанавливаться, пока область
  заморожена;
- внутренние проверки блокировок (например, «нельзя писать заблокированному
  пользователю») продолжают работать при выключенном `user-blocks`; fail
  closed применяется только к пользовательскому управлению блокировками.

Флаги читаются при старте (`@ConfigurationProperties`), поэтому изменение
переменной применяется после перезапуска экземпляра. Во всех экземплярах
должно быть одно значение; иначе поведение будет зависеть от того, какая
реплика обработала запрос.

Типовой порядок отключения:

1. выставить `<FEATURE>_ENABLED=false` во всех экземплярах;
2. перезапустить (rolling restart);
3. проверить `helptree_feature_disabled_total{feature="..."}` и 503 в логах;
4. устранить причину и вернуть `true` тем же порядком.

Отключение не удаляет данные и не меняет схему. Это временная мера, а не
замена исправлению.

### 7.1. Конфигурация Block 21

| Переменная | По умолчанию | Назначение |
| --- | --- | --- |
| `CHAT_EDIT_WINDOW_MINUTES` | `15` | Окно правки своего сообщения. |
| `CHAT_MAX_ATTACHMENTS` | `3` | Максимум изображений на сообщение. |
| `CHAT_RETENTION_DAYS` | `90` | Физическое удаление сообщений старше N дней; `0` — хранить бессрочно. |
| `CHAT_RETENTION_CRON` | `0 30 3 * * *` | Расписание retention-уборки. |
| `CHAT_RETENTION_BATCH_SIZE` | `500` | Размер батча удаления. |
| `CHAT_RETENTION_MAX_BATCHES_PER_RUN` | `20` | Верхняя граница батчей за прогон. |
| `HELP_REMINDERS_ENABLED` | `true` | Напоминания 50%/80% для ACCEPTED-помощи. |
| `HELP_REMINDER_FIRST_PERCENT` / `HELP_REMINDER_SECOND_PERCENT` | `50` / `80` | Пороги напоминаний. |
| `HELP_REMINDER_CRON` | `0 0 * * * *` | Расписание напоминаний. |
| `HELP_REMINDER_BATCH_SIZE` | `200` | Размер батча напоминаний. |
| `POST_MAX_LINKS` | `5` | Максимум ссылок в заявке. |
| `POST_DUPLICATE_WINDOW_HOURS` | `24` | Окно защиты от дублей; `0` — выключить. |
| `PARTITIONS_ENABLED` | `true` | Обслуживание месячных партиций. |
| `PARTITIONS_CRON` | `0 15 3 * * *` | Расписание обслуживания. |
| `PARTITIONS_FUTURE_MONTHS` | `3` | Сколько будущих месяцев создавать заранее. |
| `PARTITIONS_DROP_OLD` | `true` | Удалять старые месяцы. |
| `PARTITIONS_RETENTION_MONTHS` | `24` | Глубина хранения партиций (для `messages` не меньше retention чата). |
| `ANTIVIRUS_ENABLED` | `false` | ClamAV INSTREAM, fail-closed. |
| `ANTIVIRUS_HOST` / `ANTIVIRUS_PORT` | `localhost` / `3310` | Адрес ClamAV. |
| `WALLET_EXPORT_MAX_ROWS` | `50000` | Максимум строк в экспорте кошелька. |
| `WALLET_VIP_REMINDER_DAYS` | `7,3,1` | Дни до окончания VIP для напоминаний. |
| `WALLET_VIP_REMINDER_CRON` | `0 0 10 * * *` | Ежедневный прогон напоминаний. |
| `AUTH_LAST_SEEN_TOUCH_MINUTES` | `1` | Как часто обновлять `last_seen_at`. |
| `AUTH_ONLINE_WINDOW_MINUTES` | `5` | Окно, в котором пользователь считается онлайн. |

Связанные health-индикаторы: `antivirus` (при выключенном сканере — `UP` с
`disabled`) и `websocketBroker` (только при `WS_RELAY_ENABLED=true`). Оба
возвращают `DOWN` при недоступной зависимости, но не роняют остальные
проверки: `/actuator/health` агрегирует их по стандартным правилам.

---

## 8. Локальный запуск

```powershell
# 1. Подготовить .env
Copy-Item .env.example .env
# отредактировать .env: DB_PASS, JWT_SECRET, SILO_ACCESS_KEY, SILO_SECRET_KEY,
# GRAFANA_ADMIN_PASSWORD

# 2. Поднять PostgreSQL, Silo, Prometheus, Alertmanager, Grafana, Jaeger
docker compose up -d

# 3. Запустить приложение с профилем dev
powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1
```

`scripts/run-dev.ps1` загружает `.env`, проверяет обязательные переменные,
предупреждает о незаменённых `REPLACE_WITH_*` и запускает
`spring-boot:run` с профилем `dev`. Если `.env` отсутствует, скрипт завершится
с инструкцией; fallback-секретов в приложении нет.

Полезные адреса: приложение `http://localhost:8080`, Prometheus
`http://localhost:9090`, Alertmanager `http://localhost:9093`, Grafana
`http://localhost:3000`, Jaeger `http://localhost:16686`, Silo
`http://localhost:9001`.

`docker-compose.yml` больше не содержит паролей по умолчанию:

- `SILO_ACCESS_KEY`/`SILO_SECRET_KEY` обязательны;
- `GRAFANA_ADMIN_PASSWORD` обязателен;
- PostgreSQL берёт `DB_USER`/`DB_PASS` из `.env`.

---

## 9. Runbook: типовые инциденты

### Service down

1. `docker compose ps` / `kubectl get pods`.
2. Проверить `up{job="helpTree-app"}` и последние строки лога.
3. Если процесс жив, но scrape не работает — проверить
   `/actuator/prometheus` и сетевое правило.
4. Если приложение падает на старте — искать `Production configuration is
   invalid` и исправить переменные.

### Database unavailable

1. `helptree_database_up == 0` и ошибки `SELECT 1` в логе.
2. Проверить PostgreSQL, пул (`DB_POOL_MAX`), `max_connections`, диск.
3. Помнить: при недоступной БД не работают Flyway, outbox, логин и запись.
4. После восстановления дождаться `helptree_database_up=1` и проверить
   `helptree_outbox_backlog`.

### Server errors

1. Смотреть `errorType=INTERNAL_SERVER_ERROR` в структурированном логе.
2. Найти `uri` с максимальной долей 5xx в
   `helptree_api_server_errors_total`.
3. Сопоставить с последним деплоем и миграцией. Не откатывать миграции
   вручную: Flyway `clean-disabled`, а схема аддитивная.
4. Исправить root cause; перезапуск без исправления только скрывает симптом.

### High latency

1. p95 по `helptree_api_duration_seconds_bucket`; найти тяжёлый `uri`.
2. Проверить `DB_QUERY_TIMEOUT_*`, медленные запросы, пул соединений.
3. Проверить GC/память и размер ответов (`helptree_api_response_size_bytes`).
4. Не поднимать таймауты вслепую: сначала найти запрос.

### Authorization errors

1. Рост 401/403 может быть истёкшим токеном у старых клиентов.
2. Проверить `reason` в логах и `helptree_auth_login_failures_total`.
3. При признаках перебора — проверить rate limit и login lockout, при
   необходимости временно ужесточить `LOGIN_MAX_ATTEMPTS`.

### Login failures

1. Разбить по `reason`: `bad_password`, `unknown_account`, `locked`.
2. Массовый `unknown_account` — попытка перебора или сломанный клиент.
3. Массовый `locked` — сработала защита; не отключать её без причины.

### Report spike

1. Проверить `helptree_reports_created_total` и последние жалобы.
2. Отличить координированный report burst от реальной волны нарушений.
3. При необходимости временно ограничить приём жалоб флагом
   `FEATURE_REPORTS_ENABLED=false` (осознанно: это 503 для пользователей).

### Moderation backlog

1. `helptree_moderation_queue`, `helptree_reports_sla_breaches`.
2. Распределить жалобы между модераторами, начать с самых старых.
3. Убедиться, что scheduled expiry и SLA-задачи не падают.

### Moderation SLA

1. Открыть жалобы старше `REPORTS_SLA_HOURS`.
2. Проверить, не завис ли экспорт/очередь уведомлений модераторам.
3. После разбора убедиться, что `helptree_reports_sla_breaches` вернулся к 0.

### Outbox backlog

1. Проверить `helptree_outbox_backlog` и
   `helptree_outbox_oldest_age_seconds`.
2. Убедиться, что `NOTIFICATIONS_ENABLED=true` и dispatcher не отключён.
3. Проверить `helptree_outbox_failed_total`, ошибки sender'ов, Telegram/SMTP.
4. Не удалять строки outbox вручную: это нарушит идемпотентность и аудит.
   Исправьте причину, затем дождитесь retry/processing-timeout.

### Outbox failures

1. Найти sender и канал по логам (email/telegram/in-app).
2. Если провайдер недоступен — отключить канал в настройках уведомлений,
   а не увеличивать retry бесконечно.
3. Проверить, что `NOTIFICATIONS_MAX_ATTEMPTS` и
   `NOTIFICATIONS_PROCESSING_TIMEOUT_MINUTES` не заданы нулевыми.

### Image uploads

1. Проверить долю `outcome="failure"` по `purpose`.
2. Проверить Silo: доступность, свободное место, credentials, bucket policy.
3. Сверить `SILO_ENDPOINT`/`SILO_PUBLIC_ENDPOINT` и `SILO_ACCESS_KEY` с
   контейнером Silo.
4. Помнить: битые/поддельные изображения отклоняются намеренно; рост
   `failure` после атаки — ожидаемый.

### WebSocket churn

1. Рост `helptree_websocket_disconnections_total` и/или
   `helptree_websocket_sessions`.
2. Проверить idle timeout прокси/балансировщика и heartbeat.
3. При включённом relay — проверить `WS_RELAY_*` и доступность брокера.
4. Массовые reconnect'ы без relay обычно означают, что клиент не получает
   heartbeat; не увеличивайте лимиты вслепую.

### Balance mismatch

1. Это critical: `helptree_wallet_balance_mismatches > 0`.
2. **Не запускать автоматический «ремонт»**: он может создать монеты.
3. Зафиксировать затронутых пользователей, проверить ledger
   (`coin_transactions`) и последние операции.
4. Разбираться как с data-integrity инцидентом; исправление — только
   аудируемой операцией с записью в ledger.

### Chat retention backlog

1. Проверить логи `ChatRetentionScheduler` и число удалённых за прогон.
2. Если батчи упираются в `CHAT_RETENTION_MAX_BATCHES_PER_RUN` несколько дней
   подряд — временно увеличить лимит или запустить прогон вручную.
3. `CHAT_RETENTION_DAYS=0` означает «хранить бессрочно»; в этом режиме удаление
   не выполняется и старые партиции `messages` не удаляются.
4. Удаление физическое: восстановить сообщение можно только из backup.

### Partition maintenance

1. `Partitions: не удалось обслужить таблицу ...` — проверить права на DDL и
   доступность PostgreSQL.
2. `Partitions: ... уже обслуживается другим экземпляром` — штатная ситуация
   при нескольких репликах; следующий прогон продолжит работу.
3. Ненулевой `SELECT count(*) FROM messages_default` означает, что запись
   попала в default-партицию. Проверьте `PARTITIONS_ENABLED=true` и cron;
   следующий успешный прогон перенесёт строки.
4. Перед изменением `PARTITIONS_RETENTION_MONTHS` помните: для `messages`
   фактический retention — максимум из настройки и `CHAT_RETENTION_DAYS`.

### Antivirus unavailable

1. `503 SERVICE_UNAVAILABLE` на загрузке при `ANTIVIRUS_ENABLED=true` — это
   fail-closed, а не баг приложения.
2. Проверить `ANTIVIRUS_HOST`/`ANTIVIRUS_PORT`, доступность ClamAV и свежесть
   сигнатур.
3. Не отключать сканер «чтобы загрузки прошли», если политика требует
   сканирования; сначала восстановите демон.
4. Health-индикатор `antivirus` показывает `DOWN`, пока демон недоступен.

### VIP reminders

1. Проверить, что `WALLET_VIP_REMINDER_DAYS` содержит ожидаемые дни.
2. Напоминание идемпотентно: повторный прогон в тот же день не создаёт дубль.
3. Если пользователь продлил VIP, `vipUntil` изменился — новый период получит
   собственные напоминания.
4. Автопродления нет: отсутствие напоминания не продлевает и не отменяет VIP.

---

## 10. Проверка Block 20–21

```powershell
# Backend: полный набор тестов (нужна доступная PostgreSQL)
$env:INTEGRATION_TEST = 'true'
.\mvnw.cmd -B -pl helpTree-service clean test

# Frontend
Set-Location frontend
npx tsc --noEmit
npm run build
```

Ключевые проверки вручную:

- старт с `SPRING_PROFILES_ACTIVE=prod` и слабым `JWT_SECRET` завершается
  ошибкой, значение секрета не попадает в лог;
- `/actuator/health` и `/actuator/info` доступны без аутентификации;
- `/actuator/prometheus` принимает только scrape Basic Auth;
- `/actuator/metrics` без ADMIN возвращает 401/403, а `/actuator/loggers` не exposed;
- отключение флага даёт `503` с `code=FEATURE_DISABLED`;
- в логе есть `traceId`, `requestId`, `userId`, `errorType`, а email/телефон/
  токен замаскированы;
- `docker compose config` не показывает паролей по умолчанию, а без
  `GRAFANA_ADMIN_PASSWORD`/`SILO_*` запуск не проходит.

Block 21:

- `POST /api/posts/{id}/views` дважды подряд возвращает одинаковый
  `viewsCount`;
- повторная заявка с тем же текстом от того же автора даёт `409 CONFLICT`;
- правка сообщения старше `CHAT_EDIT_WINDOW_MINUTES` даёт `400`, а правка
  своего сообщения в окне — `200` с `editedAt`;
- экспорт кошелька в `csv` открывается в Excel с корректной кириллицей
  (UTF-8 BOM), а `format=xml` даёт `400` до обращения к БД;
- при `ANTIVIRUS_ENABLED=true` и остановленном ClamAV загрузка даёт
  `503 SERVICE_UNAVAILABLE`, а `/actuator/health` — `antivirus: DOWN`;
- `PARTITIONS_ENABLED=true` создаёт партиции на текущий и три будущих месяца
  без ручного DDL.
