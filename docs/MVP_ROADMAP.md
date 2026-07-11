# AutoAPI MVP Roadmap

Vertical-slice-first sequencing for one developer. Each phase delivers a demonstrable capability before adding distributed complexity.

No Kubernetes, Kafka, custom consensus, or gRPC streaming before the core vertical slice works.

---

## Phase 0: Repository Boundary and Legacy Freeze

### Goal

Establish clean separation between legacy code and new architecture without deleting history.

### Implementation Tasks

- [ ] Add `legacy/` symlink or document that `server-old/` is read-only reference (no moves yet per migration doc)
- [ ] Create new top-level directories: `control-plane/`, `gateway/`, `deploy/`, `tests/`
- [ ] Add root `README.md` pointer to `docs/README.md`
- [ ] Add `.env.example` for new stack (PostgreSQL, Redis, gateway credentials)
- [ ] Add `docker-compose.yml` skeleton (services defined, minimal impl)
- [ ] Add CI stub (lint only) — optional GitHub Actions workflow

### Components/Files Affected

- `/README.md`
- `/docker-compose.yml` (new)
- `/control-plane/` (new, empty)
- `/gateway/` (new, empty)
- `/docs/*` (this set)

### Tests

- None required; verify `docs/` complete and legacy untouched

### Definition of Done

- Legacy `server-old/` unmodified and clearly labeled frozen in `LEGACY_MIGRATION.md`
- New directory scaffold exists
- Developer can clone repo and read docs to understand next steps

---

## Phase 1: Single-Gateway Vertical Slice

### Goal

One Go gateway proxies HTTP to one upstream with host/path routing. No control plane yet — config loaded from local JSON file.

### Implementation Tasks

- [ ] Go module `gateway/` with `cmd/gateway/main.go`
- [ ] `RuntimeConfig` struct and JSON loader from file
- [ ] Route matcher: host + longest prefix + method
- [ ] Reverse proxy to single upstream URL (`httputil.ReverseProxy`)
- [ ] Request ID middleware (`X-Request-ID`)
- [ ] `/healthz`, `/readyz` endpoints
- [ ] Mock upstream container in docker-compose (`upstream-v1`)
- [ ] Gateway config file example in `deploy/config/example-v1.json`

### Components/Files Affected

- `gateway/internal/config/`
- `gateway/internal/router/`
- `gateway/internal/proxy/`
- `gateway/internal/middleware/requestid.go`
- `deploy/config/example-v1.json`
- `docker-compose.yml`

### Tests

- [ ] Unit: route matching table tests
- [ ] Integration: curl through gateway → upstream returns 200
- [ ] Integration: unknown path → 404

### Definition of Done

- `docker compose up` → gateway on `:8443` proxies `GET /v1/orders` to mock upstream
- Request ID present in upstream logs
- No PostgreSQL or Redis required

---

## Phase 2: Immutable Configuration and Atomic Activation

Phase 2 is subdivided into reviewable slices (2A–2C).

### Phase 2A — Control-Plane Configuration Compilation

#### Goal

Minimal control plane compiles draft configuration into immutable published versions.

#### Implementation Tasks

- [ ] FastAPI app `control-plane/` with SQLAlchemy models (projects, apis, routes, pools, targets, config_versions)
- [ ] Alembic migrations (`apis.enabled`, `apis.desired_config_version`)
- [ ] CRUD endpoints for projects, apis, routes, pools, targets
- [ ] `POST /apis/{id}/config/validate`
- [ ] `POST /apis/{id}/config/versions` — compile draft → immutable snapshot + monotonic version
- [ ] `GET /apis/{id}/config/versions` and `GET /apis/{id}/config/versions/{version}` (metadata)
- [ ] Snapshot serializer: draft tables → `RuntimeConfig` JSON + SHA-256 hash

#### Components/Files Affected

- `control-plane/app/` (models, routers, services, schemas)
- `control-plane/alembic/`

#### Tests

- [ ] Unit: snapshot serializer and validator
- [ ] Integration: create version returns monotonic version number and content_hash

#### Definition of Done

- Operator can validate and create config versions via REST
- Versions are INSERT-only; no gateway integration yet

---

### Phase 2B — Gateway Polling and Atomic Activation

#### Goal

Single gateway polls control plane, validates snapshot, atomically activates with request-scoped config capture.

#### Implementation Tasks

