# Политика приватности публичных данных (Block 13–14)

Документ фиксирует единые правила: какие данные может увидеть каждый тип
зрителя. Единственный источник решений в коде — сервис
`org.example.helptreeservice.service.PrivacyPolicy`. Контроллеры и сервисы не
должны принимать решения о приватности «на месте»: они запрашивают аудиторию
или конкретный `canView*`-метод у политики.

## 1. Роли и аудитории

В системе существуют только две сохраняемые роли: `USER` и `ADMIN`.
Отдельной роли `MODERATOR` нет: модерация — это административная
возможность. Если такая роль появится, она добавляется в `PrivacyPolicy`, а не
в отдельные контроллеры.

| Аудитория | Кто это | Что получает |
| --- | --- | --- |
| `PUBLIC` | Анонимный посетитель или другой авторизованный пользователь | Только публичную проекцию |
| `OWNER` | Пользователь, которому принадлежат данные | Полную проекцию своих данных |
| `PARTICIPANT` | Участник конкретного акта помощи (помощник или получатель) | Полные данные этого акта помощи |
| `ADMIN` | Администратор (`role = ADMIN`) | Полную проекцию и модераторские поля |

`PrivacyPolicy.audienceFor(ownerUserId)` определяет аудиторию для данных
пользователя, `audienceForHelp(helperId, receiverId)` — для конкретного акта
помощи. Проверки выполняются на backend; frontend может скрывать кнопки, но
не является источником правды.

## 2. Публичные endpoint-ы

Публичность пути определяется `JwtAuthFilter.isPublicPath` и дублируется в
`SecurityConfig`. Всё, что не перечислено ниже, требует действующего access
token.

| Метод и путь | Публичная проекция | Отличия для владельца/администратора |
| --- | --- | --- |
| `GET /api/posts` | `PublicPostDto` | Администратор видит и `CANCELLED` |
| `GET /api/posts/{id}` | `PublicPostDto` | Автор и администратор получают полный `PostDto` |
| `GET /api/posts/user/{userId}` | `PublicPostDto` | Владелец/администратор видят и `CANCELLED` |
| `GET /api/posts/map` | `PublicPostDto` | Нет |
| `GET /api/posts/{postId}/comments` | `CommentResponse` | Нет |
| `GET /api/users/public` | `UserPublicDto` | Нет |
| `GET /api/users/{id}/public` | `UserPublicDto` | Нет |
| `GET /api/users/{id}/activities` | `ActivityDto` (публичные события) | Владелец/администратор видят приватные события |
| `GET /api/activities?userId=...` | `ActivityDto` (публичные события) | То же; без `userId` возвращается пустая страница |
| `GET /api/helps/graph` | `PublicHelpGraphDto` | Владелец графа/администратор получают `HelpGraphDto` |
| `GET /api/helps/stats` | `HelpStatsDto` | Нет |
| `GET /api/ratings/top` | `PublicRatingResponse` | Нет |
| `GET /api/ratings/user/{id}` | `PublicRatingResponse` | Нет |
| `GET /api/achievements` | Определения достижений | С `userId`: владелец/администратор получают прогресс, остальные — только полученные достижения |
| `GET /api/search` | `SearchResultDto` | Нет |
| `GET /api/stats` | `StatsDto` | Нет |
| `GET /api/reviews/**` | `ReviewResponse` | Нет |

Не являются публичными, хотя и используют похожие префиксы:

- `GET /api/posts/mine` — только текущий пользователь, полный `PostDto`;
- `GET /api/ratings/user/{id}/history` — владелец/администратор;
- `GET /api/users/{id}/achievements` — требует авторизации, отдаёт только
  полученные достижения;
- `GET /api/helps/post/{postId}` — требует авторизации, отдаёт безопасную
  проекцию без деталей отмены;
- `GET /api/helps/helper/{id}`, `GET /api/helps/receiver/{id}` — только
  участник/администратор;
