# AutoAPI Architecture

This document describes the **proposed renovated architecture**. Legacy behavior is documented separately in `LEGACY_ANALYSIS.md`.

---

## System Overview

```text
Developer / Operator
        |
        v
+------------------+
| FastAPI          |
| Control Plane    |
| (Management)     |
+--------+---------+
         |
         v
+------------------+
| PostgreSQL       |
| (Authoritative   |
|  mgmt state)     |
+--------+---------+
         |
         |  HTTP: config metadata, snapshots,
         |        registration, heartbeat, ACK/NACK
         v
+--------+---------+
| Config           |
| Distribution     |  <-- MVP: embedded in control plane
| (HTTP endpoints) |
+--------+---------+
         |
    +----+----+
    |         |
    v         v
+-------+ +-------+
| Go    | | Go    |
|Gateway| |Gateway|
|   A   | |   B   |
+---+---+ +---+---+
    |         |
    +----+----+
         |
         v
    +---------+
    | Redis   |
    | (global |
    |  rate   |
    |  limit) |
    +---------+
         |
    +----+----+
    |         |
    v         v
+-------+ +-------+
| API   | | API   |
| v1    | | v2    |
| Pool  | | Pool  |
+-------+ +-------+
```

---

## Component Specifications

### 1. FastAPI Control Plane

| Attribute | Definition |
|-----------|------------|
| **Responsibility** | CRUD for projects, APIs, routes, policies; config validation; immutable publication; gateway membership; convergence reporting; operator REST API |
| **Owned state** | Authoritative management records in PostgreSQL; no runtime request state |
| **APIs** | `/api/v1/*` management REST (see `API_SPEC.md`) |
| **Dependencies** | PostgreSQL; optional Redis for operator rate limits (not MVP) |
| **Failure behavior** | Returns 503 on DB unavailable; does not affect gateways serving last active config; read endpoints may degrade; write/publication fails closed |

**Does not:** proxy client traffic, enforce rate limits on API consumers, participate in request path.

### 2. PostgreSQL

| Attribute | Definition |
|-----------|------------|
| **Responsibility** | Durable storage for all management entities, config version history, gateway records, ACK/NACK status, operational events |
| **Owned state** | Users, projects, APIs, routes, policies, `config_versions` snapshots, `gateways`, `gateway_api_status`, `config_activation_events`, `operational_events` |
| **APIs** | SQL via control plane ORM (SQLAlchemy/Alembic) |
| **Dependencies** | None (data store) |
| **Failure behavior** | Control plane unhealthy; gateways unaffected on hot path |

### 3. Configuration Distribution (MVP: Control Plane Embedded)

| Attribute | Definition |
|-----------|------------|
| **Responsibility** | Gateway registration; heartbeat ingestion; desired-version metadata; full snapshot delivery; ACK/NACK collection |
| **Owned state** | None durable beyond what is written to PostgreSQL; ephemeral request handling only |
| **APIs** | Gateway-facing HTTP under `/api/v1/gateway-config/*` and `/api/v1/gateways/*` (see `API_SPEC.md`) |
| **Dependencies** | PostgreSQL for snapshots and status |
| **Failure behavior** | Gateways continue on active config; poll failures logged; retry with backoff; stale if control plane down extended period |

**MVP protocol:** HTTP conditional polling with version number and content hash/ETag. No gRPC streaming, no custom binary protocol, no delta patches.

**Publication and activation flow:**

```text
Operator → POST /apis/{id}/config/versions
         → Control plane validates draft graph
         → Serializes immutable RuntimeConfig JSON
         → Computes content_hash (SHA-256)
         → INSERT config_versions (version++ monotonically)
         → Returns version metadata

Operator → POST /apis/{id}/config/versions/{version}/activate
         → Optimistic concurrency on expected_desired_version
         → UPDATE apis.desired_config_version (may move forward or backward)
         → Immutable snapshots are never modified

Gateway  → GET /gateway-config/{api_id}/desired (If-None-Match: hash)
         → 304 if unchanged
         → GET /gateway-config/{api_id}/versions/{version} if version advanced
         → Local validate → build RuntimeConfig struct
         → atomic.Pointer[RuntimeConfig].Store(newConfig)
         → POST /gateways/{id}/config-status { ack|nack, version, hash }
```

Published configuration version numbers increase monotonically. `desired_config_version` is an authoritative **mutable pointer** that may reference any existing published version (including rollback to an earlier version).

### 4. Go Gateway (Data Plane)

