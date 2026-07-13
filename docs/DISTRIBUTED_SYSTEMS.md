# AutoAPI Distributed Systems Design

Every mechanism below ties to a concrete failure scenario. Tradeoffs are explicit.

Mechanisms intentionally excluded from MVP are listed at the end.

---

## Versioned Configuration

### Failure scenario

Different gateways or different goroutines within one gateway observe partially updated routes and policies — e.g., route table updated before API keys, causing authenticated requests to hit wrong upstreams or bypass rate limits.

### Mechanism

Control plane publishes **immutable configuration snapshots** identified by `(api_id, version, content_hash)`. Each snapshot is a complete `RuntimeConfig` document stored in `config_versions.config_snapshot`. Version increments monotonically per API. Gateways always activate a full snapshot, never merge partial updates.

### Tradeoff

- **Cost:** Larger payloads on every version change; no incremental delta sync.
- **Benefit:** Snapshots are self-consistent; gateway validation is total; missed intermediate versions irrelevant (jump 40→43 directly).

---

## Atomic Configuration Activation

### Failure scenario

Requests observe partially mutated gateway configuration — e.g., route removed but old upstream pool pointer still referenced mid-update.

### Mechanism

Gateway builds and validates a complete in-memory `RuntimeConfig`, then performs a single atomic pointer swap via `atomic.Pointer[RuntimeConfig]` (Go). The active `RuntimeConfig` is immutable. Configuration is fully built and validated before `Store()`. No in-place mutation of the active configuration object.

**Request-scoped snapshot (required):** Request handlers call `activeConfig.Load()` exactly once at the beginning of each request. The loaded pointer is passed through request context to all stages:

```text
cfg := activeConfig.Load()
RouteMatch(cfg) → Authentication(cfg) → RateLimit(cfg) → TrafficSplit(cfg) → ...
```

This prevents one request from observing route policy from version N and authentication or rate-limit policy from version N+1 during activation.

### Tradeoff

- **Cost:** Brief memory duplication during swap; each request holds one pointer for its lifetime.
- **Benefit:** No torn reads within a request; all-or-nothing policy view per request.

---

## Eventual Gateway Convergence

### Failure scenario

After publication, operator assumes all gateways enforce new policy, but one gateway still runs old rate limits or routes.

### Mechanism

Each gateway polls `GET /gateway-config/{api_id}/desired` on interval with jitter. On version mismatch, fetches `GET /gateway-config/{api_id}/versions/{version}`, activates, reports via `POST /gateways/{id}/config-status`. Control plane derives convergence from `gateway_api_status` rows.

### Tradeoff

- **Cost:** Convergence delay bounded by poll interval (e.g., 5–30s MVP); not instantaneous.
- **Benefit:** Simple HTTP; no persistent streaming connections; survives gateway/control-plane clock skew with version numbers not timestamps.

---

## ACK/NACK

### Failure scenario

Control plane accepts and stores configuration that a gateway cannot construct or activate — invalid upstream URL, circular traffic split, schema violation — leaving operators unaware while assuming deployment succeeded.

### Mechanism

After local validate+activate attempt, gateway POSTs:

- **ACK:** `{ version, content_hash, activated_at }`
- **NACK:** `{ version, content_hash, error_code, error_detail }` (bounded detail, max 1 KB)

On NACK, gateway retains previous active version (does not `Store()`). Control plane upserts `gateway_api_status` (current row) and appends `config_activation_events` (history). Convergence API surfaces derived operational state.

### Tradeoff

- **Cost:** Operator must monitor NACKs; publication is "best effort distribute" not "globally committed."
- **Benefit:** Failed config never takes down working traffic; clear accountability per gateway.

---

## Missed Configuration Updates

### Failure scenario

Gateway offline during versions 41 and 42; reconnects when desired version is 43. Replaying 41 and 42 wastes bandwidth and could activate obsolete intermediate states if deltas were used.

### Mechanism

