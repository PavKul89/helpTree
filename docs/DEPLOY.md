# Production deployment

The production stack is built from the repository root and publishes only
Caddy's HTTP/HTTPS ports. PostgreSQL, the backend, Silo, Prometheus, Grafana,
Jaeger and the frontend stay on a private Docker network.

## First deployment

1. Prepare a Linux server with Docker Engine and the Docker Compose plugin.
   Allow inbound TCP ports 80 and 443 in the host firewall; do not expose
   PostgreSQL, backend, Prometheus, Grafana, Jaeger or the Silo console.
2. Create DNS A records for `DOMAIN` and `storage.DOMAIN`, both pointing to the
   server's public IP. Let's Encrypt must be able to reach ports 80/443.
3. Copy `.env.example` to `.env`. Set `DOMAIN`, `ACME_EMAIL`, distinct strong
   values for `DB_PASS`, `JWT_SECRET`, `SILO_SECRET_KEY`,
   `PROMETHEUS_SCRAPE_PASSWORD`, `GRAFANA_ADMIN_PASSWORD`, and
   `ADMIN_PASSWORD`. Generate each secret with `openssl rand -base64 64`;
   choose a non-default `SILO_ACCESS_KEY` and a valid `ADMIN_EMAIL`.
4. On the first deployment only, set `ADMIN_BOOTSTRAP_ENABLED=true` to create
   the initial administrator. After confirming that account can sign in,
   set it back to `false` and recreate the backend container.
5. Keep unique `BACKEND_IMAGE` and `FRONTEND_IMAGE` tags for each release.
   From the repository root, build and start the services:

   ```sh
   docker compose --env-file .env -f docker-compose.prod.yml config
   docker compose --env-file .env -f docker-compose.prod.yml up -d --build
   ```

6. Check service and health status:

   ```sh
   docker compose --env-file .env -f docker-compose.prod.yml ps
   curl -fsS "https://${DOMAIN}/api/stats"
   docker compose --env-file .env -f docker-compose.prod.yml exec backend \
     curl -fsS http://localhost:8081/actuator/health
   ```

   The backend health endpoint is only exposed on the private network.
   Grafana, Prometheus, Jaeger UI and the Silo console are not published; use
   an SSH tunnel when operator access is needed. The storage S3 endpoint is
   available only at `https://storage.${DOMAIN}` through Caddy.

## Updates and rollback

Before an update, retain the current `BACKEND_IMAGE` and `FRONTEND_IMAGE`
values and their local images (or push them to a private registry). Assign new,
unique image tags in `.env`, then run:

```sh
docker compose --env-file .env -f docker-compose.prod.yml build --pull
docker compose --env-file .env -f docker-compose.prod.yml up -d
docker compose --env-file .env -f docker-compose.prod.yml ps
```

For rollback, restore the previous backend and frontend image tags in `.env`
and run `docker compose --env-file .env -f docker-compose.prod.yml up -d
--no-build`. Do not remove the retained images until the release is stable.
Database migrations are forward-only; verify that a release's schema changes
are compatible with the previous application image before relying on rollback.

## Network and proxy notes

Caddy obtains and renews public certificates automatically. It routes `/api/*`
and `/ws/*` to the backend and other paths to the React frontend; WebSocket
upgrade handling is built into Caddy's reverse proxy. `X-Forwarded-For` is
replaced with the peer address observed by Caddy before the backend trusts
forwarded headers. The backend ports are never published to the host.

The Silo console is deliberately not proxied. Use an SSH tunnel to access
internal dashboards; do not add public routes without authentication and an
explicit access policy. For an nginx deployment, follow the header and
WebSocket requirements in [PROXY.md](PROXY.md).

Jaeger all-in-one keeps recent traces in memory; trace storage is diagnostic
and is not a durable archive.