| Attribute | Definition |
|-----------|------------|
| **Responsibility** | L7 reverse proxy; route matching; API-key auth; distributed rate limit; traffic split; backend selection; timeouts/retries; request IDs; metrics |
| **Owned state** | Active local `RuntimeConfig` (immutable) via `atomic.Pointer[RuntimeConfig]`; passive backend health observations (in-memory); Prometheus counters/histograms in-process |
| **APIs** | Inbound: client HTTP. Outbound: upstream HTTP, control-plane poll/ACK, Redis rate-limit |
| **Dependencies** | Control plane (config only, async); Redis (rate limit); upstream backends |
| **Failure behavior** | See `DISTRIBUTED_SYSTEMS.md` per mechanism |

**Internal structure (conceptual):**

```text
net/http Server
  └── Handler entry:
        cfg := activeConfig.Load()   // once per request; passed through context
        RequestID(cfg) → RouteMatch(cfg) → Auth(cfg) → RateLimit(cfg)
        → HealthAwareTargetSelect(cfg) → OutboundProxy → PassiveHealthUpdate
        → Telemetry
  └── activeConfig atomic.Pointer[RuntimeConfig]
  └── configPoller goroutine
  └── heartbeatReporter goroutine
```

**Request-scoped configuration:** Every request calls `Load()` exactly once at handler entry. The returned `*RuntimeConfig` pointer is stored in request context and passed to all stages. This prevents one request from observing route policy from version N and authentication or rate-limit policy from version N+1 during an atomic swap.

**Atomic pointer rules:**

- Configuration is fully built and validated **before** `Store()`.
- The active `RuntimeConfig` is **immutable** after construction.
- Request handlers call `Load()` once per request.
- `Store()` is the sole activation mechanism.
- No in-place mutation of the active configuration object.

### 5. Redis

| Attribute | Definition |
|-----------|------------|
| **Responsibility** | Centrally coordinated cross-gateway fixed-window rate limiting |
| **Owned state** | Rate-limit keys with TTL aligned to window boundaries |
| **APIs** | Redis protocol; **atomic Lua script** (MVP — not INCR+EXPIRE as separate commands) |
| **Dependencies** | None |
| **Failure behavior** | See Redis failure semantics below; never return 429 for Redis dependency failure |

**MVP algorithm:** Redis-backed fixed-window rate limiting implemented with an atomic Lua script. The script atomically:

1. Increments the counter
2. Assigns TTL when the counter is first created
3. Returns the current count and remaining TTL

**Key shape:**

```text
ratelimit:{api_id}:{policy_id}:{identity_hash}:{window_start}
```

**Limitations:** Fixed-window boundary bursts; Redis round-trip on rate-limited requests; hot-key risk under concentrated identity; Redis availability dependency. This is centrally coordinated cross-gateway fixed-window rate limiting — not universally or perfectly "globally accurate" under all traffic shapes.

**Redis failure semantics (identical across all docs):**

```text
Redis available and quota exceeded:     HTTP 429
Redis unavailable and policy fail_open: allow request; structured warning; bypass metric
Redis unavailable and policy fail_closed: HTTP 503; structured warning; dependency error metric
```

Portfolio MVP default: `fail_open` on each rate-limit policy (availability-first, bypass explicitly surfaced). Each policy stores and publishes its selected `redis_failure_mode`.

### 6. Telemetry

| Attribute | Definition |
|-----------|------------|
| **Responsibility** | Request metrics (Prometheus); structured operational event export |
| **Owned state** | Prometheus time-series (external, scraped); bounded in-memory event buffer |
| **APIs** | Prometheus scrape `/metrics` on gateway and control plane |
| **Dependencies** | None for collection; optional Prometheus server |
| **Failure behavior** | See Telemetry section below |

**Prometheus (in-process):** Standard instrumentation — `counter.Inc()`, `histogram.Observe()`, `gauge.Set()`. Metrics are updated in-process and scraped later from `/metrics`. Do **not** treat Prometheus counters/histograms as an asynchronous export queue that drops samples.

**Structured operational events (async, bounded):** Gateway and control plane emit audit/activation events through a bounded in-memory channel. The request path never blocks on event export. When the buffer is full, events are dropped, `telemetry_events_dropped_total` increments, and an internal warning is emitted at rate-limited frequency.

---

## External Request Path

Every request loads configuration once, then passes the same snapshot through all stages:

```text
Client
  │
  │  HTTP request (Host, Path, Method, headers, body)
  v
Gateway Listener
  │
  ├─► cfg := activeConfig.Load()     // once per request; stored in context
  │
  ├─► Request ID (cfg)
  │     Generate UUID if absent; set X-Request-ID on upstream request
  │
  ├─► Route Match (cfg)
  │     Match host + longest path prefix + method
  │     No match → 404
  │
  ├─► Authentication (cfg)
  │     Parse ak_live_<key_id>.<secret> from X-API-Key
  │     O(1) lookup by key_id; HMAC-SHA-256 with gateway pepper; constant-time compare
  │     Invalid/missing/revoked/expired → 401
  │
  ├─► Distributed Rate Limit (cfg)
  │     Redis Lua fixed-window: ratelimit:{api_id}:{policy_id}:{identity_hash}:{window_start}
  │     Quota exceeded (Redis up) → 429 + Retry-After
  │     Redis down + fail_closed → 503; Redis down + fail_open → allow + bypass metric
  │
  ├─► Traffic Split (cfg)
  │     Deterministic hash on stable identity (key_id or configured header)
  │     Select upstream pool
  │
  ├─► Backend Selection (cfg)
  │     Health-aware selection within pool (passive local health state)
  │     No healthy backend → 503
  │
  ├─► Outbound Execution (cfg)
  │     See Timeout/Retry flow below
  │
  ├─► Telemetry
  │     Prometheus in-process; operational events via bounded async buffer
  │
  v
Client Response
```

Loading `cfg` once prevents torn reads across policy stages during concurrent `Store()` operations.

### Timeout and Retry Execution Flow (Phase 6 — implemented in Java gateway)

Outbound attempts are executed by `RetryingProxyExecutor` / `UpstreamAttemptExecutor` (WebFlux WebClient), not unbounded Reactor `retry()`. **`maxAttempts` includes the first attempt.**

```text
route matched + auth/rate-limit passed
        |
        v
retry policy present? ----no----> single attempt (health-aware RR)
        |
       yes
        v
buffer body if needed (bounded)
        |
        v
attempt loop (maxAttempts)
        |
        v
select target (prefer unattempted on retry)
        |
        v
per-attempt timeout + WebClient exchange
        |
        v
classify result
        |
        +---- usable upstream response ----> stream to client (terminal)
        |
        +---- retryable transport failure ----+
        |   method/idempotency/budget OK?     |
        +-------------------------------------+
        |
        v
terminal 502/504 to client
```

### Traffic Split Selection Flow (Phase 7 — implemented in Java gateway)

Traffic-split assignment runs **after** authentication and rate limiting and **before** health-aware target selection. The gateway captures the active immutable runtime configuration once per request.

```text
route matched
        |
        v
authentication (required for API_KEY_ID stickiness)
        |
        v
rate limiting
        |
        v
resolve selection key (API key ID / header / cookie / request ID)
        |
        v
SHA-256 bucket = hash(routeId|policyId|fingerprint|key) mod totalWeight
        |
        v
nominal destination (weighted cumulative ranges)
        |
        v
split-level fallback if pool has no eligible target
        |
        v
health-aware target selection inside effective pool
        |
        v
bounded retries (same effective split; no re-hash on retry)
```

Hash input excludes gateway ID, raw API-key secrets, and per-request randomness. Policy fingerprint changes (weights, destinations, selection key, fallback mode) can remap sticky assignments.

---

Retries are **transport-only** in Phase 6 (no HTTP 5xx retries). POST/PATCH require valid `Idempotency-Key` when policy allows those methods. Retry budgets are gateway-local sliding windows keyed by `apiId + routeId + policyId`.

---

## Configuration Publication and Gateway Activation

### Immutable Configuration

Each publication produces a complete `RuntimeConfig` document:

```json
{
  "api_id": "...",
  "version": 43,
  "content_hash": "sha256:...",
  "routes": [...],
  "upstream_pools": {...},
  "api_keys": {...},
  "rate_limit_policies": {...},
  "traffic_splits": {...},
  "retry_policies": {...}
}
```

Stored in `config_versions.config_snapshot` (JSONB). Never mutated after insert.

### Atomic Activation (Gateway)

```text
1. Poller receives snapshot v43
2. Parse + validate schema (required fields, URL syntax, no orphan refs)
3. Build new *RuntimeConfig in memory (not visible to request handlers)
4. activeConfig.Store(newConfig)   // atomic.Pointer[RuntimeConfig]
5. Each request: cfg := Load() once at entry; all stages use same cfg
6. POST config-status ACK { version: 43, hash: "..." }
```

On validation failure: POST NACK with bounded diagnostic; **do not Store**; continue serving previous configuration.

### API-Key Validation (Gateway Hot Path)

Format: `ak_live_<key_id>.<secret>`

```text
1. Parse key_id from presented key
2. O(1) lookup by key_id in active runtime config snapshot
3. Compute HMAC-SHA-256 using gateway-side pepper + presented secret
4. Constant-time compare against secret_digest in snapshot
5. Reject revoked, expired, unknown, or mismatched keys
```

The gateway pepper is supplied via environment or secret management (AWS Secrets Manager in Phase 9). It is **never** stored in the published runtime configuration or PostgreSQL snapshot. PostgreSQL stores `secret_digest` (same HMAC construction at key creation). **Do not use bcrypt on the gateway hot path.**