Full immutable snapshots per version. Gateway compares local `active_version` to `desired_version`; fetches latest snapshot only. No replay required. Versions 41–42 are historical audit records, not activation steps.

### Tradeoff

- **Cost:** Cannot express "incremental fix" efficiently; every publish is full document.
- **Benefit:** Correctness independent of offline duration; simple gateway logic.

---

## Gateway Restart

### Failure scenario

Gateway process restart loses in-memory active configuration. Gateway accepts traffic before config loaded, causing 404/502 for all requests, or loads stale partial state.

### Mechanism

**Startup sequence:**

1. Load gateway identity and credentials from env/config.
2. Register or re-register with control plane.
3. Fetch `GET /gateway-config/{api_id}/desired`; download full snapshot via `GET /gateway-config/{api_id}/versions/{version}` if needed.
4. Validate and activate before marking ready.
5. `/readyz` returns 503 until activation succeeds (or explicit fail: no prior config and cannot fetch).
6. Begin heartbeat and poll loops.

If control plane unreachable at startup and no local persisted config cache (MVP: fail startup; post-MVP: optional on-disk last-ACK snapshot).

### Tradeoff

- **Cost:** Gateway unavailable during bootstrap; no "serve immediately" without config.
- **Benefit:** Never serves with empty route table; readiness probes integrate with orchestrators.

---

## Distributed Rate Limiting

### Failure scenario

Three gateways each independently enforce 100 requests/minute and collectively allow ~300 requests/minute.

### Mechanism

Redis-backed **fixed-window** rate limiting implemented with an **atomic Lua script**. The script atomically:

1. Increments the counter
2. Assigns TTL when the counter is first created
3. Returns the current count and remaining TTL

Key shape:

```text
ratelimit:{api_id}:{policy_id}:{identity_hash}:{window_start}
```

This is **centrally coordinated cross-gateway fixed-window rate limiting**. All gateways share one counter per `(api_id, policy_id, identity_hash, window_start)`. Quota exceeded while Redis is available → HTTP 429 at any gateway.

### Tradeoff

- **Cost:** +1 Redis RTT per rate-limited request; fixed-window boundary bursts; hot-key risk under concentrated identity; Redis availability dependency.
- **Benefit:** Simple cross-gateway coordination without a custom protocol. Not universally or perfectly "globally accurate" under all traffic shapes (boundary effects, clock/window alignment).

---

## Redis Failure

### Failure scenario

Redis unavailable or slow. Gateways cannot increment rate-limit counters.

### Mechanism

Each rate-limit policy stores and publishes `redis_failure_mode`. Behavior is identical across all components:

```text
Redis available and quota exceeded:
    return HTTP 429

Redis unavailable and policy is fail_open:
    allow request
    emit structured warning
    increment bypass/error metric (e.g. rate_limit_redis_bypass_total)

Redis unavailable and policy is fail_closed:
    return HTTP 503
    emit structured warning
    increment dependency error metric (e.g. rate_limit_redis_errors_total)
```

**Do not** return HTTP 429 for Redis dependency failure.

Portfolio MVP default: **`fail_open`** per policy — availability is easier to demonstrate while bypass is explicitly surfaced in metrics and logs.

### Tradeoff

- **Cost:** Fail-open violates quota during Redis outage; fail-closed reduces availability.
- **Benefit:** Operator choice per policy; observable, testable behavior; no silent mis-enforcement.

---

## Deterministic Traffic Splitting

### Failure scenario

Random 90/10 canary routing causes one user to bounce between API v1 and v2 across requests, breaking session semantics and corrupting canary metrics.

### Mechanism

Hash stable identity (API-key ID or configured header such as `X-Canary-Identity`) with consistent hash ring or `hash(id) % 100 < weight`. Same identity always maps to same pool until split weights change (then remapping is explicit and versioned).

### Tradeoff

- **Cost:** Uneven distribution for small populations; remapping on weight change shifts some users.
- **Benefit:** Sticky canary behavior; reproducible debugging; fair per-identity experience.

---

## Backend Health

