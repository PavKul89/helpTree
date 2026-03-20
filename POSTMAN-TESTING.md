# <span style="color: #FF6B35;">Тестирование проекта в Postman</span>

## <span style="color: #00D9FF;">Содержание</span>
1. [Настройка окружения](#настройка-окружения)
2. [Тестирование Security](#тестирование-security)
3. [Тестирование helpTree-service](#тестирование-helptree-service)
4. [Тестирование rating-service](#тестирование-rating-service)
5. [Тестирование API Gateway](#тестирование-api-gateway)
6. [Интеграционные тесты](#интеграционные-тесты)
7. [Негативные тесты](#негативные-тесты)
8. [Тестирование мониторинга и трассировки](#тестирование-мониторинга-и-трассировки)

---

<a id="настройка-окружения"></a>
## <span style="color: #00D9FF;">Настройка окружения</span>

### <span style="color: #FFD700;">Переменные окружения</span>

| Переменная | Значение | Описание |
|------------|-----------|---------|
| `base_url` | `http://localhost:8080` | API Gateway |
| `helpTree_url` | `http://localhost:8081` | helpTree-service |
| `rating_url` | `http://localhost:8085` | rating-service |

### <span style="color: #FFD700;">Коллекция переменных для авторизации</span>

Создайте переменную `auth_token` в окружении Postman для хранения JWT токена.

### Предварительные требования
- Все сервисы запущены
- PostgreSQL доступен на порту 5432
- Kafka доступен на порту 9093

---

<a id="тестирование-security"></a>
## <span style="color: #00D9FF;">Тестирование Security</span>

### <span style="color: #FF6B35;">1. Аутентификация</span>

#### 1.1 Вход администратора
**POST** `{{helpTree_url}}/api/auth/login`

```json
{
    "email": "admin@helptree.com",
    "password": "Admin123!"
}
```

**Ожидаемый ответ**: 200 OK
```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "email": "admin@helptree.com",
    "role": "ADMIN"
}
```

> Сохраните значение `token` в переменную `auth_token`

#### 1.2 Регистрация нового пользователя
**POST** `{{helpTree_url}}/api/auth/register`

```json
{
    "name": "Иван Петров",
    "email": "ivan@test.com",
    "password": "Password123!",
    "phone": "+79001234567",
    "city": "Москва"
}
```

**Ожидаемый ответ**: 201 Created
```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 2,
    "email": "ivan@test.com",
    "role": "USER"
}
```

#### 1.3 Вход под новым пользователем
**POST** `{{helpTree_url}}/api/auth/login`

```json
{
    "email": "ivan@test.com",
    "password": "Password123!"
}
```

> Сохраните новый токен в переменную `user_token`

---

### <span style="color: #FF6B35;">2. Публичные эндпоинты (без авторизации)</span>

#### 2.1 Публичные данные пользователя
**GET** `{{helpTree_url}}/api/users/1/public`

**Ожидаемый ответ**: 200 OK
```json
{
    "id": 1,
    "name": "Admin",
    "rating": 0.0,
    "helpedCount": 0,
    "debtCount": 0
}
```

#### 2.2 Вход с неверным паролем
**POST** `{{helpTree_url}}/api/auth/login`

```json
{
    "email": "admin@helptree.com",
    "password": "WrongPassword!"
}
```

**Ожидаемый ответ**: 401 Unauthorized

#### 2.3 Вход с несуществующим email
**POST** `{{helpTree_url}}/api/auth/login`

```json
{
    "email": "nonexistent@test.com",
    "password": "Password123!"
}
```

**Ожидаемый ответ**: 404 Not Found

---

### <span style="color: #FF6B35;">3. Защищённые эндпоинты (с авторизацией)</span>

#### 3.1 Заголовки авторизации
Для всех защищённых запросов добавьте:
```
Authorization: Bearer {{auth_token}}
```

#### 3.2 Просмотр своего профиля (USER)
**GET** `{{helpTree_url}}/api/users/2`
> Используйте `{{user_token}}`

**Ожидаемый ответ**: 200 OK

#### 3.3 Просмотр чужого профиля (USER)
**GET** `{{helpTree_url}}/api/users/1`
> Используйте `{{user_token}}`

**Ожидаемый ответ**: 403 Forbidden
```json
{
    "status": 403,
    "error": "Доступ запрещен",
    "message": "Вы можете просматривать только свой профиль"
}
```

#### 3.4 Просмотр любого профиля (ADMIN)
**GET** `{{helpTree_url}}/api/users/2`
> Используйте `{{auth_token}}` (админ)

**Ожидаемый ответ**: 200 OK

#### 3.5 Просмотр всех пользователей (ADMIN)
**GET** `{{helpTree_url}}/api/users`
> Используйте `{{auth_token}}`

**Ожидаемый ответ**: 200 OK (список всех пользователей)

#### 3.6 Просмотр всех пользователей (USER)
**GET** `{{helpTree_url}}/api/users`
> Используйте `{{user_token}}`

**Ожидаемый ответ**: 403 Forbidden

---

### <span style="color: #FF6B35;">4. Тестирование ролей</span>

#### 4.1 USER: Создание поста
**POST** `{{helpTree_url}}/api/posts`
> Используйте `{{user_token}}`

```json
{
    "title": "Нужна помощь",
    "content": "Описание задачи"
}
```

**Ожидаемый ответ**: 201 Created

#### 4.2 USER: Редактирование чужого поста
Создайте пост другим пользователем, затем:
**PUT** `{{helpTree_url}}/api/posts/{other_post_id}`
> Используйте `{{user_token}}`

**Ожидаемый ответ**: 403 Forbidden

#### 4.3 USER: Удаление своего аккаунта
**DELETE** `{{helpTree_url}}/api/users/2`
> Используйте `{{user_token}}`

**Ожидаемый ответ**: 204 No Content

#### 4.4 USER: Вход удалённого пользователя
**POST** `{{helpTree_url}}/api/auth/login`

```json
{
    "email": "ivan@test.com",
    "password": "Password123!"
}
```

**Ожидаемый ответ**: 403 Forbidden
```json
{
    "status": 403
}
```

---

### <span style="color: #FF6B35;">5. Запросы без токена</span>

#### 5.1 Доступ к защищённому эндпоинту без токена
**GET** `{{helpTree_url}}/api/users`

**Ожидаемый ответ**: 401 Unauthorized или 403 Forbidden

#### 5.2 Доступ с невалидным токеном
Добавьте заголовок:
```
Authorization: Bearer invalid_token_here
```

**GET** `{{helpTree_url}}/api/users/1`

**Ожидаемый ответ**: 401 Unauthorized

---

### <span style="color: #FF6B35;">6. Полный сценарий</span>

```
1. POST /api/auth/login (админ) → сохранить token в {{auth_token}}
2. POST /api/auth/register (новый юзер) → сохранить в {{user_token}}
3. GET /api/users/2 (юзер смотрит себя) → 200
4. GET /api/users/1 (юзер смотрит админа) → 403
5. GET /api/users/1 (админ смотрит юзера) → 200
6. DELETE /api/users/2 (юзер удаляет себя) → 204
7. POST /api/auth/login (удалённый юзер) → 403
```

---

<a id="тестирование-helptree-service"></a>
## <span style="color: #00D9FF;">Тестирование helpTree-service</span>

### <span style="color: #FF6B35;">7. Пользователи (Users)</span>

> Все запросы требуют заголовок `Authorization: Bearer {{auth_token}}`

#### 7.1 Создание пользователя (ADMIN)
**POST** `{{helpTree_url}}/api/users`

```json
{
    "name": "Тест Тестов",
    "email": "test@example.com",
    "password": "Password123!",
    "phone": "+79001234567",
    "city": "Москва"
}
```

**Ожидаемый ответ**: 201 Created

#### 7.2 Получение всех пользователей (ADMIN)
**GET** `{{helpTree_url}}/api/users`

**Ожидаемый ответ**: 200 OK

#### 7.3 Получение пользователя по ID
**GET** `{{helpTree_url}}/api/users/{id}`

#### 7.4 Обновление пользователя
**PUT** `{{helpTree_url}}/api/users/{id}`

```json
{
    "name": "Новое Имя",
    "phone": "+79009876543"
}
```

#### 7.5 Обновление рейтинга (ADMIN)
**PUT** `{{helpTree_url}}/api/users/{id}/rating?rating=4.5`

#### 7.6 Восстановление пользователя (ADMIN)
**POST** `{{helpTree_url}}/api/users/{id}/restore`

#### 7.7 Увеличение счётчика помощи (ADMIN)
**POST** `{{helpTree_url}}/api/users/{id}/increment-help`

#### 7.8 Зафиксировать помощь (ADMIN)
**POST** `{{helpTree_url}}/api/users/help/{helperId}/to/{receiverId}`

---

### <span style="color: #FF6B35;">8. Посты (Posts)</span>

> Все запросы требуют заголовок `Authorization: Bearer {{auth_token}}`

#### 8.1 Создание поста
**POST** `{{helpTree_url}}/api/posts`

```json
{
    "title": "Нужна помощь с переездом",
    "content": "Необходима помощь с перевозкой мебели"
}
```

#### 8.2 Получение всех постов
**GET** `{{helpTree_url}}/api/posts?page=0&size=10&sort=createdAt,desc`

#### 8.3 Фильтрация постов
```
GET {{helpTree_url}}/api/posts?status=OPEN
GET {{helpTree_url}}/api/posts?userId=1
GET {{helpTree_url}}/api/posts?title=переезд
```

#### 8.4 Обновление поста
**PUT** `{{helpTree_url}}/api/posts/{id}`

#### 8.5 Удаление поста
**DELETE** `{{helpTree_url}}/api/posts/{id}`

---

### <span style="color: #FF6B35;">9. Помощь (Helps)</span>

> Все запросы требуют заголовок `Authorization: Bearer {{auth_token}}`

#### 9.1 Откликнуться на пост
**POST** `{{helpTree_url}}/api/helps/accept`

```json
{
    "helperId": 1,
    "postId": 1
}
```

#### 9.2 Завершить помощь
**POST** `{{helpTree_url}}/api/helps/{id}/complete`

#### 9.3 Подтвердить помощь
**POST** `{{helpTree_url}}/api/helps/{id}/confirm`

#### 9.4 Отменить помощь
**POST** `{{helpTree_url}}/api/helps/{id}/cancel`

#### 9.5 Просмотр помощи (только участники)
**GET** `{{helpTree_url}}/api/helps/helper/{helperId}`
**GET** `{{helpTree_url}}/api/helps/receiver/{receiverId}`

---

<a id="тестирование-rating-service"></a>
## <span style="color: #00D9FF;">Тестирование rating-service</span>

> Все эндпоинты публичные (rating открыт для всех)

#### 10.1 Получение рейтинга пользователя
**GET** `{{rating_url}}/api/ratings/user/{userId}`

**Ожидаемый ответ**: 200 OK
```json
{
    "userId": 1,
    "userName": "Admin",
    "overallRating": 3.5,
    "level": "AVERAGE",
    "totalHelps": 5,
    "totalReceivedHelps": 3,
    "successRate": 85.0
}
```

#### 10.2 Топ пользователей
**GET** `{{rating_url}}/api/ratings/top?page=0&size=10&sort=currentRating,desc`

#### 10.3 Пересчёт рейтинга
**POST** `{{rating_url}}/api/ratings/user/{userId}/recalculate`

#### 10.4 История рейтинга
**GET** `{{rating_url}}/api/ratings/user/{userId}/history?page=0&size=20`

---

<a id="тестирование-api-gateway"></a>
## <span style="color: #00D9FF;">Тестирование API Gateway</span>

### <span style="color: #FF6B35;">11. Маршрутизация</span>

#### 11.1 Health check
**GET** `{{base_url}}/actuator/health`

#### 11.2 Список маршрутов
**GET** `{{base_url}}/actuator/gateway/routes`

#### 11.3 Запрос через Gateway с авторизацией
**POST** `{{base_url}}/api/users`
```
Authorization: Bearer {{auth_token}}
```

---

<a id="интеграционные-тесты"></a>
## <span style="color: #00D9FF;">Интеграционные тесты</span>

### <span style="color: #FF6B35;">12. Полный сценарий помощи</span>

```
1. POST /api/auth/login (админ) → {{auth_token}}
2. POST /api/auth/register (помощник) → {{helper_token}}
3. POST /api/auth/register (получатель) → {{receiver_token}}

4. POST /api/posts ({{helper_token}})
   {"title": "Нужна помощь", "content": "Описание"}

5. POST /api/helps/accept ({{helper_token}})
   {"helperId": X, "postId": Y}

6. POST /api/helps/{id}/complete ({{helper_token}})

7. POST /api/helps/{id}/confirm ({{receiver_token}})

8. GET /api/ratings/user/{helperId}} → проверка роста рейтинга
```

---

<a id="негативные-тесты"></a>
## <span style="color: #00D9FF;">Негативные тесты</span>

### <span style="color: #FF6B35;">13. Security</span>

| Тест | Запрос | Ожидаемый ответ |
|------|--------|-----------------|
| Неверный пароль | POST /api/auth/login (wrong pass) | 401 |
| Несуществующий email | POST /api/auth/login | 404 |
| Без токена | GET /api/users | 401/403 |
| Невалидный токен | GET /api/users (invalid Bearer) | 401 |
| USER смотрит всех | GET /api/users | 403 |
| USER удаляет другого | DELETE /api/users/{other}} | 403 |
| Удалённый логинится | POST /api/auth/login (deleted user) | 403 |

### <span style="color: #FF6B35;">14. Users</span>

| Тест | Запрос | Ожидаемый ответ |
|------|--------|-----------------|
| Дублирующийся email | POST /api/auth/register (существующий) | 409 |
| Неверный email | POST /api/auth/register | 400 |
| Несуществующий пользователь | GET /api/users/999999 | 404 |

### <span style="color: #FF6B35;">15. Posts & Helps</span>

| Тест | Запрос | Ожидаемый ответ |
|------|--------|-----------------|
| Несуществующий пост | GET /api/posts/999999 | 404 |
| Без обязательных полей | POST /api/posts | 400 |
| Помощь с неверным ID | POST /api/helps/accept | 404 |

---

<a id="тестирование-мониторинга-и-трассировки"></a>
## <span style="color: #00D9FF;">Тестирование мониторинга</span>

### <span style="color: #FF6B35;">16. Health & Metrics</span>

```
GET {{helpTree_url}}/actuator/health
GET {{helpTree_url}}/actuator/metrics
GET {{rating_url}}/actuator/health
GET {{base_url}}/actuator/gateway/routes
```

### <span style="color: #FF6B35;">17. Мониторинг</span>

| Сервис | URL |
|--------|-----|
| Kafka UI | http://localhost:8082 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

---

## <span style="color: #FFD700;">Быстрый чеклист</span>

```
□  Админ логинится
□  USER регистрируется
□  USER видит свой профиль (200)
□  USER НЕ видит чужой профиль (403)
□  ADMIN видит любой профиль (200)
□  ADMIN видит всех пользователей (200)
□  USER НЕ видит всех (403)
□  Неверный пароль → 401
□  Удалённый пользователь → 403
□  Без токена → 401
□  Рейтинг публичный (без токена) → 200
```