- [ ] `GET /gateway-config/{api_id}/desired` and `GET /gateway-config/{api_id}/versions/{version}`
- [ ] Gateway poller with ETag / `If-None-Match`
- [ ] Gateway `atomic.Pointer[RuntimeConfig]` — build and validate before `Store()`
- [ ] Request handler: `cfg := Load()` once; pass through context to all stages
- [ ] `POST /config/versions/{version}/activate` (operator activates; gateway picks up on poll)
- [ ] Remove file-based config from Phase 1

#### Components/Files Affected

- `gateway/internal/config/poller.go`
- `gateway/internal/config/activate.go`
- `gateway/internal/controlplane/client.go`
- `control-plane/app/routers/gateway_config.py`

#### Tests

- [ ] Unit: concurrent Load during Store (no torn reads within request)
- [ ] Integration: activate v1 → gateway serves routes from v1

#### Definition of Done

- Gateway activates config from control plane without restart
- Each request uses one consistent snapshot for all policy stages

---

### Phase 2C — ACK/NACK and Single-Gateway Convergence

#### Goal

Gateway reports activation results; control plane tracks current status and event history; convergence API works for one gateway.

#### Implementation Tasks

- [ ] `POST /gateways/register`
- [ ] `POST /gateways/{id}/config-status` (ACK/NACK)
- [ ] `gateway_api_status` table (current row per gateway+api)
- [ ] `config_activation_events` table (append-only history)
- [ ] `POST /config/versions/{version}/activate` with `expected_desired_version` → 409 on conflict
- [ ] `GET /apis/{id}/convergence` with derived operational state
- [ ] `operational_events` for `config_version_created`, `config_version_activated`

#### Components/Files Affected

- `control-plane/app/models/gateway_api_status.py`
- `control-plane/app/models/config_activation_events.py`
- `control-plane/app/services/convergence.py`
- `gateway/internal/controlplane/status.go`

#### Tests

- [ ] Integration: invalid config version → NACK → prior version still serves
- [ ] Integration: activate with stale `expected_desired_version` → 409
- [ ] Integration: convergence shows CONVERGED for single gateway

#### Definition of Done

- Single gateway ACK/NACK recorded in current status + event history
- Invalid version does not break traffic
- Derived convergence state accurate for one gateway

---

## Phase 3: Multiple Gateways and Configuration Convergence

### Goal

Two gateway instances converge to same published version; convergence API reflects partial and full convergence.

### Implementation Tasks

- [ ] Second gateway service in docker-compose (`gateway-a`, `gateway-b`)
- [ ] Gateway registration with unique credentials
- [ ] Heartbeat reporter goroutine
- [ ] `gateway_api_status` updates on ACK/NACK; append `config_activation_events`
- [ ] Stale gateway detection (heartbeat age > 3× interval)
- [ ] `GET /apis/{id}/convergence` implementation
- [ ] Poll interval jitter

### Components/Files Affected

- `docker-compose.yml` (gateway-a, gateway-b)
- `control-plane/app/services/convergence.py`
- `control-plane/app/services/gateway_status.py`
- `gateway/internal/controlplane/heartbeat.go`

### Tests

- [ ] Integration: create v2 + activate → both gateways ACK within 2 poll intervals
- [ ] Integration: stop gateway-b → marked stale; convergence excludes stale
- [ ] Integration: gateway-b restarts → jumps to current desired version (missed v2 while down, desired v3)

### Definition of Done

- Two gateways serve identical routing after publication
- Convergence dashboard/API accurate with one gateway offline

---

## Phase 4: API Authentication and Distributed Rate Limiting

### Goal

API-key auth enforced at gateway; global rate limit shared across two gateways via Redis.

### Implementation Tasks

- [ ] `api_keys` table (`key_id`, `key_prefix`, `secret_digest`) and management endpoints
- [ ] Snapshot includes minimum key validation data keyed by `key_id`
- [ ] Gateway auth: parse `ak_live_<key_id>.<secret>`; HMAC-SHA-256 with env pepper; constant-time compare
- [ ] Redis in docker-compose
- [ ] Atomic Lua fixed-window rate limit script (not separate INCR+EXPIRE)
- [ ] Key shape: `ratelimit:{api_id}:{policy_id}:{identity_hash}:{window_start}`
- [ ] Per-policy `redis_failure_mode` (default `fail_open` for portfolio MVP)
- [ ] Quota exceeded → 429; Redis down + fail_closed → 503; Redis down + fail_open → allow + bypass metric
- [ ] Metrics: `rate_limit_exceeded_total`, `rate_limit_redis_errors_total`, `rate_limit_redis_bypass_total`

