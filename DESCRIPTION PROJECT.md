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
```
---

## Технологический стек

| Категория | Технологии |
|-----------|------------|
| Язык | Java 21 |
| Фреймворки | Spring Boot 3.4.3, Spring Cloud 2024.0.0 |
| База данных | PostgreSQL 16, Flyway (миграции) |
| Брокер сообщений | Apache Kafka |
| Контейнеризация | Docker, Docker Compose |
| Утилиты | Lombok |

---

## Мониторинг и админки

| Сервис | URL | Данные для входа |
|--------|-----|------------------|
| Kafka UI | http://localhost:8082 | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin/admin |

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

## Цели и задачи проекта


---


| Статус | Задача | Дата выполнения |
|:------:|--------|----------------:|
| ✅ | Базовая архитектура микросервисов |      15.03.2026 |
| ✅ | API Gateway с маршрутизацией |      16.03.2026 |
| ✅ | Docker-инфраструктура |      17.03.2026 |
| ✅ | Kafka интеграция |      17.03.2026 |
| ✅ | Распределенная трассировка (Jaeger) |      19.03.2026 |
| 🔄 | Централизованное логирование (ELK) |               — |
| 📋 | Централизованное логирование (ELK) |               — |



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