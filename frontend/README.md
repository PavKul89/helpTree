# HelpTree frontend

## Run locally

```bash
npm ci
npm start
```

Run tests with `npm test -- --watchAll=false`; create a production build with
`npm run build`.

By default, the development frontend uses `http://localhost:8080` for API
requests and derives the WebSocket address from it.

To customize local settings, copy `.env.example` to `.env`. The same variables
can also be supplied by the build environment:

- `REACT_APP_API_URL` — backend base URL; defaults to
  `http://localhost:8080`. Set it to an empty value to use same-origin API
  paths behind a reverse proxy.
- `REACT_APP_WS_URL` — complete WebSocket URL, including `/ws/chat`; when
  omitted, it is derived from `REACT_APP_API_URL` or the current page origin.

For production, build with `npm run build`. `REACT_APP_*` values are embedded
in the generated files at build time; changing environment variables after the
build does not change the URLs. Rebuild to use different values.
