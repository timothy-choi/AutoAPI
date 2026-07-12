# AutoAPI Server

Java 21 **Spring WebFlux** application with a nonblocking L7 gateway, PostgreSQL-backed control plane, immutable configuration compilation/activation, multi-gateway convergence, **API-key authentication**, and **cross-gateway Redis rate limiting** (Phase 4).

## Phase 4 highlights

- Structured API keys: `ak_live_<keyId>.<secret>` (Bearer token)
- One-time plaintext issuance; HMAC-SHA-256 digest stored with shared pepper (`AUTOAPI_API_KEY_PEPPER`)
- Gateway O(1) key lookup, constant-time digest comparison, no PostgreSQL on the request path
- Fixed-window Redis Lua script shared across gateway instances
- Rate-limit headers: `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`, `Retry-After`
- Policy failure modes: `FAIL_OPEN` (allow on Redis outage) and `FAIL_CLOSED` (503)

## Build and test

```bash
cd Server
./gradlew --no-daemon spotlessCheck
./gradlew --no-daemon test
./gradlew --no-daemon check
./gradlew --no-daemon bootJar
```

Integration tests use Testcontainers (PostgreSQL and Redis). Docker must be available locally for the full suite.

## Run with Docker Compose (Phase 4 stack)

From repository root:

```bash
docker compose up --build
./scripts/smoke-phase4.sh
docker compose down -v
```

Services: `postgres`, `redis`, `control-plane`, `gateway-a`, `gateway-b`, `upstream-v1`, `upstream-v2`.

Development pepper (not production-safe) is supplied via Compose:

```text
AUTOAPI_API_KEY_PEPPER=development-only-change-me-not-for-production-use
```

Changing the pepper invalidates all issued API keys unless they are reissued.

## Security notes

- Plaintext API keys are returned only on `POST /api/v1/apis/{apiId}/api-keys`
- Pepper is never stored in PostgreSQL, Redis, or published snapshots
- Key revocation affects the data plane only after publish + activate of a new config version
- Gateway `/readyz` stays available when Redis is down; individual routes enforce `FAIL_OPEN` / `FAIL_CLOSED`

## Known limitations (Phase 4)

- No user login, OAuth, billing, or emergency immediate revocation channel
- Fixed-window boundary bursts; single Redis deployment; no multi-region quotas
- No retries, backend health-aware routing, or traffic splitting

See `docs/ARCHITECTURE.md`, `docs/API_SPEC.md`, and `docs/DISTRIBUTED_SYSTEMS.md` for full design detail.
