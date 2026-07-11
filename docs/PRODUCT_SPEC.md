# AutoAPI Product Specification

## Product Definition

AutoAPI is a **distributed API runtime and traffic-management platform**. Developers register APIs, upstream services, routes, and runtime policies through a management control plane. The control plane validates configuration, publishes immutable versioned runtime snapshots, and distributes them to gateway nodes. Distributed Go gateway nodes validate, atomically activate, and enforce L7 traffic policy — routing, authentication, rate limiting, traffic splitting, timeouts, and retries — on live HTTP requests without querying the management database per request.

## GitHub Description

> Distributed L7 API gateway platform with versioned config propagation, multi-node traffic policy, and global rate limiting.

---

## Target Users

| User | Need |
|------|------|
| **Backend engineers** | Expose services through a managed gateway with auth and rate limits without building proxy infrastructure |
| **Platform engineers** | Operate a gateway fleet with configuration convergence visibility and rollback |
| **Operators / SREs** | Understand gateway health, config drift, and traffic policy behavior under failure |
| **Portfolio reviewers** | Evaluate distributed systems and L7 networking engineering depth |

Primary MVP user: a single developer operating 1–3 gateway nodes and a handful of APIs (self-hosted or small AWS deployment).

---

## Problem Statement

Teams operating multiple API gateway instances face:

1. **Inconsistent traffic policy** — partial config updates cause different nodes to enforce different routes or rate limits.
2. **No unified L7 control** — auth, routing, splitting, and retries are scattered across app code, load balancers, and vendor gateways.
3. **Per-node rate limits** — independent counters multiply effective quota by node count.
4. **Unsafe deployment** — publishing config without gateway ACK/NACK leaves operators blind to activation failures.
5. **Control-plane coupling** — querying a database on every request creates availability and latency dependencies.

Legacy AutoAPI attempted to solve API **generation and cloud deployment** (`server-old/ApiGenerator/CloudServices/`). The renovated product solves API **runtime traffic management**.

---

## Goals

1. Manage the live L7 request path through a dedicated gateway data plane.
2. Publish immutable, versioned runtime configuration from a FastAPI control plane backed by PostgreSQL.
3. Distribute configuration to gateway nodes with ACK/NACK and convergence reporting.
4. Enforce API-key authentication and distributed rate limiting (Redis) across gateway nodes.
5. Support health-aware backend selection, timeouts, bounded retries, and deterministic canary routing.
6. Keep PostgreSQL off the hot request path.
7. Provide observable failure behavior for every distributed mechanism.
8. Deliver a feasible MVP for one developer using a focused stack (Python, Go, PostgreSQL, Redis).

---

## Non-Goals

AutoAPI is **not**:

| Exclusion | Rationale |
|-----------|-----------|
| **Generic infrastructure orchestrator** | Covered by Cloud Networking Studio; not the intellectual center |
| **Kubernetes dashboard** | No cluster UI or workload management |
| **Workflow engine** | No DAG/batch job orchestration |
| **Service mesh** | No sidecar injection, mTLS mesh, or xDS control plane |
| **Full Envoy replacement** | MVP gateway is intentionally scoped; not competing on full proxy feature parity |
| **Multi-cloud resource provisioner** | Legacy `CloudServices/` pattern abandoned |
| **API code generator** | Not contract/codegen platform (see Renovation Option 2) |
| **Social/collaboration platform** | Legacy User/Group/Messaging scope removed |
| **Billing and payments** | Legacy Stripe/PayPal scope removed |

Also excluded from MVP (may be post-MVP where noted):

- Raft/Paxos/Kafka/multi-region consensus
- gRPC streaming for config distribution
- Exactly-once request processing
- Custom deployment worker leases

---

## Core User Workflow

```text
1. Create a project
2. Register an API (host, base path)
3. Define upstream pool and backend targets (URLs, weights)
4. Define routes (host, path pattern, HTTP methods → upstream pool)
5. Attach policies: API keys, rate limits, retry rules, traffic split
6. Validate configuration (`POST /config/validate`)
7. Create immutable configuration version N (`POST /config/versions`)
8. Activate version N (`POST /config/versions/{N}/activate`) — rollback activates a prior published version the same way
9. Gateways poll desired metadata, fetch snapshot, validate locally, atomically activate
10. Gateways ACK (success) or NACK (failure with diagnostic)
11. Operator views derived convergence state and per-gateway status
12. Live client traffic: single request-scoped cfg through route match → auth → rate limit → proxy → upstream
```