### Failure scenario

Failed backend continues receiving equal share of traffic; error rate stays high despite one healthy instance.

### Mechanism

Gateway passive health tracking per target (**local to each gateway instance** in Phase 5):

**Qualifying transport failures (Phase 5):** connection refused, connection reset, DNS failure, connect timeout, response timeout, premature upstream close before a usable response.

**Non-qualifying in Phase 5:** upstream HTTP 2xx/3xx/4xx/5xx (target was reachable), client disconnect, gateway auth/rate-limit rejection, route-not-found, control-plane polling failure, Redis failure. HTTP 5xx-based outlier detection is deferred.

**Local states:** `HEALTHY` and `EJECTED` (temporary). No half-open probes; recovery happens through real traffic after ejection expiry.

```text
HEALTHY
   |
   | consecutive qualifying transport failures >= threshold
   v
EJECTED (until ejectedUntil)
   |
   | expiry + successful real request
   v
HEALTHY
```

Round-robin selects among non-ejected targets. When all targets are ejected, the gateway forces selection of the target with the earliest `ejectedUntil` (degraded mode). No retry to another target within the same request.

Different gateways may temporarily disagree about target health because observations are local.

### Tradeoff

- **Cost:** Per-gateway health view; uneven exclusion under asymmetric failures.
- **Benefit:** No shared health store; simple MVP; aligns with passive observation from real traffic.

---

## Timeouts

### Failure scenario

Slow upstream indefinitely consumes gateway goroutines/connections; cascading latency under load.

### Mechanism

Request-scoped context deadline derived from route `timeout_ms` in the request-scoped `cfg`. Outbound execution uses an attempt loop wrapping `HTTP RoundTrip`:

```text
request deadline established
        |
        v
traffic pool selected
        |
        v
attempt loop → backend selected → outbound request → RoundTrip → classify result
        |
        +---- retry eligible and budget/deadline remain ----+
        +---------------------------------------------------+
        v
final response returned
```

`httputil.ReverseProxy` suits Phase 1 single-attempt proxying; bounded retries require a custom outbound layer or custom `RoundTripper` — not standard `ReverseProxy.ServeHTTP` alone.

On deadline exceeded → 504 Gateway Timeout; upstream connection cancelled.

### Tradeoff

- **Cost:** Aggressive timeouts cause false positives; custom outbound layer adds complexity for retries.
- **Benefit:** Bounded resource usage; predictable tail latency cap; safe retry eligibility.

---

## Retries

### Failure scenario

Unsafe requests (POST with body) or non-idempotent operations retried incorrectly, causing duplicate side effects.

### Mechanism

Retry eligibility rules in `retry_policies`:

- **Allowed:** GET, HEAD, OPTIONS; connect/timeouts on idempotent methods.
- **Denied:** POST/PUT/PATCH with body unless explicit `idempotent: true` flag (post-MVP).
- Bounded attempts (e.g., max 2) with exponential backoff.
- Only retry on gateway-generated errors (502, 503, connect failure), not 4xx from upstream.

### Tradeoff

- **Cost:** Lower success rate on transient errors for non-eligible methods.
- **Benefit:** Prevents duplicate writes; operator-controlled safety.

---

## Retry Amplification

### Failure scenario

Client, gateway, and upstream service each retry independently during outage, multiplying traffic 3×–9× and deepening failure.

### Mechanism (Post-MVP)

Gateway maintains **retry budget** per request via header (e.g., `X-Retry-Budget: 3`). Each retry layer decrements; at zero, no further retries. Control plane configures default budget per API.

### Tradeoff

- **Cost:** Requires client cooperation for full effect; gateway-only budget still helps gateway→upstream layer.
- **Benefit:** Limits outage traffic multiplication; industry pattern (Google SRE retry budget).

---

## Stale Gateway Membership

### Failure scenario

Dead gateway remains in convergence denominator forever; operator sees "67% converged" with no path to 100%.

### Mechanism

