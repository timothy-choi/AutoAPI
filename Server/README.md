# AutoAPI Server

Java 21 **Spring WebFlux** application implementing the AutoAPI control plane and gateway runtime in a single deployable artifact. Role is selected at startup via `AUTOAPI_ROLE` (`control-plane`, `gateway`, or `combined`).

For project overview, architecture diagrams, and quick start, see the [root README](../README.md).

## Capabilities

| Area | Implementation |
|------|----------------|
| Gateway data plane | Nonblocking L7 reverse proxy, atomic runtime snapshot activation |
| Control plane | PostgreSQL-backed management, validation, compilation, activation |
| Authentication | API keys (`ak_live_…`) with HMAC-SHA256 digests |
| Rate limiting | Cross-gateway Redis fixed-window limits (Lua script) |
| Resilience | Passive health, bounded retries, circuit breakers, traffic splitting |
| Operations | Service discovery, gateway groups, rollouts, events, webhooks |
| Governance | Management RBAC, policy engine with hierarchical inheritance |
| Observability | Request IDs, W3C trace context, structured logs, Prometheus metrics |

## Build and Test

```bash
cd Server
./gradlew --no-daemon spotlessCheck
./gradlew --no-daemon test
./gradlew --no-daemon check
./gradlew --no-daemon bootJar
```

Integration tests use Testcontainers (PostgreSQL and Redis). Docker must be available for the full suite.

From the repository root:

```bash
./scripts/verify-server.sh
```

## Run with Docker Compose

From the repository root:

```bash
docker compose up --build
./scripts/smoke-phase4.sh
./scripts/smoke-phase13.sh
./scripts/smoke-phase14.sh
docker compose down -v
```

| Service | Host port | Role |
|---------|-----------|------|
| `control-plane` | 8081 | Management API |
| `gateway-a` | 8080 | Gateway |
| `gateway-b` | 8082 | Gateway |
| `gateway-c` | 8083 | Gateway |

See [`docker-compose.yml`](../docker-compose.yml) for the full service list including mock upstreams.

## Gateway Pipeline

Per-request enforcement order:

```text
Route match → API key auth → rate limit → traffic split → backend health
  → circuit breaker → bounded retries → upstream proxy
```

Internal diagnostic endpoints (trusted network only in development):

- `GET /internal/v1/upstream-health`
- `GET /internal/v1/retry-status`
- `GET /internal/v1/traffic-splits`
- `GET /internal/v1/circuit-breakers`
- `GET /internal/v1/request-summaries`

## Security Notes

- Plaintext API keys are returned only on `POST /api/v1/apis/{apiId}/api-keys`
- `AUTOAPI_API_KEY_PEPPER` and `AUTOAPI_MANAGEMENT_TOKEN_PEPPER` must be set in production
- Key revocation affects the data plane only after publish + activate of a new config version
- Gateway `/readyz` stays available when Redis is down; routes enforce `FAIL_OPEN` / `FAIL_CLOSED` per policy

## CI/CD

GitHub Actions workflows in [`.github/workflows/`](../.github/workflows/):

- **server-ci.yml** — Gradle checks, Compose validation, Phase 4–7/13/14 smoke tests
- **server-container.yml** — Docker build, Trivy scan, container smoke, GHCR publication on `main` and version tags

See [root README — CI/CD](../README.md#cicd) for publication policy.

## Documentation

| Topic | Document |
|-------|----------|
| Architecture | [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) |
| Management API | [`docs/API_SPEC.md`](../docs/API_SPEC.md) |
| Policy engine | [`docs/POLICY_ENGINE.md`](../docs/POLICY_ENGINE.md) |
| Management auth | [`docs/MANAGEMENT_AUTH.md`](../docs/MANAGEMENT_AUTH.md) |
| Observability | [`docs/OBSERVABILITY.md`](../docs/OBSERVABILITY.md) |