- `GET /api/helps/{id}/history` — только участник/администратор.

## 3. Заявки (`PostDto` → `PublicPostDto`)

Публичная карточка содержит только:

`id, title, description, category, authorName, authorAvatarUrl, createdAt,
updatedAt, userId, authorRating, userCity, latitude, longitude, helperId,
helperName, status, imageUrls, boosted, authorNicknameColor, commentsCount`.

Правила:

1. **Email, телефон и иные контакты автора** в публичную проекцию не входят.
2. **Точные координаты** заменяются округлением до
   `PrivacyPolicy.PUBLIC_COORDINATE_DECIMALS = 2` знаков (~1 км). Полные
   координаты видят только автор и администратор.
3. **`boostedUntil`** не отдаётся публично; публично доступен только флаг
   `boosted`. Точное время окончания поднятия — владелец/администратор.
4. **`authorBlockedAt`** удалён из DTO полностью. Факт блокировки автора не
   является публичным полем; запрет отклика заблокированному автору
   обеспечивает `HelpService.acceptHelp` на backend.
5. **Внутренние признаки блокировки, удаления и модерации** (`deleted`,
   `deletedAt`, причины блокировки) не входят ни в один публичный DTO.
6. **`helperId` и `helperName`** — публичная идентичность назначенного
   помощника. Это не внутренние данные: имя помощника уже видно в карточке
   заявки и в публичном графе. Email, телефон, координаты, долг и история
   рейтинга помощника через эти поля недоступны.
7. Владелец и администратор получают полный `PostDto` только из
   `GET /api/posts/{id}` (при праве владельца), `GET /api/posts/mine`, а также
   из create/update/boost-ответов.

## 4. Активность

Публичный зритель получает только явно разрешённые типы событий:

| Тип | Публичное условие |
| --- | --- |
| `HELP_GIVEN`, `HELP_RECEIVED` | Только `CONFIRMED` помощь |
| `POST_CREATED` | Только статусы `OPEN`, `IN_PROGRESS`, `COMPLETED` |
| `ACHIEVEMENT_EARNED` | Полученное достижение |
| `USER_BLOCKED` | Никогда публично |

`relatedUserId` и `relatedUserName` заполняются только для владельца и
администратора. Если публичный зритель явно запрашивает приватный тип
(`USER_BLOCKED`), возвращается пустая страница, а не ошибка: публично такого
события просто нет. `ActivityService` дополнительно проверяет
`privacyPolicy.isPublicActivity(type)` и падает fail-closed, если SQL-предикат
когда-нибудь разойдётся с политикой.

## 5. Граф помощи

Публичные узлы (`PublicHelpGraphDto.Node`):

`id, name, avatarUrl, helpedCount, rating`.

Публичные связи (`PublicHelpGraphDto.Edge`):

`id, fromUserId, fromUserName, toUserId, toUserName, confirmedAt`.

Приватные поля графа (только владелец графа и администратор):

`debtCount`, `postId`, `postTitle`, `status`.

Правила:

1. В граф попадают только `CONFIRMED` и не удалённые помощи — тот же набор
   фактов, что используют рейтинг, достижения и статистика.
2. `userId` в запросе выбирает подграф. Если `userId` не указан, строится
   глобальный граф; для обычного зрителя он всегда публичный, для
   администратора — полный.
3. Имя, аватар, рейтинг и `helpedCount` публичны. `debtCount` (долг) и
   привязка к конкретной заявке приватны.
4. Глубина и число рёбер ограничены сервером (`app.graph.*`); запрос не может
   заставить сервис обойти неограниченный компонент.

## 6. Рейтинги и достижения

Публичный рейтинг (`PublicRatingResponse`):

`userId, userName, overallRating, level, calculatedAt, totalHelps,
totalReceivedHelps, reviewCount, averageReviewRating, successRate`.

Не отдаются публично:

- `ratingComponents` (включая `avgResponseTime` и разбивку по компонентам);
- `totalHelpRecords`, `activeHelps`, `cancelledHelps` — сырые счётчики
  записей помощи;
- история рейтинга `RatingHistory` (старое/новое значение, причина, `helpId`,
  JSON компонентов) — только владелец/администратор.

Достижения:

- `GET /api/achievements` без `userId` — только справочник достижений;
- с `userId` — владелец/администратор получают `currentProgress` и
  `targetValue`, остальные — только уже полученные достижения;
- `GET /api/users/{id}/achievements` требует авторизации и также отдаёт только
  полученные достижения;
- сам факт получения достижения публичен через `ACHIEVEMENT_EARNED` в
  активности.

## 7. Помощь

`HelpResponse` содержит данные участников и детали отмены. Правила:

1. `GET /api/helps/post/{postId}` доступен любому авторизованному
   пользователю, но возвращает безопасную проекцию: `cancelReason`,
   `cancelledById`, `cancelInitiator` всегда пустые.
2. `GET /api/helps/helper/{id}` и `GET /api/helps/receiver/{id}` доступны
   только самому помощнику/получателю и администратору; проверка идёт через
   `PrivacyPolicy.canViewHelp`.
3. `GET /api/helps/{id}/history` доступен только участникам и администратору.
4. `accept/complete/confirm/cancel` возвращают полный `HelpResponse` только
   участнику, выполнившему действие, или администратору.
5. Публичной карточке заявки достаточно `helperId`, `helperName` и статуса;
   внутренние причины отмены в неё не попадают.

## 8. Профиль пользователя

`UserPublicDto`: `id, name, rating, helpedCount, debtCount, avatarUrl, city,
nicknameColor`.

- `debtCount` остаётся публичным счётчиком репутации.
- `blockedAt`, `blockedUntil`, `email`, `phone`, `latitude`, `longitude`,
  `birthDate`, `telegramChatId`, `role`, `status`, `helpCoins`, `vipUntil`
  в публичный профиль не входят.
- Полный `UserDto` доступен только владельцу и администратору через
  `GET /api/users/{id}` и `GET /api/users/me`.
- С Block 14 публичная проекция дополнительно учитывает
  `user_privacy_settings`: `profileVisibility = PRIVATE` скрывает профиль от
  всех, кроме владельца и администратора; `PUBLIC` разрешает анонимный
  просмотр; `REGISTERED` (по умолчанию) — только авторизованным участникам.
  `showCity`, `showEmail` и `showPhone` управляют соответствующими полями,
  `searchable` исключает профиль из поиска, `allowDirectMessages` запрещает
  начинать переписку. Настройки не скрывают данные от администраторов и не
  заменяют серверные проверки прав.

## 9. Кэширование

`PublicCachePolicy` разрешает ETag/`Cache-Control` только для явно публичных
GET-проекций.

| Политика | Пути | Заголовок |
| --- | --- | --- |
| Анонимная | `/api/posts`, `/api/posts/map`, комментарии, `/api/stats`, `/api/search`, `/api/users/public`, `/api/helps/stats`, `/api/reviews/**`, `/api/ratings/top`, `/api/ratings/user/{id}`, `/api/achievements` | `public, max-age=30, stale-while-revalidate=30` |
| Зависит от роли | `/api/posts/{id}`, `/api/posts/user/{id}`, `/api/helps/graph`, `/api/users/{id}/public`, `/api/activities`, `/api/users/{id}/activities` | `private, no-cache` |

`/api/posts/mine` и история рейтинга не кэшируются вообще. Причина проста:
одинаковый URL может вернуть разные поля владельцу и постороннему, поэтому
общий кэш не должен хранить такой ответ.

## 10. Где это проверяется

- `service/PrivacyPolicy` — аудитории и все `canView*`-решения.
- `controller/PostController` — выбор `PostDto`/`PublicPostDto`, `/mine`,
  фильтр статусов.