API keys are scoped to an API. Policy enforcement (rate limits, splits) comes from route bindings in the snapshot.

### Passive Backend Health (MVP)

Health-affecting failures: connection refused, connection reset, transport error, request timeout, HTTP 502, 503, 504. **Do not** treat every HTTP 500 as automatic evidence of backend unhealthiness.

Local state machine per target (per gateway):

```text
HEALTHY
   |
   | failure threshold reached
   v
UNHEALTHY
   |
   | cooldown elapsed
   v
HALF_OPEN
   | success             | failure
   v                     v
HEALTHY              UNHEALTHY
```

Passive health is **local to each gateway** in MVP. Different gateways may temporarily disagree about target health.

---

## Derived API Operational State

Store on `apis`:

- `enabled` (boolean) — administrative disable
- `desired_config_version` (integer) — authoritative mutable pointer to a published version

Derive operational state at read time (convergence API):

| State | Condition |
|-------|-----------|
| `UNPUBLISHED` | No published configuration exists |
| `DISABLED` | `enabled = false` |
| `CONVERGING` | Live gateways exist; not all ACK'd desired; none NACK'd |
| `CONVERGED` | All live gateways ACK desired version |
| `DEGRADED` | One or more live gateways NACK desired version |

Do not persist derived state as an authoritative mutable column on `apis`.

---

### Why PostgreSQL Must Not Be on the Hot Request Path

| Concern | With DB on hot path | With local immutable config |
|---------|--------------------|-----------------------------|
| **Latency** | +1–5 ms (or worse) per request for route/key lookup | Memory lookup, nanoseconds |
| **Availability** | Gateway degrades when DB down | Gateway serves last ACK'd config independently |
| **Consistency** | Partial reads mid-transaction possible | Request-scoped snapshot from single Load() |
| **Scale** | Connection pool contention at high RPS | Horizontal gateway scale without DB fan-out |
| **Blast radius** | DB incident = total API outage | Control-plane incident = stale config, not dropped traffic |

Legacy AutoAPI implicitly delegated runtime to cloud vendor gateways (`CloudServices/AWS/ApiGateway/ApiGatewayHelper.js`). The renovated architecture makes this separation explicit with a self-hosted data plane.

---

## Management Plane vs Data Plane

| Plane | Processes | Traffic | State access |
|-------|-----------|---------|--------------|
| **Management** | FastAPI control plane | Operator REST | PostgreSQL read/write |
| **Data** | Go gateway | Client API HTTP | Local active config + Redis |

Gateways never expose management CRUD. Control plane never proxies client API traffic.

---

## Deployment Topology (MVP)

```text
docker-compose:
  postgres:5432
  redis:6379
  control-plane:8080   (FastAPI + config distribution)
  gateway-a:8443
  gateway-b:8444
  upstream-v1:9001     (mock backend)
  upstream-v2:9002     (mock backend)
```

Post-MVP AWS (recommended MVP ingress):

```text
Internet
   |
   v
AWS Application Load Balancer
   |
   +---- AutoAPI Gateway A
   |
   +---- AutoAPI Gateway B
```

The ALB handles public ingress, TLS termination, gateway fleet health checks, and distribution across gateway instances. AutoAPI gateways still own L7 route matching, API-key authentication, distributed rate limiting, canary routing, backend selection, timeout/retry policy, and upstream traffic management.

Control plane: ECS/Fargate or EC2 behind separate ALB; RDS PostgreSQL; ElastiCache Redis.

**NLB** is an optional future alternative if AutoAPI later needs direct L4 pass-through or gateway-owned TLS termination.

---

## Security Boundaries

- **Operator → Control plane:** authenticated (post-MVP JWT); MVP may use local dev token
- **Gateway → Control plane:** gateway credential (registration token) for poll/ACK/heartbeat
- **Client → Gateway:** API key per consumer
- **Gateway → Upstream:** plain HTTP MVP; mTLS future
- **Secrets:** API key `secret_digest` in PostgreSQL; gateway pepper via env/secrets manager; plaintext key shown once at creation

---

## Technology Choices

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Control plane | Python/FastAPI | Fast CRUD development, validation with Pydantic |
| Data plane | Go | Performance, stdlib HTTP proxy, static binary deployment |
| Management DB | PostgreSQL | Relational integrity, JSONB snapshots, mature ops |
| Rate limit store | Redis | Atomic ops, TTL, low latency |
| Config transport | HTTP polling + ETag | Simple, debuggable, no streaming infra for MVP |
| Metrics | Prometheus | Industry standard, pull-based, nonblocking scrape |

Intentionally excluded from MVP: Kubernetes, Kafka, gRPC, Envoy, Istio, custom consensus.
