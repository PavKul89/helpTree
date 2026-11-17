# Безопасность frontend: токены и зависимости

## Хранение токенов

Сейчас оба токена хранятся в `localStorage`:

- `accessToken` — короткоживущий токен доступа;
- `refreshToken` — долгоживущий токен обновления;
- `token` — совместимый legacy-ключ, который читают перехватчики и WebSocket.

Это осознанный остаточный риск, а не имитация защиты. Любой успешный XSS в
браузере может прочитать оба значения. В коде приложения нет
`dangerouslySetInnerHTML`, `innerHTML`, `eval` и `document.write`, а зависимости
обновлены, но полностью исключить XSS на стороне SPA нельзя.

## Почему токены пока не перенесены в httpOnly cookie

Текущий бэкенд-контракт ожидает токены в JSON:

- `POST /api/auth/refresh` принимает `refreshToken` в теле запроса;
- `POST /api/auth/logout` принимает `refreshToken` в теле и `accessToken` в
  заголовке `Authorization`;
- WebSocket-подключение использует `Authorization: Bearer ...` и отдельный
  ticket из `GET /api/auth/ws-ticket`.

Простое переименование ключей в `localStorage` или запись фиктивной cookie
ничего не изменит для злоумышленника. Полный переход требует изменений и
бэкенда, и frontend:

1. бэкенд выставляет refresh-токен в `Set-Cookie: HttpOnly; Secure;
   SameSite=Strict` и принимает его от браузера автоматически;
2. добавляется защита от CSRF для операций refresh/logout;
3. WebSocket-handshake перестаёт зависеть от токена в JavaScript и использует
   cookie/ticket;
4. frontend удаляет токены из `localStorage` и перестаёт передавать их вручную.

Пока эти изменения не сделаны, перенос в cookie без изменения API привёл бы к
рассинхрону контрактов, а не к реальному повышению безопасности.

## Что уже сделано

- `axios` обновлён до `1.21.1`, `react-router-dom` — до `7.18.5` (исправлены
  уязвимые версии этих runtime-зависимостей).
- Устаревший `react-scripts` заменён на Vite + Jest. `npm audit` показывает
  `0 vulnerabilities`.
- Сохранён контракт `REACT_APP_API_URL` / `REACT_APP_WS_URL`: Vite инъецирует
  эти значения на этапе сборки, Docker build args и документация не менялись.
- Тесты и production-сборка проверяются в CI и Docker.

## Рекомендуемый следующий шаг

Реализовать cookie-based refresh с CSRF-защитой на бэкенде и только после этого
убрать токены из JavaScript-хранилища. До тех пор риск XSS → кража токена
остаётся принятым.