- `service/PostService` — публичная проекция и округление координат.
- `service/ActivityService` + `repository/ActivityRepository` — публичные
  события и fail-closed проверка.
- `service/HelpService` + `controller/HelpController` — проекции графа и
  помощи.
- `controller/RatingController` + `dto/PublicRatingResponse` — публичный
  рейтинг.
- `controller/AchievementController` — прогресс только владельцу/админу.
- `security/JwtAuthFilter` — whitelist публичных путей; `/api/posts/mine` и
  история рейтинга закрыты.
- `security/PublicCachePolicy` — кэш только безопасных проекций.

## 11. Тесты

Политика покрыта целевыми тестами:

- `PrivacyPolicyTest` — аудитории и `canView*`;
- `PostServiceTest`, `PostControllerVisibilityTest` — публичная проекция
  заявок и `/mine`;
- `ActivityServiceTest` — публичные/приватные события и fail-closed;
- `HelpServiceTest`, `HelpControllerTest`, `HelpMapperTest` — граф, помощь,
  отсутствие деталей отмены в публичной проекции;
- `AchievementControllerTest` — прогресс только владельцу/админу;
- `PublicRatingResponseTest` — отсутствие внутренних компонентов рейтинга;
- `JwtAuthFilterTest` — whitelist публичных путей;
- `PublicCachePolicyTest` — запрет кэша для owner-endpoint-ов;
- `UserServiceTest` — скрытие города/телефона/онлайн-статуса и порог
  «онлайн»;
- `AccountSettingsServiceTest` — настройки приватности и экспорт;
- `AuthSessionServiceTest` — throttled `last_seen_at`.

## 12. Настройки приватности аккаунта (Block 14, V50)

Настройки хранятся в `user_privacy_settings` и доступны владельцу через
`GET/PUT /api/account/privacy`. Строка создаётся сервисом при первом чтении
или изменении; её отсутствие означает значения по умолчанию.

| Поле | По умолчанию | Смысл |
| --- | --- | --- |
| `profileVisibility` | `REGISTERED` | `PUBLIC` — виден гостям, `REGISTERED` — только участникам, `PRIVATE` — только владельцу и администратору |
| `showCity` | `TRUE` | Показывать город в публичном профиле и поиске |
| `showEmail` | `FALSE` | Показывать email (не рекомендуется) |
| `showPhone` | `FALSE` | Показывать телефон |
| `showOnlineStatus` | `TRUE` | Показывать онлайн-статус (Block 21) |
| `allowDirectMessages` | `TRUE` | Разрешать другим участникам начинать переписку |
| `searchable` | `TRUE` | Участвовать в поиске участников |

Правила:

1. Настройки применяются только к публичным проекциям. Администратор видит
   полный `UserDto` независимо от них.
2. `PRIVATE` не удаляет исторические связи: посты, помощи и отзывы остаются,
   но профиль скрывается из публичных выдачи и поиска.
3. `showEmail`/`showPhone` не отменяют базовое правило: контакты не входят в
   публичную карточку заявки или помощи. Они лишь разрешают поле в профиле,
   если соответствующая проекция его поддерживает.
4. Block 21 применяет настройки к публичной проекции явно: при
   `showCity=false` и `showPhone=false` соответствующие поля возвращаются как
   `null`, а не пустая строка или маска. Это одинаково для профиля, поиска и
   публичной ленты.
5. Онлайн-статус — производное значение: `users.last_seen_at` обновляется при
   проверке access-токена не чаще `AUTH_LAST_SEEN_TOUCH_MINUTES` (1 минута).
   Пользователь считается онлайн, если активность была в пределах
   `AUTH_ONLINE_WINDOW_MINUTES` (5 минут). При `showOnlineStatus=false`
   возвращается `online = null`; при выключенном статусе сервер не раскрывает
   и `lastSeenAt`. Администратор видит `last_seen_at` в полном `UserDto`.
