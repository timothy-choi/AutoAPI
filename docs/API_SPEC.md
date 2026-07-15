# AutoAPI Management REST API Specification

Base path: `/api/v1`

This document covers **management-plane** and **gateway-control** APIs. Client API traffic flows through the Go gateway data plane (not documented here as REST — standard HTTP proxy semantics).

Authentication (MVP): operator bearer token for management endpoints; gateway credential for gateway endpoints. Post-MVP: JWT with project scope.

Common error envelope:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable summary",
    "details": []
  }
}
```

---

## Projects

### `POST /api/v1/projects`

**Purpose:** Create a project container for APIs.

**Request:**

```json
{
  "name": "my-project",
  "description": "Optional"
}
```

**Response:** `201`

```json
{
  "id": "uuid",
  "name": "my-project",
  "description": "Optional",
  "created_at": "2026-07-10T00:00:00Z"
}
```

**Errors:** `400` invalid name; `409` duplicate name; `401` unauthorized

---

### `GET /api/v1/projects`

**Purpose:** List projects.

**Response:** `200` `{ "items": [...], "total": N }`

---

### `GET /api/v1/projects/{project_id}`

**Purpose:** Get project by ID.

**Response:** `200` project object

**Errors:** `404`

---

### `PATCH /api/v1/projects/{project_id}`

**Purpose:** Update project name/description.

**Response:** `200` updated project

---

### `DELETE /api/v1/projects/{project_id}`

**Purpose:** Delete project and cascade APIs (soft-delete post-MVP).

**Response:** `204`

**Errors:** `409` if APIs still registered (optional guard)

---

## APIs

### `POST /api/v1/projects/{project_id}/apis`

**Purpose:** Register an API (logical gateway front door).

**Request:**

```json
{
  "name": "orders-api",
  "host": "api.example.com",
  "base_path": "/v1",
  "description": "Orders service"
}
```

**Response:** `201`

```json
{
  "id": "uuid",
  "project_id": "uuid",
  "name": "orders-api",
  "host": "api.example.com",
  "base_path": "/v1",
  "enabled": true,
  "desired_config_version": 0,
  "operational_state": "UNPUBLISHED",
  "created_at": "..."
}
```

---

### `GET /api/v1/projects/{project_id}/apis`

**Purpose:** List APIs in project.

**Response:** `200` `{ "items": [...] }`

---

### `GET /api/v1/apis/{api_id}`

**Purpose:** Get API details including desired config version.

**Response:** `200`

---

### `PATCH /api/v1/apis/{api_id}`

**Purpose:** Update API metadata (not runtime routes — those are draft until publish).

**Response:** `200`

---

### `DELETE /api/v1/apis/{api_id}`

**Purpose:** Delete API and related config.

**Response:** `204`

---

## Upstream Pools

### `POST /api/v1/apis/{api_id}/upstream-pools`

**Purpose:** Create upstream pool (load-balanced backend group).

**Request:**

```json
{
  "name": "orders-v1-pool",
  "load_balancing": "round_robin",
  "description": "Primary pool"
}
```

**Response:** `201` pool object with `id`

---

### `GET /api/v1/apis/{api_id}/upstream-pools`

**Purpose:** List pools for API.

**Response:** `200` `{ "items": [...] }`

---

### `GET /api/v1/upstream-pools/{pool_id}`

**Purpose:** Get pool by ID.

**Response:** `200`

---

### `PATCH /api/v1/upstream-pools/{pool_id}`

**Purpose:** Update pool metadata/load balancing algorithm.

**Response:** `200`

---

### `DELETE /api/v1/upstream-pools/{pool_id}`

**Purpose:** Delete pool (fail if referenced by routes).

**Response:** `204`

**Errors:** `409` pool in use

---

## Backend Targets

### `POST /api/v1/upstream-pools/{pool_id}/targets`

**Purpose:** Add backend target URL to pool.

**Request:**

```json
{
  "url": "http://orders-v1:8080",
  "weight": 100,
  "enabled": true
}
```

**Response:** `201` target object

---

### `GET /api/v1/upstream-pools/{pool_id}/targets`

**Purpose:** List targets in pool.

**Response:** `200` `{ "items": [...] }`

---

### `PATCH /api/v1/upstream-targets/{target_id}`

**Purpose:** Update URL, weight, enabled flag.

**Response:** `200`

---

### `DELETE /api/v1/upstream-targets/{target_id}`

**Purpose:** Remove target from pool.

**Response:** `204`

---

## Routes

### `POST /api/v1/apis/{api_id}/routes`

**Purpose:** Define L7 route rule (draft until published).

**Request:**

```json
{
  "host": "api.example.com",
  "path_prefix": "/v1/orders",
  "methods": ["GET", "POST"],
  "upstream_pool_id": "uuid",
  "priority": 100,
  "timeout_ms": 30000
}
```

**Response:** `201` route object

---

### `GET /api/v1/apis/{api_id}/routes`

**Purpose:** List routes for API.

**Response:** `200` `{ "items": [...] }`

---

### `PATCH /api/v1/routes/{route_id}`

**Purpose:** Update route match rules or upstream pool.

**Response:** `200`

---

### `DELETE /api/v1/routes/{route_id}`

**Purpose:** Delete route.

**Response:** `204`

---

## API Keys

### `POST /api/v1/apis/{api_id}/api-keys`

**Purpose:** Create API key for consumer authentication.

**Request:**

```json
{
  "name": "partner-acme",
  "expires_at": null
}
```

**Response:** `201`

```json
{
  "key_id": "abc123",
  "name": "partner-acme",
  "key_prefix": "ak_live_abc123",
  "key": "ak_live_abc123.<secret_shown_once>",
  "status": "active",
  "expires_at": null,
  "created_at": "..."
}
```

The full plaintext key uses the format `ak_live_<key_id>.<secret>`. It is returned **once** at creation and never stored in plaintext.

API keys are scoped to the parent API (`api_id`). Rate-limit and other policies are applied via route bindings in the published snapshot, not by binding keys directly to policies in MVP.

---

### `GET /api/v1/apis/{api_id}/api-keys`

**Purpose:** List keys (never returns full secret).

**Response:** `200` `{ "items": [...] }`

---

### `POST /api/v1/apis/{api_id}/api-keys/{key_id}/revoke`

**Purpose:** Revoke API key.

**Response:** `200` `{ "status": "revoked" }`

---

## Rate-Limit Policies

### `POST /api/v1/apis/{api_id}/rate-limit-policies`

**Purpose:** Define rate limit policy.

**Request:**

```json
{
  "name": "default",
  "limit": 100,
  "window_seconds": 60,
  "scope": "api_key",
  "redis_failure_mode": "fail_open"
}
```

**Response:** `201`

---

### `GET /api/v1/apis/{api_id}/rate-limit-policies`

**Purpose:** List policies.

**Response:** `200`

---

### `PATCH /api/v1/rate-limit-policies/{policy_id}`

**Purpose:** Update limits.

**Response:** `200`

---

### `DELETE /api/v1/rate-limit-policies/{policy_id}`

**Purpose:** Delete policy.

**Response:** `204`

---

## Backend Health Policies (Phase 5)

Passive transport-failure detection is configured in the control plane and compiled into upstream pool snapshots. Gateway-local ejection state is **not** stored in PostgreSQL or Redis.

### `POST /api/v1/apis/{api_id}/backend-health-policies`

**Purpose:** Create a passive health policy for an API.

**Request:**

```json
{
  "name": "orders-passive-health",
  "consecutiveFailureThreshold": 3,
  "ejectionDurationSeconds": 30,
  "maxEjectionPercent": 50,
  "enabled": true
}
```

**Response:** `201 Created`

---

### `GET /api/v1/apis/{api_id}/backend-health-policies`

**Purpose:** List policies for an API.

**Response:** `200`

---

### `GET /api/v1/apis/{api_id}/backend-health-policies/{policy_id}`

**Purpose:** Get one policy.

**Response:** `200`

---

### `PATCH /api/v1/apis/{api_id}/backend-health-policies/{policy_id}`

**Purpose:** Partial update. Omitted fields retain existing values.

**Response:** `200`

---

### `PUT /api/v1/upstream-pools/{pool_id}/backend-health-policy`

**Purpose:** Bind an enabled policy to a draft upstream pool (same API).

**Request:**

```json
{
  "backendHealthPolicyId": "uuid"
}
```

**Response:** `200 OK`

---

### `DELETE /api/v1/upstream-pools/{pool_id}/backend-health-policy`

**Purpose:** Remove passive-health binding from the draft pool (does not delete the policy record).

**Response:** `200 OK`

---

## Retry Policies (Phase 6)

Bounded upstream retries are configured in the control plane, compiled into route snapshots, and enforced gateway-locally. **`maxAttempts` is the total upstream attempts including the first attempt** (`1` disables retries; `2` allows at most one retry).

Retry budgets and attempt counters are **not** stored in PostgreSQL or Redis.

### `POST /api/v1/apis/{api_id}/retry-policies`

**Purpose:** Create a retry policy for an API.

**Request:**

```json
{
  "name": "safe-orders-retry",
  "maxAttempts": 2,
  "perAttemptTimeoutMs": 1000,
  "retryOnConnectFailure": true,
  "retryOnConnectionReset": true,
  "retryOnDnsFailure": true,
  "retryOnResponseTimeout": true,
  "retryableMethods": ["GET", "HEAD", "OPTIONS", "PUT", "DELETE"],
  "requireIdempotencyKeyForUnsafeMethods": true,
  "budgetPercent": 20,
  "budgetMinRetriesPerSecond": 2,
  "budgetWindowSeconds": 10,
  "enabled": true
}
```

**Response:** `201 Created`

Publication rejects POST/PATCH in `retryableMethods` when `requireIdempotencyKeyForUnsafeMethods` is false.

---

### `GET /api/v1/apis/{api_id}/retry-policies`

**Purpose:** List retry policies for an API.

**Response:** `200`

---

### `GET /api/v1/apis/{api_id}/retry-policies/{policy_id}`

**Purpose:** Get one retry policy.

**Response:** `200`

---

### `PATCH /api/v1/apis/{api_id}/retry-policies/{policy_id}`

**Purpose:** Partial update. Omitted fields retain existing values; merged state is validated.

**Response:** `200`

---

### `PUT /api/v1/routes/{route_id}/retry-policy`

**Purpose:** Bind an enabled retry policy to a draft route (same API).

**Request:**

```json
{
  "retryPolicyId": "uuid"
}
```

**Response:** `200 OK`

---

### `DELETE /api/v1/routes/{route_id}/retry-policy`

**Purpose:** Remove retry binding from the draft route (does not delete the policy).

**Response:** `200 OK`

---

### Gateway internal: `GET /internal/v1/retry-status`

**Purpose:** Trusted-network visibility into gateway-local retry budget snapshots (no request or key material).

**Response:** `200`

---

## Traffic Policies

### `POST /api/v1/apis/{api_id}/traffic-splits`

**Purpose:** Define weighted/deterministic split between upstream pools.

**Request:**

```json
{
  "name": "canary-v2",
  "stable_identity_header": "X-API-Key-Id",
  "targets": [
    { "upstream_pool_id": "uuid-v1", "weight": 90 },
    { "upstream_pool_id": "uuid-v2", "weight": 10 }
  ]
}
```

**Response:** `201`

---

### `GET /api/v1/apis/{api_id}/traffic-splits`

**Purpose:** List traffic splits.

**Response:** `200`

---

### `POST /api/v1/routes/{route_id}/policy-bindings`

**Purpose:** Bind rate limit, retry, or traffic split policy to route.

**Request:**

```json
{
  "rate_limit_policy_id": "uuid",
  "retry_policy_id": "uuid",
  "traffic_split_id": "uuid"
}
```

**Response:** `201`

---

## Configuration Validation

### `POST /api/v1/apis/{api_id}/config/validate`

**Purpose:** Dry-run validate current draft configuration without publishing.

**Response:** `200`

```json
{
  "valid": true,
  "errors": [],
  "warnings": []
}
```

**Errors:** `200` with `valid: false` and error list; `404` API not found

---

## Configuration Versions

Creating an immutable configuration version **compiles** the current draft graph into a published snapshot. It does not change `desired_config_version` unless followed by an activate call (or `activate: true` in the create request).

### `POST /api/v1/apis/{api_id}/config/versions`

**Purpose:** Validate draft configuration, serialize immutable snapshot, insert new `config_versions` row with monotonically increasing version number.

**Request:**

```json
{
  "comment": "Add orders POST route",
  "activate": false
}
```

Optional `"activate": true` sets `desired_config_version` to the newly created version in the same transaction (convenience for the common publish-and-rollout path).

**Response:** `201`

```json
{
  "api_id": "uuid",
  "version": 5,
  "content_hash": "sha256:abc123...",
  "published_at": "2026-07-10T00:00:00Z",
  "published_by": "operator-id",
  "desired_config_version": 4
}
```

`desired_config_version` in the response reflects the current desired pointer after the operation (unchanged if `activate` is false).

**Errors:** `400` validation failed; `409` no changes since last version

---

### `GET /api/v1/apis/{api_id}/config/versions`

**Purpose:** List published configuration versions (metadata only).

**Response:** `200`

```json
{
  "items": [
    {
      "version": 5,
      "content_hash": "sha256:...",
      "published_at": "...",
      "comment": "..."
    }
  ]
}
```

---

### `GET /api/v1/apis/{api_id}/config/versions/{version}`

**Purpose:** Get published version metadata (operator). Gateways fetch full snapshot via `GET /api/v1/gateway-config/{api_id}/versions/{version}`.

**Response:** `200`

```json
{
  "api_id": "uuid",
  "version": 5,
  "content_hash": "sha256:...",
  "comment": "...",
  "published_at": "...",
  "published_by": "operator-id"
}
```

**Errors:** `404` version not found

---

## Activation and Rollback

Rollback is **not** a separate endpoint. Rollback means activating an already published prior version.

### `POST /api/v1/apis/{api_id}/config/versions/{version}/activate`

**Purpose:** Set `apis.desired_config_version` to `{version}`. Gateways converge to this snapshot. Uses optimistic concurrency to prevent lost updates.

**Request:**

```json
{
  "expected_desired_version": 5
}
```

Omit `expected_desired_version` to activate without concurrency check (discouraged for operator UIs).

**Response:** `200`

```json
{
  "api_id": "uuid",
  "desired_config_version": 4,
  "previous_desired_version": 5,
  "content_hash": "sha256:..."
}
```

**Errors:**

- `404` version not found or not published
- `409 Conflict` — current `desired_config_version` ≠ `expected_desired_version`
- `400` API administratively disabled (`enabled = false`)

---

## Gateway Registration

### `POST /api/v1/gateways/register`

**Purpose:** Register new gateway node (gateway-facing).

**Request:**

```json
{
  "name": "gateway-a",
  "region": "local",
  "registration_token": "env-provided-token"
}
```

**Response:** `201`

```json
{
  "gateway_id": "uuid",
  "gateway_credential": "gw_secret_...",
  "poll_interval_seconds": 10
}
```

---

### `GET /api/v1/gateways`

**Purpose:** List registered gateways (operator).

**Query:** `?live_only=true` excludes stale by heartbeat.

**Response:** `200` `{ "items": [...] }`

---

### `GET /api/v1/gateways/{gateway_id}`

**Purpose:** Gateway details and last known status.

**Response:** `200`

---

## Gateway Heartbeats

### `POST /api/v1/gateways/{gateway_id}/heartbeat`

**Purpose:** Gateway liveness signal (gateway-facing).

**Request:**

```json
{
  "active_config_version": 5,
  "active_content_hash": "sha256:...",
  "uptime_seconds": 3600,
  "inflight_requests": 12
}
```

**Response:** `204`

**Errors:** `401` invalid credential; `404` unknown gateway

---

## Gateway Configuration Retrieval

Gateway-facing configuration endpoints are scoped by **API**, not gateway ID. The gateway authenticates with its credential on every request.

### `GET /api/v1/gateway-config/{api_id}/desired`

**Purpose:** Return desired-version metadata for polling.

**Headers:**

- `If-None-Match: sha256:...` → `304` if unchanged

**Response:** `200`

```json
{
  "api_id": "uuid",
  "desired_version": 5,
  "content_hash": "sha256:abc123...",
  "snapshot_url": "/api/v1/gateway-config/{api_id}/versions/5"
}
```

---

### `GET /api/v1/gateway-config/{api_id}/versions/{version}`

**Purpose:** Fetch full immutable runtime snapshot for local validation and activation.

**Headers:** `If-None-Match` supported

**Response:** `200`

```json
{
  "api_id": "uuid",
  "version": 5,
  "content_hash": "sha256:abc123...",
  "snapshot": {
    "routes": [],
    "upstream_pools": {},
    "api_keys": {},
    "rate_limit_policies": {},
    "traffic_splits": {},
    "retry_policies": {}
  }
}
```

The `api_keys` map in the snapshot contains only validation metadata keyed by `key_id` (digest, status, expiry) — never the gateway pepper or plaintext secrets.

**Errors:** `404` version not found

---

## ACK/NACK

### `POST /api/v1/gateways/{gateway_id}/config-status`

**Purpose:** Report configuration activation result. Updates `gateway_api_status` (current row) and appends `config_activation_events` (history).

**Request (ACK):**

```json
{
  "api_id": "uuid",
  "version": 5,
  "content_hash": "sha256:abc123...",
  "status": "ack",
  "apply_duration_ms": 12
}
```

**Request (NACK):**

```json
{
  "api_id": "uuid",
  "version": 5,
  "content_hash": "sha256:abc123...",
  "status": "nack",
  "error_code": "VALIDATION_FAILED",
  "diagnostic": "upstream pool orders-v2-pool has zero targets",
  "apply_duration_ms": 3
}
```

**Response:** `204`

---

## Convergence Status

### `GET /api/v1/apis/{api_id}/convergence`

**Purpose:** Report derived operational convergence for the API's desired version. Operational state is **derived**, not stored as an authoritative column on `apis`.

**Response:** `200`

```json
{
  "api_id": "uuid",
  "enabled": true,
  "desired_config_version": 5,
  "desired_content_hash": "sha256:...",
  "operational_state": "CONVERGING",
  "live_gateways": 3,
  "converged_gateways": 2,
  "convergence_ratio": 0.667,
  "gateways": [
    {
      "gateway_id": "uuid",
      "last_status": "ack",
      "active_version": 5,
      "last_heartbeat_at": "..."
    },
    {
      "gateway_id": "uuid",
      "last_status": "nack",
      "active_version": 4,
      "last_error_code": "VALIDATION_FAILED"
    }
  ]
}
```

**Derived `operational_state` values:**

| State | Definition |
|-------|------------|
| `UNPUBLISHED` | No published configuration exists (`desired_config_version = 0`) |
| `DISABLED` | `apis.enabled = false` |
| `CONVERGING` | At least one live gateway has not ACK'd desired version; no live gateway has NACK'd |
| `CONVERGED` | All live expected gateways ACK desired version |
| `DEGRADED` | One or more live gateways NACK desired version or cannot serve it |

---

## Health

### `GET /api/v1/health`

**Purpose:** Liveness — process running.

**Response:** `200` `{ "status": "ok" }`

---

### `GET /api/v1/health/ready`

**Purpose:** Readiness — control plane can serve requests (DB connected).

**Response:** `200` or `503`

```json
{
  "status": "ready",
  "checks": {
    "postgres": "ok"
  }
}
```

---

## Gateway Data Plane (Non-REST)

Client traffic: `https://{gateway_host}/{path}` with standard HTTP semantics.

| Aspect | Behavior |
|--------|----------|
| Auth | `X-API-Key` header; format `ak_live_<key_id>.<secret>` |
| Rate limit (quota exceeded, Redis up) | `429 Too Many Requests` + `Retry-After` |
| Rate limit (Redis unavailable, policy `fail_closed`) | `503 Service Unavailable` |
| Rate limit (Redis unavailable, policy `fail_open`) | Request allowed; bypass metric incremented |
| Timeout | `504 Gateway Timeout` |
| No route | `404 Not Found` |
| Upstream failure | `502 Bad Gateway` / `503 Service Unavailable` |
| Request ID | `X-Request-ID` on request and response |

Gateway exposes:

- `GET /healthz` — liveness
- `GET /readyz` — config activated
- `GET /metrics` — Prometheus scrape

These are **not** under `/api/v1`.