---

## MVP Requirements

### Control Plane (FastAPI + PostgreSQL)

- [ ] Projects, APIs, upstream pools, targets, routes CRUD
- [ ] API key management (create, revoke, bind to API)
- [ ] Rate-limit policy definition (requests per window)
- [ ] Configuration validation (`POST /config/validate`)
- [ ] Immutable configuration version creation (`POST /config/versions`); published version numbers increase monotonically
- [ ] Version activation and rollback (`POST /config/versions/{version}/activate`) with optimistic concurrency
- [ ] Configuration snapshot retrieval (`GET /gateway-config/{api_id}/versions/{version}`)
- [ ] Gateway desired metadata (`GET /gateway-config/{api_id}/desired`)
- [ ] Gateway registration, heartbeat, config-status (ACK/NACK)
- [ ] Derived convergence status API (`GET /apis/{id}/convergence`) — not a mutable lifecycle column
- [ ] Health and readiness endpoints

### Gateway (Go)

- [ ] HTTP reverse proxy
- [ ] Host, path, and method route matching
- [ ] Immutable active runtime config with `atomic.Pointer[RuntimeConfig]` swap
- [ ] Request-scoped config: `Load()` once per request; same snapshot through all stages
- [ ] Startup: fetch and activate config before accepting traffic (readiness)
- [ ] Configuration polling via `GET /gateway-config/{api_id}/desired` with ETag
- [ ] ACK/NACK via `POST /gateways/{id}/config-status`
- [ ] Periodic heartbeat
- [ ] API-key authentication (`ak_live_<key_id>.<secret>`); HMAC-SHA-256 with gateway pepper (not bcrypt)
- [ ] Redis fixed-window rate limiting via atomic Lua script
- [ ] Upstream pool selection and round-robin (health-unaware initially, then health-aware)
- [ ] Request ID generation (propagate or generate `X-Request-ID`)
- [ ] Request timeout enforcement with outbound cancellation
- [ ] Prometheus metrics (in-process counters/histograms); operational events via bounded async buffer

### Configuration Distribution

- [ ] Embedded in control-plane process (HTTP endpoints for gateways)
- [ ] Conditional polling (If-None-Match / version check)
- [ ] Full snapshot on version change (no delta replay required)

### Redis

- [ ] Global rate-limit counters via Redis Lua (key: `ratelimit:{api_id}:{policy_id}:{identity_hash}:{window_start}`)
- [ ] Per-policy `redis_failure_mode` (`fail_open` default for portfolio MVP):
  - Quota exceeded + Redis up → 429
  - Redis down + fail_open → allow + bypass metric
  - Redis down + fail_closed → 503

### Infrastructure

- [ ] Docker Compose for local dev (control plane, gateway, PostgreSQL, Redis)
- [ ] Integration tests for config version create → activate → gateway activation → proxied request

---

## Post-MVP Requirements

- Retry policies with eligibility rules (idempotent methods, no body replay)
- Retry budget headers to limit amplification
- Deterministic sticky canary routing (hash on API-key ID or configured header)
- Weighted traffic splitting across upstream pools
- Passive backend health (502/503/504, transport errors, timeouts — not all HTTP 500)
- Prometheus metrics export
- Operational events log in PostgreSQL
- Gateway stale detection (heartbeat age threshold)
- AWS deployment (single-region; ALB ingress for gateway fleet)
- Control-plane auth (operator JWT/API key)
- Rate-limit policy tiers per API key

---

## Future Requirements

- Multiple projects with RBAC
- mTLS between gateway and upstreams
- Request body size limits and WAF-lite rules
- OpenTelemetry tracing export
- Long-polling or SSE for config push (still no custom protocol)
- Separate config-distribution service (only if control-plane scale requires it)
- Multi-region gateway fleets with regional rate-limit namespaces
- Audit log for all configuration changes
- CLI for operator workflows

---

## Success Criteria

MVP is successful when:

1. Two gateway nodes converge to the same published config version and report ACK.
2. A rate limit configured as 100 req/min is centrally coordinated across both gateway nodes (fixed-window Lua), not ~200.
3. Control plane restart does not interrupt in-flight gateway traffic.
4. A bad config version is NACK'd by gateway; previous version continues serving.
5. Rollback via activate prior version converges all live gateways within one poll interval.
