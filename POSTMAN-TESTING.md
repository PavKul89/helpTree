# <span style="color: #FF6B35;">Тестирование проекта в Postman</span>

## <span style="color: #00D9FF;">Содержание</span>
1. [Настройка окружения](#настройка-окружения)
2. [Тестирование helpTree-service](#тестирование-helptree-service)
3. [Тестирование rating-service](#тестирование-rating-service)
4. [Тестирование API Gateway](#тестирование-api-gateway)
5. [Интеграционные тесты](#интеграционные-тесты)
6. [Негативные тесты](#негативные-тесты)
7. [Тестирование мониторинга и трассировки](#тестирование-мониторинга-и-трассировки)
8. [Дополнительные тесты](#дополнительные-тесты)
9. [Чек-лист тестирования](#чек-лист-тестирования)

---

<a id="настройка-окружения"></a>
## <span style="color: #00D9FF;">Настройка окружения</span>

### <span style="color: #FFD700;">Переменные окружения</span>

| Переменная | Значение | Описание |
|------------|-----------|-----------|
| `base_url` | `http://localhost:8080` | API Gateway |
| `helpTree_url` | `http://localhost:8081` | helpTree-service |
| `rating_url` | `http://localhost:8085` | rating-service |

### Предварительные требования
- Все сервисы запущены через docker-compose
- PostgreSQL доступен на порту 5432
- Kafka доступен на порту 9093
- Gateway доступен на порту 8080

---

<a id="тестирование-helptree-service"></a>
## <span style="color: #00D9FF;">Тестирование helpTree-service</span>

### <span style="color: #FF6B35;">1. Пользователи (Users)</span>

#### 1.1 Создание пользователя
**POST** `{{helpTree_url}}/api/users`

```json
{
    "name": "Иван Петров",
    "email": "ivan.petrov@example.com",
    "password": "password123",
    "phone": "+79001234567",
    "city": "Москва"
}
```

**Ожидаемый ответ**: 201 Created
```json
{
    "id": 1,
    "name": "Иван Петров",
    "email": "ivan.petrov@example.com",
    "phone": "+79001234567",
    "city": "Москва",
    "helpedCount": 0,
    "debtCount": 0,
    "rating": 0.0,
    "role": "USER",
    "status": "ACTIVE"
}
```

#### 1.2 Получение всех пользователей
**GET** `{{helpTree_url}}/api/users`

**Ожидаемый ответ**: 200 OK
```json
[
    {
        "id": 1,
        "name": "Иван Петров",
        "email": "ivan.petrov@example.com",
        ...
    }
]
```

#### 1.3 Получение пользователя по ID
**GET** `{{helpTree_url}}/api/users/1`

**Ожидаемый ответ**: 200 OK

#### 1.4 Получение пользователя по Email
**GET** `{{helpTree_url}}/api/users/email/ivan.petrov@example.com`

**Ожидаемый ответ**: 200 OK

#### 1.5 Обновление пользователя
**PUT** `{{helpTree_url}}/api/users/1`

```json
{
    "name": "Иван Иванов",
    "phone": "+79009876543",
    "city": "Санкт-Петербург"
}
```

**Ожидаемый ответ**: 200 OK

#### 1.6 Обновление рейтинга пользователя
**PUT** `{{helpTree_url}}/api/users/1/rating?rating=4.5`

**Ожидаемый ответ**: 200 OK

#### 1.7 Удаление пользователя (soft delete)
**DELETE** `{{helpTree_url}}/api/users/1`

**Ожидаемый ответ**: 204 No Content

#### 1.8 Восстановление пользователя
**POST** `{{helpTree_url}}/api/users/1/restore`

**Ожидаемый ответ**: 204 No Content

#### 1.9 Увеличение счетчика помощи
**POST** `{{helpTree_url}}/api/users/1/increment-help`

**Ожидаемый ответ**: 200 OK

#### 1.10 Зафиксировать помощь от одного пользователя другому
**POST** `{{helpTree_url}}/api/users/help/1/to/2`

**Ожидаемый ответ**: 200 OK
```json
"Помощь зафиксирована"
```

#### 1.11 Увеличить долг пользователя
**POST** `{{helpTree_url}}/api/users/increment-debt/2`

**Ожидаемый ответ**: 200 OK
```json
"Долг увеличен"
```

#### 1.12 Отметить, что пользователь помог
**POST** `{{helpTree_url}}/api/users/helped/1`

**Ожидаемый ответ**: 200 OK
```json
"Помощь оказана"
```

---

### <span style="color: #FF6B35;">2. Посты (Posts)</span>

#### 2.1 Создание поста
**POST** `{{helpTree_url}}/api/posts`

```json
{
    "title": "Нужна помощь с переездом",
    "content": "Необходима помощь с перевозкой мебели",
    "authorId": 1
}
```

**Ожидаемый ответ**: 201 Created
```json
{
    "id": 1,
    "title": "Нужна помощь с переездом",
    "content": "Необходима помощь с перевозкой мебели",
    "authorId": 1,
    "authorName": "Иван Петров",
    "status": "OPEN",
    "createdAt": "2026-03-19T10:00:00"
}
```

#### 2.2 Получение всех постов (с пагинацией)
**GET** `{{helpTree_url}}/api/posts?page=0&size=10&sort=createdAt,desc`

**Ожидаемый ответ**: 200 OK

#### 2.3 Фильтрация постов по статусу
**GET** `{{helpTree_url}}/api/posts?status=OPEN`

#### 2.4 Фильтрация постов по автору
**GET** `{{helpTree_url}}/api/posts?userId=1`

#### 2.5 Поиск постов по названию
**GET** `{{helpTree_url}}/api/posts?title=переезд`

#### 2.6 Получение поста по ID
**GET** `{{helpTree_url}}/api/posts/1`

**Ожидаемый ответ**: 200 OK

#### 2.7 Обновление поста
**PUT** `{{helpTree_url}}/api/posts/1`

```json
{
    "title": "Нужна помощь",
    "content": "Обновленное описание"
}
```

**Ожидаемый ответ**: 200 OK

#### 2.8 Удаление поста
**DELETE** `{{helpTree_url}}/api/posts/1`

**Ожидаемый ответ**: 204 No Content

#### 2.9 Восстановление поста
**POST** `{{helpTree_url}}/api/posts/1/restore`

**Ожидаемый ответ**: 204 No Content

#### 2.10 Получение постов пользователя
**GET** `{{helpTree_url}}/api/posts/user/1`

**Ожидаемый ответ**: 200 OK

---

### <span style="color: #FF6B35;">3. Помощь (Helps)</span>

#### 3.1 Откликнуться на пост (принять помощь)
**POST** `{{helpTree_url}}/api/helps/accept`

```json
{
    "helperId": 2,
    "postId": 1
}
```

**Ожидаемый ответ**: 201 Created
```json
{
    "id": 1,
    "helperId": 2,
    "receiverId": 1,
    "postId": 1,
    "status": "ACCEPTED",
    "createdAt": "2026-03-19T10:00:00"
}
```

#### 3.2 Завершить помощь (помощник)
**POST** `{{helpTree_url}}/api/helps/1/complete`

**Ожидаемый ответ**: 200 OK
```json
{
    "id": 1,
    "status": "COMPLETED"
}
```

#### 3.3 Подтвердить получение помощи (получатель)
**POST** `{{helpTree_url}}/api/helps/1/confirm`

**Ожидаемый ответ**: 200 OK
```json
{
    "id": 1,
    "status": "CONFIRMED"
}
```

#### 3.4 Отменить помощь
**POST** `{{helpTree_url}}/api/helps/1/cancel`

**Ожидаемый ответ**: 200 OK

#### 3.5 Получить все помощи, где пользователь помогал
**GET** `{{helpTree_url}}/api/helps/helper/2`

**Ожидаемый ответ**: 200 OK

#### 3.6 Получить все помощи, где пользователю помогали
**GET** `{{helpTree_url}}/api/helps/receiver/1`

**Ожидаемый ответ**: 200 OK

---

<a id="тестирование-rating-service"></a>
## <span style="color: #00D9FF;">Тестирование rating-service</span>

### <span style="color: #FF6B35;">4. Рейтинги</span>

#### 4.1 Получение рейтинга пользователя
**GET** `{{rating_url}}/api/ratings/user/1`

**Ожидаемый ответ**: 200 OK
```json
{
    "userId": 1,
    "currentRating": 4.5,
    "totalHelps": 10,
    "lastCalculated": "2026-03-19T10:00:00"
}
```

#### 4.2 Получение топ пользователей по рейтингу
**GET** `{{rating_url}}/api/ratings/top?page=0&size=10&sort=currentRating,desc`

**Ожидаемый ответ**: 200 OK
```json
{
    "content": [
        {
            "userId": 1,
            "currentRating": 5.0,
            "totalHelps": 20
        }
    ],
    "totalElements": 1
}
```

#### 4.3 Принудительный пересчет рейтинга
**POST** `{{rating_url}}/api/ratings/user/1/recalculate`

**Ожидаемый ответ**: 200 OK

#### 4.4 Получение истории изменения рейтинга
**GET** `{{rating_url}}/api/ratings/user/1/history?page=0&size=20&sort=calculatedAt,desc`

**Ожидаемый ответ**: 200 OK
```json
{
    "content": [
        {
            "id": 1,
            "userId": 1,
            "previousRating": 4.0,
            "newRating": 4.5,
            "change": 0.5,
            "calculatedAt": "2026-03-19T10:00:00"
        }
    ]
}
```

---

<a id="тестирование-api-gateway"></a>
## <span style="color: #00D9FF;">Тестирование API Gateway</span>

### <span style="color: #FF6B35;">5. Маршрутизация через Gateway</span>

#### 5.1 Проверка доступности Gateway
**GET** `{{base_url}}/actuator/health`

**Ожидаемый ответ**: 200 OK
```json
{
    "status": "UP"
}
```

#### 5.2 Просмотр маршрутов Gateway
**GET** `{{base_url}}/actuator/gateway/routes`

**Ожидаемый ответ**: 200 OK - список всех маршрутов

#### 5.3 Тестирование маршрута пользователей через Gateway
**POST** `{{base_url}}/api/users`

```json
{
    "name": "Тестовый пользователь",
    "email": "testgateway@example.com",
    "password": "test123",
    "phone": "+79001112233",
    "city": "Казань"
}
```

**Ожидаемый ответ**: 201 Created

#### 5.4 Тестирование маршрута рейтингов через Gateway
**GET** `{{base_url}}/api/ratings/user/1`

**Ожидаемый ответ**: 200 OK

#### 5.5 Тестирование маршрута постов через Gateway
**GET** `{{base_url}}/api/posts`

**Ожидаемый ответ**: 200 OK

---

<a id="интеграционные-тесты"></a>
## <span style="color: #00D9FF;">Интеграционные тесты</span>

### <span style="color: #FF6B35;">6. Полный сценарий помощи</span>

#### 6.1 Создание двух пользователей
**POST** `{{helpTree_url}}/api/users`
```json
{
    "name": "Помощник",
    "email": "helper@example.com",
    "password": "password123",
    "phone": "+79001112233",
    "city": "Москва"
}
```

**POST** `{{helpTree_url}}/api/users`
```json
{
    "name": "Получатель",
    "email": "receiver@example.com",
    "password": "password123",
    "phone": "+79004445566",
    "city": "Москва"
}
```

#### 6.2 Создание поста получателем
**POST** `{{helpTree_url}}/api/posts`
```json
{
    "title": "Нужна помощь с ремонтом",
    "content": "Требуется помощь в покраске стен",
    "authorId": 2
}
```

#### 6.3 Помощник откликается на пост
**POST** `{{helpTree_url}}/api/helps/accept`
```json
{
    "helperId": 1,
    "postId": 1
}
```

#### 6.4 Помощник завершает работу
**POST** `{{helpTree_url}}/api/helps/1/complete`

#### 6.5 Получатель подтверждает выполнение
**POST** `{{helpTree_url}}/api/helps/1/confirm`

#### 6.6 Проверка увеличения счетчиков
**GET** `{{helpTree_url}}/api/users/1`

Ожидается: helpedCount увеличился на 1

**GET** `{{helpTree_url}}/api/users/2`

Ожидается: debtCount увеличился на 1

#### 6.7 Проверка рейтинга
**GET** `{{rating_url}}/api/ratings/user/1`

Ожидается: rating обновлен

---

### <span style="color: #FF6B35;">7. Тестирование Kafka событий</span>

#### 7.1 Проверка топика help-events
После выполнения сценария помощи (раздел 6), проверить в Kafka UI (http://localhost:8082):
- Появление сообщения в топике help-events
- Корректность данных в сообщении

#### 7.2 Проверка обновления рейтинга
После подтверждения помощи проверить:
**GET** `{{rating_url}}/api/ratings/user/1/history`

Ожидается: новая запись в истории рейтинга

---

<a id="негативные-тесты"></a>
## <span style="color: #00D9FF;">Негативные тесты</span>

### <span style="color: #FF6B35;">8. Ошибки в Users</span>

#### 8.1 Создание пользователя с дублирующимся email
**POST** `{{helpTree_url}}/api/users`
```json
{
    "name": "Дубликат",
    "email": "ivan.petrov@example.com",
    "password": "password123"
}
```

**Ожидаемый ответ**: 409 Conflict

#### 8.2 Создание пользователя с некорректным email
**POST** `{{helpTree_url}}/api/users`
```json
{
    "name": "Тест",
    "email": "invalid-email",
    "password": "password123"
}
```

**Ожидаемый ответ**: 400 Bad Request

#### 8.3 Получение несуществующего пользователя
**GET** `{{helpTree_url}}/api/users/999999`

**Ожидаемый ответ**: 404 Not Found

#### 8.4 Обновление несуществующего пользователя
**PUT** `{{helpTree_url}}/api/users/999999`

**Ожидаемый ответ**: 404 Not Found

#### 8.5 Удаление несуществующего пользователя
**DELETE** `{{helpTree_url}}/api/users/999999`

**Ожидаемый ответ**: 404 Not Found

---

### <span style="color: #FF6B35;">9. Ошибки в Posts</span>

#### 9.1 Создание поста без обязательных полей
**POST** `{{helpTree_url}}/api/posts`
```json
{
    "title": "Тест"
}
```

**Ожидаемый ответ**: 400 Bad Request

#### 9.2 Получение несуществующего поста
**GET** `{{helpTree_url}}/api/posts/999999`

**Ожидаемый ответ**: 404 Not Found

#### 9.3 Обновление поста с неверным ID
**PUT** `{{helpTree_url}}/api/posts/999999`

**Ожидаемый ответ**: 404 Not Found

---

### <span style="color: #FF6B35;">10. Ошибки в Helps</span>

#### 10.1 Принятие помощи с неверным helperId
**POST** `{{helpTree_url}}/api/helps/accept`
```json
{
    "helperId": 999999,
    "postId": 1
}
```

**Ожидаемый ответ**: 404 Not Found

#### 10.2 Подтверждение несуществующей помощи
**POST** `{{helpTree_url}}/api/helps/999999/confirm`

**Ожидаемый ответ**: 404 Not Found

#### 10.3 Завершение уже завершенной помощи
Дважды вызвать `POST` `/api/helps/1/complete`

**Ожидаемый ответ**: 400 Bad Request или корректная обработка

---

### <span style="color: #FF6B35;">11. Ошибки в Rating</span>

#### 11.1 Получение рейтинга несуществующего пользователя
**GET** `{{rating_url}}/api/ratings/user/999999`

**Ожидаемый ответ**: 404 Not Found или создание с дефолтными значениями

#### 11.2 Пересчет рейтинга несуществующего пользователя
**POST** `{{rating_url}}/api/ratings/user/999999/recalculate`

**Ожидаемый ответ**: 404 Not Found

---

<a id="тестирование-мониторинга-и-трассировки"></a>
## <span style="color: #00D9FF;">Тестирование мониторинга и трассировки</span>

### <span style="color: #FF6B35;">12. Health Checks</span>

#### 12.1 HelpTree-service health
**GET** `{{helpTree_url}}/actuator/health`

**Ожидаемый ответ**: 200 OK
```json
{
    "status": "UP",
    "components": {
        "db": { "status": "UP" },
        "kafka": { "status": "UP" }
    }
}
```

#### 12.2 Rating-service health
**GET** `{{rating_url}}/actuator/health`

**Ожидаемый ответ**: 200 OK

---

### <span style="color: #FF6B35;">13. Metrics</span>

#### 13.1 Метрики HelpTree-service
**GET** `{{helpTree_url}}/actuator/metrics`

**Ожидаемый ответ**: 200 OK - список доступных метрик

#### 13.2 Метрики HTTP запросов
**GET** `{{helpTree_url}}/actuator/metrics/http.server.requests`

**Ожидаемый ответ**: 200 OK

#### 13.3 Метрики Rating-service
**GET** `{{rating_url}}/actuator/metrics`

#### 13.4 Custom метрики рейтинга
**GET** `{{rating_url}}/actuator/metrics/rating.controller`

---

### <span style="color: #FF6B35;">14. Трассировка (Jaeger)</span>

#### 14.1 Доступ к Jaeger UI
Открыть в браузере: http://localhost:16686

#### 14.2 Проверка trace после API вызова
1. Выполнить любой API вызов (например, GET `/api/users`)
2. Открыть Jaeger UI
3. Найти trace по сервису "helpTree"
4. Проверить наличие span с информацией о запросе

#### 14.3 Trace test endpoint
**GET** `{{helpTree_url}}/trace-test`

**Ожидаемый ответ**: 200 OK
Проверить появление trace в Jaeger

---

### <span style="color: #FF6B35;">15. Prometheus</span>

#### 15.1 Доступ к Prometheus
Открыть в браузере: http://localhost:9090

#### 15.2 Проверка метрик
В Prometheus UI выполнить запрос:
```
helpTree_http_server_requests_seconds_count
```

---

<a id="дополнительные-тесты"></a>
## <span style="color: #00D9FF;">Дополнительные тесты</span>

### <span style="color: #FF6B35;">16. Пагинация и сортировка</span>

#### 16.1 Пагинация постов
**GET** `{{helpTree_url}}/api/posts?page=0&size=5&sort=createdAt,desc`

#### 16.2 Сортировка постов по названию
**GET** `{{helpTree_url}}/api/posts?sort=title,asc`

#### 16.3 Пагинация рейтинга
**GET** `{{rating_url}}/api/ratings/top?page=0&size=5`

---

### <span style="color: #FF6B35;">17. Circuit Breaker</span>

#### 17.1 Тестирование отказоустойчивости
1. Остановить helpTree-service
2. Вызвать любой endpoint через Gateway
3. Проверить ответ circuit breaker (обычно 503 Service Unavailable)
4. Запустить helpTree-service
5. Проверить восстановление

---
