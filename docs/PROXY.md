# Reverse proxy client addresses

When the backend is served behind nginx or Caddy, set
`TRUST_FORWARDED_HEADERS=true` in the backend environment so rate limits,
login protection, audit records, and request logs use the client's IP address.
Leave it `false` when requests can reach the backend directly from untrusted
clients.

Configure the proxy to **replace** forwarded client-address headers with the
address it observed. For nginx:

```nginx
location / {
    proxy_pass http://helpTree-service:8080;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Real-IP $remote_addr;
}

location /ws/ {
    proxy_pass http://helpTree-service:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Real-IP $remote_addr;
}
```

Use `$remote_addr`, **not** `$proxy_add_x_forwarded_for`: the latter can retain
client-supplied values, and the backend uses the first `X-Forwarded-For` entry.
Forwarded values are accepted only when they are valid numeric IPv4 or IPv6
addresses; invalid values fall back to the connection's remote address.

When forwarded headers are trusted, direct access to the backend must be
blocked. Do not publish the backend port to the host or the internet; in Docker
Compose, use `expose` for the backend port instead of `ports`, and allow access
only from the proxy on the private application network.

## Production deployment checklist (Plan 5)

- [ ] Set `TRUST_FORWARDED_HEADERS=true` for the backend only when traffic must
      pass through the trusted proxy.
- [ ] Do not publish the backend port; expose it only to the private Compose
      network and the proxy.
- [ ] Configure the proxy to overwrite `X-Forwarded-For` and `X-Real-IP` using
      `$remote_addr`; do not append client-provided forwarded values.
- [ ] Forward the WebSocket `Upgrade` and `Connection` headers for `/ws/`.
