# AutoAPI Server

Java 21 **Spring WebFlux** application with a nonblocking L7 gateway, PostgreSQL-backed control plane, immutable configuration compilation/activation, multi-gateway convergence, **API-key authentication**, **cross-gateway Redis rate limiting** (Phase 4), **passive backend health tracking with health-aware routing** (Phase 5), and **bounded idempotency-aware request retries with gateway-local retry budgets** (Phase 6).

## Phase 6 highlights

- Route-bound **retry policies** compiled into immutable snapshots (`maxAttempts` includes the first attempt; `1` disables retries)
- Retries only for configured **pre-response transport failures** (connect, reset, DNS, response timeout) — not HTTP 5xx
- **Idempotency-Key** required for unsafe methods (POST/PATCH) when policy allows them; gateway does not provide durable exactly-once storage
- Bounded **request-body replay** for retry-enabled routes (`autoapi.gateway.retry.max-replay-body-bytes`, default 1 MiB); oversized bodies are forwarded once without retry
- **Per-attempt** passive-health accounting and independent timeouts
- Retry attempts prefer a **different eligible target** when available
- **Gateway-local retry budgets** per `apiId + routeId + policyId` (not PostgreSQL/Redis)
- Internal visibility: `GET /internal/v1/retry-status`
- Terminal mapping: transport failures → `502 UPSTREAM_UNAVAILABLE`; response timeout → `504 UPSTREAM_TIMEOUT`

## Phase 5 highlights

- Passive **transport-failure** detection (connection/DNS/timeout/premature close — not HTTP 5xx)
- Consecutive-failure threshold, temporary target ejection, and expiry-based recovery through real traffic
- `maxEjectionPercent` cap; health-aware round robin among eligible targets
- All-targets-ejected fallback: select earliest `ejectedUntil` (degraded, no same-request retry)
- Gateway-local health state (`TargetHealthRegistry`); not stored in PostgreSQL or Redis
- Management API: `backend-health-policies` + pool binding; compiled into immutable pool snapshots
- Internal visibility: `GET /internal/v1/upstream-health` (trusted network only; unauthenticated MVP)

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
./scripts/smoke-phase5.sh
./scripts/smoke-phase6.sh
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

## Known limitations (Phase 5)

- No active health probes, half-open circuit breaker, or HTTP 5xx-based ejection
- No automatic retries, hedged requests, or traffic splitting within a request
- Backend health views are **gateway-local** and may differ temporarily across instances
- Internal upstream-health endpoint is unauthenticated — do not expose publicly

See `docs/ARCHITECTURE.md`, `docs/API_SPEC.md`, and `docs/DISTRIBUTED_SYSTEMS.md` for full design detail.