### Components/Files Affected

- `control-plane/app/models/api_keys.py`
- `control-plane/app/models/rate_limit_policies.py`
- `gateway/internal/middleware/auth.go`
- `gateway/internal/middleware/ratelimit.go`
- `gateway/internal/redis/client.go`

### Tests

- [ ] Unit: auth middleware rejects missing/invalid key
- [ ] Integration: 101st request in window → 429
- [ ] Integration: 50 req each on 2 gateways → ~100 total allowed, not 200
- [ ] Integration: Redis stopped → fail-open or fail-closed per config (both tested)

### Definition of Done

- Unauthorized requests return 401
- Rate limit globally enforced across gateways
- Redis failure behavior documented and verified

---

## Phase 5: Health-Aware Load Balancing

### Goal

Multiple targets per pool; unhealthy targets removed from rotation after consecutive failures.

### Implementation Tasks

- [ ] Multiple targets in pool (docker-compose: `upstream-v1-a`, `upstream-v1-b`)
- [ ] Gateway passive health tracker: HEALTHY → UNHEALTHY → HALF_OPEN state machine
- [ ] Health-affecting failures: connection refused/reset, transport error, timeout, 502, 503, 504 (not all 500)
- [ ] Mark UNHEALTHY after N consecutive health-affecting failures
- [ ] HALF_OPEN recovery: cooldown → single success → HEALTHY
- [ ] Round-robin among healthy targets only
- [ ] 503 when all targets unhealthy

### Components/Files Affected

- `gateway/internal/upstream/health.go`
- `gateway/internal/upstream/selector.go`
- `docker-compose.yml` (multiple upstreams)

### Tests

- [ ] Integration: kill one upstream → traffic flows to remaining
- [ ] Integration: kill all upstreams → 503
- [ ] Integration: recover upstream → traffic resumes

### Definition of Done

- Failed backend stops receiving new requests within failure threshold
- No control-plane involvement in health state

---

## Phase 6: Timeout and Retry Policy

### Goal

Outbound request deadlines enforced; bounded retries on eligible GET failures.

### Implementation Tasks

- [ ] Route-level `timeout_ms` in snapshot
- [ ] Context deadline on outbound request
- [ ] `504 Gateway Timeout` on expiry
- [ ] `retry_policies` table and snapshot inclusion
- [ ] Custom outbound execution layer (or custom `RoundTripper`) — not `ReverseProxy.ServeHTTP` alone for retries
- [ ] Attempt loop: deadline → pool → backend → RoundTrip → classify → retry if eligible
- [ ] No retry on POST body requests (default)

### Components/Files Affected

- `gateway/internal/middleware/timeout.go`
- `gateway/internal/middleware/retry.go`
- `control-plane/app/models/retry_policies.py`

### Tests

- [ ] Integration: slow upstream (> timeout) → 504, connection cancelled
- [ ] Integration: upstream 503 on GET → retried up to max_attempts
- [ ] Integration: upstream 503 on POST → no retry

### Definition of Done

- Timeouts prevent hung goroutines (verify with slow upstream test)
- Retries safe for idempotent methods only

---

## Phase 7: Canary Traffic Management

### Goal

Deterministic 90/10 traffic split between two upstream pools; same API key always hits same pool.

### Implementation Tasks

- [ ] `traffic_splits` and `traffic_split_targets` tables
- [ ] Route policy binding for traffic split
- [ ] Gateway consistent hash on API-key ID
- [ ] Two pools: v1 (90%), v2 (10%) in docker-compose
- [ ] Metrics label: `upstream_pool` on requests

### Components/Files Affected

- `control-plane/app/models/traffic_splits.py`
- `gateway/internal/middleware/trafficsplit.go`
- `docker-compose.yml` (upstream-v2)

### Tests

- [ ] Unit: hash distribution ~90/10 over 10000 keys (± tolerance)
- [ ] Integration: same API key → same upstream pool across 100 requests
- [ ] Integration: different keys → both pools receive traffic

### Definition of Done

- Canary routing deterministic and versioned in config snapshot
- Changing split weights and republishing changes mapping predictably

---

## Phase 8: Observability and Failure Demonstrations

### Goal

Prometheus metrics, structured logs, and scripted failure demos for portfolio/interview use.

### Implementation Tasks

