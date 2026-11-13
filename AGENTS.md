# AGENTS.md

## Project shape

This repo is a two-part app:

- `helpTree-service/` is the Spring Boot 4 backend (Java 21, Maven multi-module parent at the repo root).
- `frontend/` is a React + TypeScript SPA built with `react-scripts`.
- Root `docker-compose.yml` runs Postgres, Prometheus, Alertmanager, Grafana, Jaeger, and Silo/MinIO.

The root Maven project is `helpTree-parent` in `pom.xml`; the only application module is `helpTree-service`.

## Backend architecture

The service code lives under `helpTree-service/src/main/java/org/example/helptreeservice/` and follows a standard Spring layering:

- `controller/` holds REST and WebSocket controllers (`AccountController`, `ChatController`, `NotificationController`, etc.)
- `service/` contains business logic
- `repository/` contains Spring Data repositories
- `entity/`, `dto/`, `mapper/`, `converter/` handle persistence and API mapping
- `security/` contains auth and permission configuration (`SecurityConfig`, `JwtAuthFilter`)
- `config/` holds `@ConfigurationProperties`, async/scheduling config, and app setup
- `filter/` includes request-scoped filters like rate limiting
- `scheduler/` contains scheduled jobs
- `event/`, `metrics/`, `logging/`, `health/` support observability and operational features

Key backend entrypoints:

- `helpTree-service/src/main/java/.../HelpTreeServiceApplication.java` enables scheduling and caching
- `helpTree-service/src/main/java/.../security/SecurityConfig.java` defines the main `SecurityFilterChain`, JWT auth, CORS, and actuator protection
- `helpTree-service/src/main/resources/application.yaml` is the canonical runtime config, using `${ENV_VAR:default}` placeholders

## Frontend architecture

The frontend is a React SPA in `frontend/src/` with route-based page structure and provider-based auth flow.

Important patterns from the current app:

- `frontend/src/App.tsx` is the root router. It splits routes into public pages, protected pages, and `/admin` routes.
- `AuthProvider` and `useAuth()` are the source of truth for session state; protected pages wrap child screens in `ProtectedRoute`.
- The map page is lazy-loaded with `React.lazy()` and a loading fallback (`/map` route in `App.tsx`).
- UI state relies on `ToastProvider` and direct API modules under `frontend/src/api/`.

Use the existing page/component naming conventions when adding new screens: page modules live in `frontend/src/pages`, reusable UI goes in `frontend/src/components`, and API calls stay in `frontend/src/api`.

## Local workflow

### Backend

Run the backend locally with Maven:

```bash
./mvnw -pl helpTree-service spring-boot:run
```

Run tests for the service:

```bash
./mvnw -pl helpTree-service test
```

Relevant config:

- root `pom.xml` sets Java 21 and the Spring Boot BOM
- `helpTree-service/pom.xml` declares the backend dependencies and Flyway plugin
- `helpTree-service/src/main/resources/application.yaml` is the default config; local dev values come from environment variables and `.env`

### Frontend

From the repo root:

```bash
cd frontend
npm ci
npm start
```

The repo’s frontend README documents the expected runtime settings:

- default API URL: `http://localhost:8080`
- WebSocket URL is derived from the API URL when not explicitly set
- `REACT_APP_API_URL` and `REACT_APP_WS_URL` are build-time values; rebuild after changing them

### Docker / infrastructure

Use the repo root compose setup for supporting services:

```bash
docker compose up -d
```

This project expects `.env` to exist, based on `.env.example` in the repo root. The environment file includes required values for Postgres, JWT, Silo/MinIO, Prometheus, Grafana, mail settings, and feature flags.

## Configuration and environment conventions

- Keep runtime settings in `application.yaml` and environment variables in `.env`.
- Do not hardcode secrets or well-known credentials; the repo explicitly requires non-default values in `.env.example`.
- The backend has a production validator pattern: production config must reject weak or sample secrets.
- CORS and websocket origin allowlists are configured through environment values rather than in code.

Examples in the repo:

- `helpTree-service/src/main/resources/application.yaml` uses `${VAR:default}` placeholders for DB, JWT, tracing, and feature flags.
- `.env.example` documents required values such as `JWT_SECRET`, `DB_PASS`, `SILO_ACCESS_KEY`, `SILO_SECRET_KEY`, `GRAFANA_ADMIN_PASSWORD`, and `PROMETHEUS_SCRAPE_PASSWORD`.

## Security and API patterns

The backend uses Spring Security with JWT-based authentication and a custom filter chain. Key conventions:

- `SecurityConfig` permits public access only to explicit routes (`/api/auth/**`, `/api/posts/**`, `/swagger-ui/**`, `/ws/**`, some public read endpoints)
- Actuator endpoints are guarded; `/actuator/health`, `/actuator/info` are public, everything else requires ADMIN
- JWT is handled in the security package; rate-limiter filtering runs in the security chain after JWT authentication
- Error responses are standardized through the API error writer and maintain a consistent envelope for security failures

When changing auth or routing behavior, keep the allowlist and protected-route logic in sync with the current controller layout.

## Data and background jobs

This app uses multiple operational concerns that matter for AI agents:

- Flyway migrations run automatically via Spring Boot; the backend is configured to validate migrations and use PostgreSQL
- scheduled jobs are enabled by `@EnableScheduling` in the main application class
- caches are enabled with `@EnableCaching`
- async tasks are configured separately under `config/`
- WebSocket support is enabled with Spring STOMP/Broker configuration (`WebSocketConfig` in `config/`)

If you touch persistence, background jobs, or messaging, review the related config and the migration scripts under `helpTree-service/src/main/resources/db/`.

## Testing and validation

Prefer the project’s existing test entry points:

```bash
./mvnw -pl helpTree-service test
cd frontend && npm test -- --watchAll=false
```

The frontend uses React Testing Library and CRA/Jest conventions (`testing-library/react`, `@testing-library/jest-dom`). The backend uses Spring Boot test starters and Testcontainers for migration/integration tests where relevant.

## Editing guidance for AI agents

- Preserve existing package/module boundaries; do not move code between `frontend/` and `helpTree-service/` unless the task explicitly requires it.
- Keep API contract changes aligned across backend DTOs, controllers, and frontend callers.
- Follow the existing naming patterns already visible in the repo (`*Controller`, `*Page`, `*Properties`, `*Config`).
- When adding configuration, prefer the pattern used in `application.yaml` and `.env.example`: environment-driven values with clear defaults and documentation.
- Do not invent new service boundaries or new top-level apps; this repo is intentionally split into one backend and one frontend.

## Useful references

- `pom.xml` — root multi-module Maven structure and Java version
- `helpTree-service/pom.xml` — backend dependencies and build config
- `helpTree-service/src/main/resources/application.yaml` — runtime configuration defaults
- `helpTree-service/src/main/java/.../security/SecurityConfig.java` — auth, CORS, and route protections
- `frontend/src/App.tsx` — router structure and protected/public route behavior
- `frontend/README.md` — frontend local run and env variable contract
- `.env.example` — required deployment and local runtime secrets
- `docker-compose.yml` — local infrastructure stack