6. Настройки уведомлений (`notification_preferences`, Block 12/V42) не
   являются приватностью: они определяют каналы доставки, а не видимость
   данных.

## 13. Экспорт, мягкое удаление и анонимизация

### Экспорт данных

- `GET /api/account/export` возвращает JSON владельцу: `exportedAt`, `user`,
  `privacy`, `notifications`, `posts`, `reviews`, `helps`, `transactions`,
  `notificationHistory`, `messages`.
- Block 21 расширяет экспорт до полного среза аккаунта: профиль, настройки
  приватности и уведомлений, собственные и полученные отзывы, помощи в обеих
  ролях, все транзакции кошелька, история уведомлений и отправленные
  сообщения. Это ответ на запрос доступа к данным; он не является backup-ом и
  не содержит чужих персональных данных.
- `GET /api/admin/users/{id}/export` требует `ADMIN` и обязательный `reason`;
  факт экспорта пишется в `admin_audit_log`.
- В экспорт не попадают пароли, refresh-токены, access-токены, сессии и
  служебные секреты. Файл содержит персональные данные, поэтому его нельзя
  передавать третьим лицам без основания.
- Экспорт кошелька (`/api/users/{id}/wallet/transactions/export`) — отдельный
  файл CSV/JSON, доступный владельцу и ADMIN; он ограничен
  `app.wallet.export-max-rows` и не включает данные других пользователей.

### Мягкое удаление

- `DELETE /api/admin/users/{id}?reason=…` требует `ADMIN` и причину.
- Сервер выставляет `deleted = TRUE` и `deleted_at`, отзывает все сессии и
  пишет аудит `USER_DELETED`. Строка остаётся в базе для исторических связей.
- Восстановление (`POST /api/admin/users/{id}/restore` с причиной) снимает
  soft-delete, но запрещено для анонимизированных аккаунтов.

### Анонимизация (необратима)

- Владелец: `POST /api/account/anonymize` требует текущий пароль, чтобы
  украденный access-токен не мог стереть аккаунт; причина необязательна и
  сохраняется в аудите.
- Администратор: `POST /api/admin/users/{id}/anonymize` требует причину и
  необязательное подтверждение email; операция пишется в аудит.
- Сервер заменяет имя на `Анонимизированный пользователь #<id>`, email — на
  `anonymized+<id>@anon.helptree.local`, очищает телефон, город, координаты,
  дату рождения, аватар и прочие PII, выставляет `deleted = TRUE` и
  `anonymized_at`, отзывает все сессии.
- Анонимизация выполняется сразу в одной транзакции. Контент, созданный
  пользователем (заявки, помощи, отзывы, сообщения), остаётся для истории
  связей и больше не указывает на PII; профиль исчезает из публичных выдач.
  Восстановление возможно только через backup и невозможно через API.
- Анонимизация последнего `ADMIN` запрещена, как и повторная анонимизация.
- Восстановление анонимизированного аккаунта запрещено: PII стёрты
  безвозвратно, остаётся только обезличенная запись для ссылочной целостности.

## 14. Административные операции и аудит

- Все endpoint-ы `/api/admin/**` требуют роль `ADMIN`; дополнительно каждый
  метод `AdminUserController` повторно проверяет администратора через
  `AuthorizationService` (defence in depth).
- Критические действия — удаление, восстановление, анонимизация, смена роли,
  экспорт, создание и приглашение — требуют непустую `reason`.
- Каждое действие пишется в append-only `admin_audit_log`: администратор,
  действие, сущность, причина, `beforeState`/`afterState`, IP, user-agent,
  request id и время. Запись выполняется в той же транзакции, что и операция.
- Смена роли доступна только через `PATCH /api/admin/users/{id}/role`; сервер
  запрещает снятие роли у последнего `ADMIN`. Роли остаются только `USER` и
  `ADMIN`.
- Самостоятельная анонимизация пишется в аудит с `adminId = null`, чтобы
  действие владельца не выглядело как действие администратора.
