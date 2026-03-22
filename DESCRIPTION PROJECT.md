# 🌳 helpTree — Сервис взаимопомощи

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-24.0-2496ed)](https://www.docker.com/)

## О проекта

```powershell
**helpTree** — платформа для обмена помощью между пользователями.
 Проект позволяет создавать посты о помощи, откликаться на них и отслеживать рейтинг участников.
 Построен на микросервисной архитектуре с использованием современных технологий.
```
## Микросервисы

| Сервис | Порт | Назначение |
|--------|------|------------|
| **gateway-service** | 8080 | API Gateway (единая точка входа) |
| **helpTree-service** | 8081 | Пользователи, посты, помощь |
| **rating-service** | 8085 | Рейтинги и статистика |
| **notification-service** | 8087 | Telegram уведомления |

## Основные возможности
```powershell
- Создание и управление постами о помощи
- Система откликов (ACCEPTED → COMPLETED → CONFIRMED)
- Автоматический расчет рейтинга пользователей
- История изменений рейтинга
- Топ пользователей по рейтингу
- API Gateway как единая точка входа
- Асинхронное взаимодействие через Kafka
- Загрузка изображений в посты (MinIO/S3)
- Telegram уведомления о событиях помощи
```

---

## Технологический стек

| Категория | Технологии |
|-----------|------------|
| Язык | Java 21 |
| Фреймворки | Spring Boot 3.4.3, Spring Cloud 2024.0.0 |
| База данных | PostgreSQL 16, Flyway (миграции) |
| Брокер сообщений | Apache Kafka |
| Хранилище файлов | MinIO (S3-совместимое) |
| Контейнеризация | Docker, Docker Compose |
| Утилиты | Lombok |

---

## Мониторинг и админки

| Сервис | URL | Данные для входа |
|--------|-----|------------------|
| Kafka UI | http://localhost:8082 | — |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin/admin |
| Jaeger | http://localhost:16686 | — |

---

## Telegram уведомления

### Описание
Система уведомляет пользователей в Telegram о событиях связанных с помощью.

### Telegram Bot
- **Имя**: @helpTreeNotifierBot
- **Token**: `{TELEGRAM_BOT_TOKEN}` (переменная окружения)

### Команды бота
| Команда | Описание |
|---------|---------|
| `/start` | Показать Chat ID пользователя |
| `/help` | Список команд |
| `/myid` | Показать ваш Chat ID |

### Привязка Telegram к аккаунту
1. Открыть Telegram → написать `/start` боту
2. Скопировать Chat ID
3. В приложении: `PUT /api/users/{userId}/telegram` с телом `{"chatId": "ВАШ_CHAT_ID"}`

### События и уведомления

| Событие | Описание | Кому приходит |
|---------|----------|--------------|
| HELP_ACCEPTED | Кто-то принял помочь | **Автору** поста |
| HELP_COMPLETED | Помощник завершил работу | **Автору** поста |
| HELP_CONFIRMED | Автор подтвердил помощь | **Обоим** (автору и помощнику) |
| HELP_CANCELLED | Помощь отменена | **Обоим** (автору и помощнику) |



### Техническая реализация
- **Kafka Topic**: `help-events`
- **notification-service**: потребляет события из Kafka и отправляет в Telegram
- **HelpEvent DTO**: содержит postTitle, authorId, helperId, eventType

---

## Как запустить

### Сборка

```powershell
# Собрать проект
.\mvnw.cmd clean package -DskipTests

# Запустить приложение
.\mvnw.cmd spring-boot:run
```

### Docker Compose (вся инфраструктура)

```powershell
docker-compose up -d
```

---


## Вход администратора (создаётся автоматически)
```json
POST http: //localhost:8081/api/auth/login
Content-Type: application/json

{
    "email": "admin@test.com",
    "password": "123456"
}
```


### Роли и полномочия

| Роль | Доступ |
|------|--------|
| **USER** | Свой профиль, свои посты, свой рейтинг |
| **ADMIN** | Все пользователи, управление рейтингом, любые операции |

## Конфигурация админа

```yaml
# helpTree-service/src/main/resources/application.yaml
app:
  admin:
    email: admin@test.com
    password: 123456
```

---

## Цели и задачи проекта


---


| Статус | Задача                              | Дата выполнения |
|:------:|-------------------------------------|----------------:|
| ✅ | Базовая архитектура микросервисов   |      15.03.2026 |
| ✅ | API Gateway с маршрутизацией        |      16.03.2026 |
| ✅ | Docker-инфраструктура               |      17.03.2026 |
| ✅ | Kafka интеграция                    |      17.03.2026 |
| ✅ | Распределенная трассировка (Jaeger) |      19.03.2026 |
| ✅ | Security (BCrypt, JWT, роли)        |      20.03.2026 |
| ✅ | Загрузка изображений (MinIO)        |      21.03.2026 |
| ✅ | Rate limit (лимит запросов)         |      21.03.2026 |
| ✅ | Telegram уведомления                |      22.03.2026 |
| 🔄 | Чат между пользователями            |               — |
| 🔄 | Отзывы после помощи                 |               — |
| 🔄 | Пагинация Help эндпоинтов                 |               — |
| 🔄 | Убрать дублирование DTO (HelpEvent в 3 местах!)              |               — |
| 🔄 | Добавить валидацию паролей            |               — |
| 🔄 | Исправить event type mismatch           |               — |
| 🔄 | Добавить Swagger             |               — |
| 🔄 | Добавить Swagger             |               — |
---
## Устранение неполадок

```powershell
# Kafka #
docker-compose down
docker volume rm helpTree_kafka_data
docker volume ls | findstr kafka
docker volume prune -f
docker-compose up -d
```
---

## Контакты
```powershell
- **Владелец проекта**: Кулаженко Павел Михайлович
- **Email**: PavKul89@gmail.com
- **GitHub**: https://github.com/PavKul89
```