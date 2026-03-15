# 🌳 helpTree — Сервис взаимопомощи

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-24.0-2496ed)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📋 О проекте

**helpTree** — платформа для обмена помощью между пользователями. Проект позволяет создавать посты о помощи, откликаться на них и отслеживать рейтинг участников. Построен на микросервисной архитектуре с использованием современных технологий.

### 🗺️ Микросервисы

| Сервис | Порт | Назначение | Технологии |
|--------|------|------------|------------|
| **gateway-service** | 8080 | API Gateway (единая точка входа) | Spring Cloud Gateway, Netty |
| **helpTree-service** | 8081 | Пользователи, посты, помощь | Spring Boot, JPA, Kafka Producer |
| **rating-service** | 8085 | Рейтинги и статистика | Spring Boot, Kafka Consumer |

### 🎯 Основные возможности
- ✅ Создание и управление постами о помощи
- ✅ Система откликов (ACCEPTED → COMPLETED → CONFIRMED)
- ✅ Автоматический расчет рейтинга пользователей
- ✅ История изменений рейтинга
- ✅ Топ пользователей по рейтингу
- ✅ API Gateway как единая точка входа
- ✅ Асинхронное взаимодействие через Kafka
- ✅ Мониторинг через Prometheus + Grafana

## 🚀 Технологический стек

| Категория | Технологии |
|-----------|------------|
| **Язык** | Java 21 |
| **Фреймворки** | Spring Boot 3.4.3, Spring Cloud 2024.0.0 |
| **API Gateway** | Spring Cloud Gateway, Netty |
| **База данных** | PostgreSQL 16, Flyway (миграции) |
| **Брокер сообщений** | Apache Kafka 7.4.0 |
| **Мониторинг** | Prometheus, Grafana |
| **Трассировка** | Jaeger (планируется) |
| **Логирование** | ELK Stack (планируется) |
| **Контейнеризация** | Docker, Docker Compose |
| **Утилиты** | Lombok, MapStruct |

---
📊 Мониторинг и наблюдаемость
Сервис	URL	Доступ
- Kafka UI	http://localhost:8082	Открытый доступ
- Prometheus	http://localhost:9090	Открытый доступ
- Grafana	http://localhost:3000	admin/admin
- Jaeger	http://localhost:16686	Открытый доступ
- Kibana	http://localhost:5601	Открытый доступ

---
Как запустить

Собрать и запустить (локально, требуется PostgreSQL):

```powershell
# Собрать проект
.\mvnw.cmd clean package -DskipTests

# Запустить приложение
.\mvnw.cmd spring-boot:run
```

---
### Запланированные задачи

### 🏗️ Инфраструктура и базовые сервисы

| Статус | Задача | Дата выполнения | Примечание |
|:------:|--------|-----------------|------------|
| 🟢 | Базовая архитектура микросервисов | Март 2026 | helpTree-service, rating-service |
| 🟢 | API Gateway с маршрутизацией | Март 2026 | gateway-service на порту 8080 |
| 🟢 | Логирование в Gateway (LoggingFilter) | Март 2026 | Добавлен Request ID |
| 🟢 | Docker-инфраструктура | Март 2026 | PostgreSQL, Kafka, Prometheus, Grafana |
| 🟢 | Kafka интеграция | Март 2026 | Асинхронные события |
| 🟡 | Распределенная трассировка (Jaeger) | В работе | Интеграция OpenTelemetry |
| ⚪ | Централизованное логирование (ELK) | План | Elasticsearch + Logstash + Kibana |


---
## Контакты
- Владелец проекта: (Кулаженко Павел Михайлович)
- Email: (PavKul89@gmail.com)
- GitHub: https://github.com/PavKul89

