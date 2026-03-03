# helpTree

Краткое описание

helpTree — небольшое Spring Boot приложение для обмена помощью между пользователями (посты, отклики/помощь, пользователи).

Цели этого репозитория
- Быстрая разработка REST API
- Наличие миграций Flyway
- Ясная архитектура и покрытие тестами

Как запустить

1) Собрать и запустить (локально, требуется PostgreSQL):

```powershell
# Собрать проект
.\mvnw.cmd clean package -DskipTests

# Запустить приложение
.\mvnw.cmd spring-boot:run
```

2) Запуск тестов:

```powershell
.\mvnw.cmd test
```

Конфигурация
- Файлы конфигурации: `src/main/resources/application.yaml`
- Миграции БД: `src/main/resources/db/migration` (Flyway)

Ключевые технологии
- Java 21, Spring Boot 3/4 (уточнить в pom.xml), Spring Data JPA, Flyway
- PostgreSQL
- Lombok

Дальнейшие шаги и рекомендации
- Поддерживать единый формат ошибок (RFC7807) — уже реализовано в `GlobalExceptionHandler`.
- Добавить OpenAPI (springdoc) для автодокументации.
- Внедрить MapStruct для DTO-мэппинга.
- Добавить Testcontainers для интеграционных тестов.
- План по реструктуризации пакетов — в `ARCHITECTURE.md`.

Контакты
- Владелец проекта: (Кулаженко Павел Михайлович)
- Email: (указать email)
- GitHub: (указать профиль)


