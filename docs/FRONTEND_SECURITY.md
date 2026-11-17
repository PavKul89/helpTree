# Безопасность frontend: токены, cookie и CSRF

## Текущая схема

Токены больше не хранятся в `localStorage`.

- **Refresh-токен** выдаётся бэкендом только в `Set-Cookie` с атрибутами
  `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`. JavaScript не может его
  прочитать. Cookie попадает только на auth-маршруты.
- **Access-токен** живёт только в памяти (`frontend/src/api/tokenStore.ts`) и
  передаётся в заголовке `Authorization: Bearer ...`. После перезагрузки
  страницы сессия восстанавливается тихим вызовом `/api/auth/refresh`.
- **CSRF**. Поскольку refresh — единственный мутирующий эндпоинт, который
  аутентифицируется автоматически прикладываемой cookie, он защищён
  double-submit: бэкенд дополнительно ставит читаемую cookie `helptree_csrf`,
  SPA возвращает её значение в заголовке `X-CSRF-Token`, фильтр сравнивает
  значения в constant-time и дополнительно проверяет `Origin` по списку
  `CORS_ORIGINS`. CSRF-токен ротируется при каждом refresh.
- **Logout** / `logout-all` очищают обе cookie на сервере, а не только память SPA.

Поле `refreshToken` больше **не сериализуется** в `AuthResponse` (`@JsonIgnore`).
`RefreshTokenRequest` в теле `/api/auth/refresh` сохранён для небраузерных
клиентов, но браузерный клиент не может получить refresh-токен из ответа.

## Что по-прежнему нельзя забывать

- `style-src` в CSP содержит `'unsafe-inline'` — React использует инлайн-style.
  Полный отказ от него потребует nonce/hash на сборке.
- Внешние источники в CSP (`images.unsplash.com`, тайлы карт, Google Fonts,
  `corsproxy.io`) разрешены явно. При добавлении нового домена его нужно добавить
  в `Content-Security-Policy` в `deploy/Caddyfile`, иначе ресурс будет заблокирован.
- `Content-Security-Policy` выставляется только на основной домен, но не на
  `storage.${DOMAIN}`: консоль MinIO использует инлайн-скрипты и сломалась бы.
- XSS по-прежнему опасен для access-токена в памяти (он доступен выполняемому
  JavaScript в текущей вкладке), но не даёт долгоживущего refresh-токена и не
  сохраняется между перезагрузками.

## Что сделано ранее

- `axios` обновлён до `1.21.1`, `react-router-dom` — до `7.18.5`.
- Устаревший `react-scripts` заменён на Vite + Jest. `npm audit` показывает
  `0 vulnerabilities`.
- Сохранён контракт `REACT_APP_API_URL` / `REACT_APP_WS_URL`.

## Проверки

- `npm test` — refresh, CSRF-заголовок, одиночный refresh promise, WebSocket-ticket retry.
- `npm run typecheck`.
- Backend: `AuthCookieServiceTest`, `RefreshCsrfFilterTest`, `SecurityConfigTest`,
  `AuthControllerTest`.
