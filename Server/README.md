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
- No upstream base-path joining or path rewriting

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

## Upstream URI policy (Phase 1)

Each route `upstream.url` identifies an **upstream origin only** — scheme, host, and optional port. Phase 1 does **not** implement upstream base-path joining or path rewriting; the incoming client path is forwarded unchanged.

**Accepted examples:**

- `http://backend:8080`
- `https://backend.example.com`
- `http://backend:8080/` (trailing `/` is equivalent to no path)

**Rejected at startup validation:**

- Non-root paths (`http://backend:8080/internal-api`, `http://backend:8080/v1`)
- Query strings (`http://backend:8080?mode=test`)
- Fragments (`http://backend:8080#fragment`)
- Unsupported schemes or user-info credentials

## Route matching

- **Host:** case-insensitive; port stripped (`api.local:8080` → `api.local`). Bracketed IPv6 (`[::1]:8080`) supported; bare IPv6 literals are not.
- **Path prefix:** longest match with segment boundaries (`/v1/orders` matches `/v1/orders/123` but not `/v1/orders-old`).
- **Root prefix `/`:** matches any path starting with `/`.
- **Method-aware longest prefix:** AutoAPI selects the longest matching path prefix **among routes that accept the request method**. A broader prefix may therefore handle a method that a more specific path route does not accept. For example, with `POST` on `/v1` and `GET` only on `/v1/orders`, `POST /v1/orders/123` matches the `/v1` route rather than returning `405`.
- **405 vs 404:** if at least one route matches host and path prefix but **none** of those routes accept the request method, returns `405 METHOD_NOT_ALLOWED` with a sorted `Allow` header listing methods from all host+path candidates. If no route matches host and path prefix, returns `404 ROUTE_NOT_FOUND`.
- **Ambiguity:** validation rejects overlapping methods for the same normalized host and path prefix.

## Request ID

- Uses first nonblank `X-Request-ID` (trimmed, max 128 chars); otherwise generates UUID.
- Attached to upstream request, downstream response, logs, and JSON errors.

## Outbound Host and forwarding headers (Phase 1)

When proxying to a selected upstream:

| Header | Value |
|--------|-------|
| `Host` | Selected upstream authority (e.g. `upstream-v1:8080`) |
| `X-Forwarded-Host` | Normalized original client-facing host (port stripped, lowercase) |
| `X-Forwarded-Proto` | Gateway-derived scheme |
| `X-Forwarded-For` | Client remote address |

The client-facing `Host` header is **not** forwarded upstream as-is. Client-supplied `X-Forwarded-For`, `X-Forwarded-Host`, and `X-Forwarded-Proto` are **removed** and replaced with gateway-derived values. Hop-by-hop headers (including `Connection` tokens) are stripped on requests and responses.

## Proxy error handling

| Condition | Client response |
|-----------|-----------------|
| Known upstream transport failure (`WebClientRequestException`, connection refused, DNS failure, etc.) | `502 UPSTREAM_UNAVAILABLE` |
| Unexpected gateway failure before response commit | `500 INTERNAL_GATEWAY_ERROR` |
| Stream failure after downstream response commit | Log with request ID; terminate stream safely; **do not** write a second JSON error body |

Client error bodies never include Java exception class names, stack traces, upstream DNS details, or raw Reactor exception text. Internal logs include request ID, route ID (when available), upstream authority, exception type, and a safe summary.

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

If JSON serialization fails unexpectedly, the gateway returns a constant safe fallback body with `"requestId": "unavailable"`.

## Graceful shutdown

`server.shutdown=graceful` with `spring.lifecycle.timeout-per-shutdown-phase=30s`. In-flight requests receive up to 30 seconds to complete after shutdown signal.

## CI/CD

| Event | Validation | GHCR publication |
|-------|------------|------------------|
| Push to any branch (relevant paths) | Java CI + Compose validation + integration + container build/smoke | **No** |
| Pull request (relevant paths) | Same validation | **No** |
| Push to `main` | Same validation | `ghcr.io/<owner>/autoapi-server:main` and `:sha-<short>` |
| Semantic version tag `v*.*.*` | Same validation | Semver tags and `latest` |
| Manual `workflow_dispatch` | Same validation | Only when ref is `main` or a `v*` tag (same rules as push) |
| AWS / persistent deploy | **Not performed** (roadmap Phase 9) | — |

**Trigger scope:** every relevant push to any branch (e.g. `phase1-dev`, `feature/routing`, `fix/proxy-host`) runs validation workflows. Changes under `server-old/**` alone do not trigger Server CI or Container workflows.

**Container integrity:** the container workflow builds one local image (`autoapi-server:ci`), smoke-tests that exact image, then tags and pushes it to GHCR when publication is eligible. Feature branches and pull requests never publish images.

### Recommended branch protection checks (enable in GitHub settings)

- `Server CI / test-and-build`
- `Server CI / compose-validation`
- `Server CI / integration`
- `Server Container / build`
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