- [ ] Prometheus `/metrics`: `counter.Inc()`, `histogram.Observe()`, `gauge.Set()` (in-process; scraped later)
- [ ] Bounded async buffer for structured operational events; drop + `telemetry_events_dropped_total` when full
- [ ] `operational_events` on version create/activate; activation detail in `config_activation_events`
- [ ] Scripts in `deploy/demo/`:
  - `demo-control-plane-outage.sh`
  - `demo-bad-config-nack.sh`
  - `demo-redis-failure.sh`
  - `demo-rollback.sh` (activate prior version)
- [ ] Grafana dashboard JSON optional

### Components/Files Affected

- `gateway/internal/telemetry/`
- `control-plane/app/telemetry/`
- `deploy/demo/*.sh`
- `docker-compose.yml` (prometheus)

### Tests

- [ ] Verify metrics increment under load test (hey/wrk)
- [ ] Demo scripts exit 0 with expected behavior documented

### Definition of Done

- Each failure scenario in `DISTRIBUTED_SYSTEMS.md` has a runnable demonstration
- Metrics scrape works; request path not blocked by slow Prometheus scrape or full event buffer

---

## Phase 9: AWS Deployment and Hardening

### Goal

Deploy control plane, gateway fleet, RDS, and ElastiCache to AWS (single region); document runbook.

### Implementation Tasks

- [ ] Terraform or CDK modules (minimal): VPC, RDS PostgreSQL, ElastiCache Redis, EC2/ECS for gateway and control plane
- [ ] **ALB** in front of gateway fleet (TLS termination, health checks, distribution)
- [ ] NLB documented as optional future alternative for L4 pass-through
- [ ] Secrets in AWS Secrets Manager (DB URL, gateway tokens)
- [ ] Control plane behind ALB with HTTPS
- [ ] Environment-specific config
- [ ] Runbook: create version, activate, rollback (activate prior), gateway replacement, Redis failover behavior
- [ ] Security pass: TLS termination, security groups, no public RDS

### Components/Files Affected

- `deploy/aws/` (Terraform)
- `deploy/runbook.md`
- CI: deploy workflow optional

### Tests

- [ ] Smoke test against AWS deployment: create version, activate, proxy, rate limit
- [ ] Control plane stop → gateway continues serving

### Definition of Done

- End-to-end flow works in AWS single-region
- Runbook covers failure scenarios from Phase 8
- MVP feature-complete per `PRODUCT_SPEC.md`

---

## Roadmap Summary

| Phase | Capability | Depends on |
|-------|-----------|------------|
| 0 | Legacy freeze, scaffold | — |
| 1 | Single gateway proxy | 0 |
| 2A | Config compilation (control plane) | 1 |
| 2B | Gateway polling + atomic activation | 2A |
| 2C | ACK/NACK + single-gateway convergence | 2B |
| 3 | Multi-gateway convergence | 2C |
| 4 | Auth + distributed rate limit | 3 |
| 5 | Health-aware LB | 3 |
| 6 | Timeout + retry | 3 |
| 7 | Canary traffic split | 4, 5 |
| 8 | Observability + demos | 4–7 |
| 9 | AWS deployment | 8 |

Phases 5 and 6 can parallelize after Phase 4. Phase 7 requires Phase 5 (multiple pools/targets).

Estimated full roadmap (Phases 0–9): achievable incrementally; Phase 9 optional for local-only completion.

---

# First Resume-Ready Milestone

AutoAPI is ready to appear on a resume after completing:

- **Phase 1** — single-gateway L7 proxy vertical slice
- **Phase 2A** — control-plane configuration compilation
- **Phase 2B** — gateway polling and `atomic.Pointer[RuntimeConfig]` activation with request-scoped config
- **Phase 2C** — ACK/NACK, gateway status tables, single-gateway convergence
- **Phase 3** — multiple gateways and configuration convergence
- **Phase 4** — API-key authentication and Redis Lua fixed-window rate limiting
- **Basic Prometheus metrics** — in-process counters/histograms on gateway and control plane
- **At least one automated failure demonstration** — e.g. bad config NACK, control-plane outage, or Redis fail_open bypass

Health-aware routing (Phase 5), timeout/retry policy (Phase 6), and canary traffic splitting (Phase 7) are strong later improvements but **not required** before AutoAPI first demonstrates distributed API runtime and traffic-management engineering on a resume.

Phases 8–9 add observability depth, additional failure demos, and AWS deployment hardening beyond this milestone.
