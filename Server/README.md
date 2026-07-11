# AutoAPI Server (Phase 1)

Java 21 **Spring WebFlux** gateway data plane for AutoAPI. Phase 1 loads a static JSON runtime configuration, matches L7 routes, and asynchronously reverse-proxies matching requests to a configured upstream.

## Why Java WebFlux

The gateway is the live request path. It must handle concurrent HTTP traffic, nonblocking proxy I/O, connection reuse, and streaming bodies. Spring WebFlux on Reactor Netty provides that foundation without adopting Spring Cloud Gateway, keeping route matching, request-ID handling, header policy, and proxy error mapping as AutoAPI-owned code.

## Current capabilities (Phase 1)

- Static JSON runtime configuration (validated at startup)
- Host + longest path-prefix + HTTP method routing
- Nonblocking reverse proxy (`WebClient` + Reactor Netty)
- Request ID propagation (`X-Request-ID`)
- `/healthz` and `/readyz`
- JSON error envelope (`404`, `405`, `502`, `500`)

## Current limitations

- Static configuration only (no control plane, versions, polling, ACK/NACK)
- One upstream URL per route
- No API-key authentication
- No rate limiting (no Redis)
- No retries, health-aware routing, or traffic splitting
- No PostgreSQL or dynamic config swap

## Build and test

```bash
cd Server
./gradlew --no-daemon spotlessCheck test check bootJar
```

## Run locally (JAR)

```bash
cd Server
./gradlew bootJar
java -jar build/libs/autoapi-server-0.1.0-SNAPSHOT.jar \
  --autoapi.config.path=../deploy/config/runtime.json
```

Start a mock upstream separately (`tests/mock-upstream/server.py`) and point `deploy/config/runtime.json` at it.

## Run with Docker Compose (from repo root)

```bash
docker compose up --build
curl -H 'Host: api.autoapi.local' http://localhost:8080/v1/orders/123
```

## Configuration path precedence

1. `--autoapi.config.path=/path/to/runtime.json`
2. `AUTOAPI_CONFIG_PATH` environment variable

Startup fails if the file is missing, JSON is invalid, or validation fails.

## Runtime JSON format

```json
{
  "gateway": { "listenAddress": "0.0.0.0", "port": 8080 },
  "routes": [
    {
      "id": "orders-route",
      "host": "api.autoapi.local",
      "pathPrefix": "/v1/orders",
      "methods": ["GET", "POST"],
      "upstream": { "url": "http://upstream-v1:8080" }
    }
  ]
}
```

## Route matching

- **Host:** case-insensitive; port stripped (`api.local:8080` → `api.local`). Bracketed IPv6 (`[::1]:8080`) supported; bare IPv6 literals are not.
- **Path prefix:** longest match with segment boundaries (`/v1/orders` matches `/v1/orders/123` but not `/v1/orders-old`).
- **Root prefix `/`:** matches any path starting with `/`.
- **405 vs 404:** if host+path match but method does not, returns `405 METHOD_NOT_ALLOWED` with sorted `Allow` header; otherwise `404 ROUTE_NOT_FOUND`.
- **Ambiguity:** validation rejects overlapping methods for the same normalized host and path prefix.

## Request ID

- Uses first nonblank `X-Request-ID` (trimmed, max 128 chars); otherwise generates UUID.
- Attached to upstream request, downstream response, logs, and JSON errors.

## Forwarding header trust model (Phase 1)

Client-supplied `X-Forwarded-For`, `X-Forwarded-Host`, and `X-Forwarded-Proto` are **removed** and replaced with gateway-derived values. Hop-by-hop headers (including `Connection` tokens) are stripped on requests and responses.

## Error responses

```json
{
  "error": {
    "code": "ROUTE_NOT_FOUND",
    "message": "No matching route was found",
    "requestId": "..."
  }
}
```

Codes: `ROUTE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `UPSTREAM_UNAVAILABLE` (502), `INTERNAL_GATEWAY_ERROR` (500).

## Graceful shutdown

`server.shutdown=graceful` with `spring.lifecycle.timeout-per-shutdown-phase=30s`. In-flight requests receive up to 30 seconds to complete after shutdown signal.

## CI/CD

| Event | Behavior |
|-------|----------|
| PR touching `Server/**` | `Server CI`: Gradle test/check/bootJar, `docker compose config`, Compose integration smoke |
| PR | Container image **built** but **not pushed** |
| Push to `main` | Publishes `ghcr.io/<owner>/autoapi-server:main` and `:sha-<short>` |
| Tag `v*.*.*` | Publishes semver tags and `latest` |
| AWS / persistent deploy | **Not performed** (roadmap Phase 9) |

### Recommended branch protection checks (enable in GitHub settings)

- `Server CI / test-and-build`
- `Server CI / compose-validation`
- `Server CI / integration`
- `Server Container / build-and-test`
- `Server Container / container`

Branch protection is **not** configured by this repository automatically.

### Local CI-equivalent script

```bash
./scripts/verify-server.sh
./scripts/smoke-server.sh   # requires Docker
```

## Container image

Multi-stage `Server/Dockerfile` (Java 21 Temurin). Non-root `autoapi` user. Example:

```bash
docker build -f Server/Dockerfile -t autoapi-server:local Server
```
