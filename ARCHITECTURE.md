ARCHITECTURE: helpTree

Цель
- Описать целевую пакетную структуру, основные слои и план по поэтапной реорганизации проекта.

Целевая пакетная структура

com.example.helpTree
├── api
│   ├── controller   # REST controllers
│   └── dto          # request/response DTOs
├── domain
│   ├── entity       # JPA сущности
│   └── enums        # enum'ы
├── service
│   ├── interfaces   # интерфейсы сервисов (опционально)
│   └── impl         # реализации сервисов
├── repository       # Spring Data JPA репозитории
├── mapper           # MapStruct мапперы
├── config           # конфигурация (security, openapi, web)
├── exception        # GlobalExceptionHandler и кастомные исключения
├── filter           # Servlet/HTTP фильтры (TraceId и т.д.)
└── util             # вспомогательные классы и валидаторы

Почему так
- Ясное разделение ответственности (Controller -> Service -> Repository).
- DTO отделены от доменной модели — можно свободно менять API.
- MapStruct централизует маппинг и облегчает поддержку.

Переезд классов — поэтапный план
1) Подготовка
   - Создать ветку: `git checkout -b refactor/package-structure`
   - Добавить `README.md` и `ARCHITECTURE.md` (выполнено)
   - Написать smoke-тесты и убедиться, что сборка чистая: `./mvnw.cmd test`

2) Создание новых пакетов (скелетов)
   - Создать пакеты `api`, `domain`, `service`, `mapper`, `config`, `filter`, `util`.
   - Добавить package-info.java или README в каждый пакет при необходимости.

3) Перемещение контроллеров и DTO
   - Переместить `controller/*.java` -> `api/controller`
   - Переместить `dto/*` -> `api/dto`
   - После каждого шага запускать `mvn test`.

4) Переместить entity в `domain/entity`, enums -> `domain/enums`.

5) Сервисы и репозитории
   - Перенести сервисы в `service/impl` и интерфейсы в `service/interfaces`.
   - Репозитории оставить в `repository`.

6) MapStruct
   - Добавить зависимости и создать мапперы в `mapper`.

7) Конфигурация
   - Перенести конфигурационные классы в `config` (security, openapi и т.д.).

8) Тесты
   - Добавить интеграционные тесты (Testcontainers) и MockMvc тесты для контрактов API.

Риски и mitigations
- Перемещение пакетов не должно ломать API, но может повлиять на component scanning — убедитесь, что `@SpringBootApplication` scan покрывает корневой пакет (`com.example.helpTree`).
- Выполнять перемещения небольшими коммитами и запускать тесты после каждого шага.

Checklist для PR
- [ ] Все тесты зелёные
- [ ] Примеры вызовов API в README или OpenAPI
- [ ] Проверка CI

Roadmap (30/60/90 дней)
- 0–7 дней: README + ARCHITECTURE + OpenAPI + basic mapping
- 7–30 дней: MapStruct, Testcontainers, рефакторинг пакетов
- 30–90 дней: Security (JWT), caching, search, observability


Если хотите, могу начать перенос реальных файлов по группам (каждый шаг с прогоном тестов и созданием отдельного коммита). Напишите, с какого пакета начать (например, `controller` -> `api/controller`).
