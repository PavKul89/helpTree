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

## Тестирование Security

### Эндпоинты аутентификации

| Метод | URL | Описание |
|-------|-----|---------|
| POST | `/api/auth/register` | Регистрация пользователя |
| POST | `/api/auth/login` | Вход в систему |
| GET | `/api/users/{id}/public` | Публичные данные (без авторизации) |

### Заголовки авторизации

После логина используйте токен:
```
Authorization: Bearer <token>
X-User-Id: <userId>
X-User-Role: USER | ADMIN
```

### Тестирование в Postman

**1. Регистрация пользователя**
```json
POST http://localhost:8081/api/auth/register
Content-Type: application/json

{
    "name": "Иван Петров",
    "email": "ivan@test.com",
    "password": "Password123!",
    "phone": "+79001234567",
    "city": "Москва"
}
```

**2. Вход администратора (создаётся автоматически)**
```json
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
    "email": "admin@test.com",
    "password": "123456"
}
```

**3. Просмотр профиля (только владелец или админ)**
```
GET http://localhost:8081/api/users/3
Authorization: Bearer <token>
```

**4. Все пользователи (только админ)**
```
GET http://localhost:8081/api/users
Authorization: Bearer <token>
```

### Роли и полномочия

| Роль | Доступ |
|------|--------|
| **USER** | Свой профиль, свои посты, свой рейтинг |
| **ADMIN** | Все пользователи, управление рейтингом, любые операции |

### Конфигурация админа

```yaml
# helpTree-service/src/main/resources/application.yaml
app:
  admin:
    email: admin@helptree.com
    password: Admin123!
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
| 🔄 | Централизованное логирование (ELK)  |               — |
| 📋 | Чат между пользователями            |               — |
| 📋 | Telegram уведомления                |               — |
| 📋 | Отзывы после помощи                 |               — |



---
## Устранение неполадок

```powershell
# Kafka #
docker-compose down
docker volume rm helpTree_kafka_data
docker volume ls | findstr kafka
docker volume prune -f
docker-compose up -d

# MinIO #
docker-compose up -d minio
```

---

## Загрузка изображений

### Структура API

| Метод | URL | Описание |
|-------|-----|---------|
| POST | `/api/images` | Загрузить одно изображение |
| POST | `/api/images/multiple` | Загрузить несколько изображений |
| DELETE | `/api/images?url=...` | Удалить изображение |

### Пример использования

**1. Загрузить изображение:**
```
POST http://localhost:8080/api/images
Content-Type: multipart/form-data
Key: file
Value: [выбрать файл]
```

**2. Создать пост с изображением:**
```
POST http://localhost:8080/api/posts
{
    "title": "Нужна помощь",
    "description": "Описание",
    "imageUrls": ["http://localhost:9000/helptree-images/uuid.jpg?..."]
}
```

---
## Контакты
```powershell
- **Владелец проекта**: Кулаженко Павел Михайлович
- **Email**: PavKul89@gmail.com
- **GitHub**: https://github.com/PavKul89
```