Heartbeat timestamp per gateway (`gateways.last_heartbeat_at`). Gateways POST heartbeat every `T` seconds. Control plane marks gateway **stale** if `now - last_heartbeat > 3T`. Convergence calculated over **live** gateways only. Stale gateways flagged in UI/API; optional auto-deregister post-MVP.

### Tradeoff

- **Cost:** Brief network partition marks gateway stale; may flap.
- **Benefit:** Accurate convergence; operators not chasing ghost nodes.

---

## Control-Plane Outage

### Failure scenario

PostgreSQL or control plane unavailable. Gateways cannot poll new config.

### Mechanism

Gateways serve traffic using last successfully activated snapshot indefinitely. Poll loop backs off and retries. No per-request control-plane contact. Rate limiting continues via Redis (independent failure domain). New publications impossible until recovery.

### Tradeoff

- **Cost:** Cannot roll out or rollback during outage; gateway registrations/heartbeats buffer or fail silently.
- **Benefit:** Data plane availability decoupled from management plane — core architectural invariant.

---

## Race Between Publication and Rollback

### Failure scenario

Operator publishes version 44, then immediately rollbacks to 42 while gateways mid-activation of 44. Some gateways on 44, some on 42, some on 43.

### Mechanism

`apis.desired_config_version` is an authoritative **mutable pointer** updated via `POST /config/versions/{version}/activate` with optimistic concurrency (`expected_desired_version`). Rollback does not modify any immutable snapshot — it repoints desired to a prior published version.

Published configuration version numbers are **monotonically increasing**. `desired_config_version` may move **forward or backward** to reference any existing published version.

Activation request example:

```json
{ "expected_desired_version": 44 }
```

If current desired ≠ 44 → `409 Conflict`. Gateways always converge to current desired, not highest version ever published.

### Tradeoff

- **Cost:** Gateways that activated version 44 must downgrade to 42 if desired regresses — second activation event.
- **Benefit:** Deterministic end state; immutable audit trail; no snapshot mutation on rollback.

---

## Telemetry Loss / Backpressure

### Failure scenario

Metrics sink or Prometheus scrape slow; blocking metric export stalls request handlers.

### Mechanism

**Prometheus (in-process):** `counter.Inc()`, `histogram.Observe()`, `gauge.Set()`. Updated on the request path without blocking; scraped later from `/metrics`. Standard Prometheus instrumentation is not an async queue that drops metric samples.

**Structured operational events (async, bounded):** Audit events (config version created, version activated, gateway stale/recovered) and activation summaries use a bounded in-memory channel. When full: drop event, increment `telemetry_events_dropped_total`, emit rate-limited internal warning. Request path never blocks on event export.

`operational_events` (PostgreSQL) stores durable control-plane audit records. `config_activation_events` stores append-only gateway activation attempt history.

### Tradeoff

- **Cost:** Dropped operational events under overload; Prometheus scrape lag during incidents.
- **Benefit:** Request path never blocked by observability export.

---

## Mechanisms Intentionally Excluded from MVP

| Mechanism | Reason excluded |
|-----------|-----------------|
| Raft / Paxos / multi-region consensus | No shared mutable gateway state requiring consensus; snapshots are control-plane authoritative |
| Kafka / event streaming for config | HTTP snapshot polling sufficient for MVP scale |
| gRPC streaming config | Complexity; HTTP ETag polling debuggable first |
| Exactly-once request processing | L7 proxy semantics are at-least-once; idempotency is upstream responsibility |
| Distributed deployment worker leases | Not a deployment orchestrator |
| Shared backend health store | Passive local health sufficient for MVP |
| Cross-region config replication | Single-region MVP |
| Delta/incremental config patches | Full snapshots simpler and safer |
| Custom binary config protocol | JSON + hash adequate |

Post-MVP evaluation triggers:

- \>100 gateways polling → consider long-poll or SSE
- Hot-key rate limits → Redis cluster or local token bucket + global sync
- Strict cross-gateway health agreement → optional health gossip or Redis health keys
