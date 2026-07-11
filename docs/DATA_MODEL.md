# AutoAPI Data Model

PostgreSQL schema for the renovated AutoAPI management plane. Gateways, Redis, and Prometheus own separate state domains as noted.

ORM: SQLAlchemy + Alembic migrations (proposed).

---

## State Ownership Summary

| Domain | Owner | Durability |
|--------|-------|------------|
| Projects, APIs, routes, policies, config versions | PostgreSQL (control plane) | Durable |
| Gateway registration, current status, activation history | PostgreSQL (control plane) | Durable |
| Active runtime config | Gateway memory (`atomic.Pointer[RuntimeConfig]`) | Ephemeral; rebuilt from snapshot |
| Passive backend health | Gateway memory | Ephemeral |
| Rate-limit counters | Redis | Ephemeral (TTL-bound windows) |
| Request metrics time-series | Prometheus | Ephemeral retention policy |
| Operational events | PostgreSQL | Durable audit trail |

---

## Entity Relationship Overview

```text
users
  └── projects
        └── apis
              ├── upstream_pools
              │     └── upstream_targets
              ├── routes
              ├── api_keys
              ├── rate_limit_policies
              ├── retry_policies
              ├── traffic_splits
              │     └── traffic_split_targets
              ├── route_policy_bindings
              └── config_versions

gateways
  └── gateway_api_status (current row per gateway + api)
  └── config_activation_events (append-only activation history)

operational_events (control-plane audit: version created, version activated, gateway stale/recovered)
```

---

## users

Operator accounts for management plane (MVP: single user acceptable).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL |
| `password_hash` | VARCHAR(255) | NULL if SSO post-MVP |
| `display_name` | VARCHAR(255) | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**Indexes:** `UNIQUE (email)`

---

## projects

Top-level organizational container (replaces legacy `Project.js` aggregate concept at management boundary).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `owner_id` | UUID | FK → users.id |
| `name` | VARCHAR(128) | NOT NULL |
| `description` | TEXT | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `owner_id` → `users(id)` ON DELETE RESTRICT

**Indexes:** `UNIQUE (owner_id, name)`

---

## apis

Registered API front door (host/base path).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `project_id` | UUID | FK → projects.id |
| `name` | VARCHAR(128) | NOT NULL |
| `host` | VARCHAR(255) | NOT NULL — match Host header |
| `base_path` | VARCHAR(255) | DEFAULT '/' |
| `description` | TEXT | |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true — administrative disable |
| `desired_config_version` | INTEGER | NOT NULL DEFAULT 0 — mutable pointer to published version |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `project_id` → `projects(id)` ON DELETE CASCADE

**Indexes:**

- `UNIQUE (project_id, name)`
- `INDEX (host)`
- `INDEX (desired_config_version)`

**Operational state** (`UNPUBLISHED`, `CONVERGING`, `CONVERGED`, `DEGRADED`, `DISABLED`) is **derived** at read time from `enabled`, `desired_config_version`, published versions, and `gateway_api_status` — not stored as an authoritative column.

---

## Derived API Operational State

| State | Derivation |
|-------|------------|
| `UNPUBLISHED` | No published configuration exists (`desired_config_version = 0` and no versions) |
| `DISABLED` | `enabled = false` |
| `CONVERGING` | Live gateways exist; not all ACK'd desired; no live gateway NACK'd |
| `CONVERGED` | All live gateways ACK `desired_config_version` |
| `DEGRADED` | One or more live gateways NACK desired version |

---

## upstream_pools

Named backend groups for load balancing.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `api_id` | UUID | FK → apis.id |
| `name` | VARCHAR(128) | NOT NULL |
| `load_balancing` | VARCHAR(32) | round_robin, weighted, least_conn (post-MVP) |
| `description` | TEXT | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `api_id` → `apis(id)` ON DELETE CASCADE

**Indexes:** `UNIQUE (api_id, name)`

---

## upstream_targets

Individual backend URLs within a pool.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `upstream_pool_id` | UUID | FK → upstream_pools.id |
| `url` | VARCHAR(2048) | NOT NULL — http(s) upstream base |
| `weight` | INTEGER | NOT NULL DEFAULT 100 |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `upstream_pool_id` → `upstream_pools(id)` ON DELETE CASCADE

**Indexes:** `INDEX (upstream_pool_id)`, `INDEX (upstream_pool_id, enabled)`

---

## routes

L7 routing rules (draft until published into snapshot).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `api_id` | UUID | FK → apis.id |
| `host` | VARCHAR(255) | NOT NULL — usually matches apis.host |
| `path_prefix` | VARCHAR(1024) | NOT NULL |
| `methods` | VARCHAR(16)[] | e.g. {GET,POST} |
| `upstream_pool_id` | UUID | FK → upstream_pools.id |
| `priority` | INTEGER | NOT NULL DEFAULT 0 — higher wins |
| `timeout_ms` | INTEGER | DEFAULT 30000 |
| `enabled` | BOOLEAN | DEFAULT true |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:**

- `api_id` → `apis(id)` ON DELETE CASCADE
- `upstream_pool_id` → `upstream_pools(id)` ON DELETE RESTRICT

**Indexes:**

- `INDEX (api_id, enabled)`
- `INDEX (api_id, host, path_prefix)`

---

## api_keys

Consumer authentication keys. Format: `ak_live_<key_id>.<secret>`. Plaintext returned once at creation.

| Column | Type | Notes |
|--------|------|-------|
| `key_id` | VARCHAR(64) | PK within API scope; embedded in key prefix |
| `api_id` | UUID | FK → apis.id |
| `name` | VARCHAR(128) | |
| `key_prefix` | VARCHAR(32) | Display prefix, e.g. `ak_live_abc123` |
| `secret_digest` | VARCHAR(128) | NOT NULL — HMAC-SHA-256(pepper, secret); same algorithm as gateway validation |
| `status` | VARCHAR(16) | active, revoked |
| `expires_at` | TIMESTAMPTZ | NULL = no expiry |
| `created_at` | TIMESTAMPTZ | |
| `revoked_at` | TIMESTAMPTZ | |

**PK:** `(api_id, key_id)` or surrogate `id` UUID with `UNIQUE (api_id, key_id)`

**FK:** `api_id` → `apis(id)` ON DELETE CASCADE

**Indexes:**

- `UNIQUE (api_id, key_id)`
- `INDEX (api_id, status)`

**Scope:** Keys belong to an API. Rate-limit and traffic policies apply via `route_policy_bindings` in the published snapshot, not direct key-to-policy FKs in MVP.

**Gateway pepper:** Stored in gateway environment/secrets manager only — never in PostgreSQL snapshot or published config. Control plane uses the same pepper at key creation to compute `secret_digest`.

**Runtime snapshot entry (minimum):** `{ "secret_digest", "status", "expires_at" }` keyed by `key_id`.

---

## rate_limit_policies

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `api_id` | UUID | FK → apis.id |
| `name` | VARCHAR(128) | NOT NULL |
| `limit` | INTEGER | NOT NULL |
| `window_seconds` | INTEGER | NOT NULL |
| `scope` | VARCHAR(32) | api_key, ip (post-MVP) |
| `redis_failure_mode` | VARCHAR(16) | fail_open, fail_closed |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `api_id` → `apis(id)` ON DELETE CASCADE

**Indexes:** `UNIQUE (api_id, name)`

---

## retry_policies

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `api_id` | UUID | FK → apis.id |
| `name` | VARCHAR(128) | NOT NULL |
| `max_attempts` | INTEGER | NOT NULL DEFAULT 1 |
| `backoff_ms` | INTEGER | DEFAULT 100 |
| `retryable_methods` | VARCHAR(16)[] | |
| `retryable_status_codes` | INTEGER[] | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `api_id` → `apis(id)` ON DELETE CASCADE

**Indexes:** `UNIQUE (api_id, name)`

---

## traffic_splits

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `api_id` | UUID | FK → apis.id |
| `name` | VARCHAR(128) | NOT NULL |
| `stable_identity_header` | VARCHAR(128) | e.g. X-API-Key-Id |
| `hash_algorithm` | VARCHAR(32) | consistent_hash_v1 |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** `api_id` → `apis(id)` ON DELETE CASCADE

**Indexes:** `UNIQUE (api_id, name)`

---

## traffic_split_targets

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `traffic_split_id` | UUID | FK → traffic_splits.id |
| `upstream_pool_id` | UUID | FK → upstream_pools.id |
| `weight` | INTEGER | NOT NULL — 0–100 cumulative |
| `created_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:**

- `traffic_split_id` → `traffic_splits(id)` ON DELETE CASCADE
- `upstream_pool_id` → `upstream_pools(id)` ON DELETE RESTRICT

**Indexes:** `INDEX (traffic_split_id)`

**Constraint:** Sum of weights per split should equal 100 (enforced at validation, optional CHECK trigger).

---

## route_policy_bindings

Associates policies with routes (draft graph).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `route_id` | UUID | FK → routes.id, UNIQUE |
| `rate_limit_policy_id` | UUID | FK → rate_limit_policies.id, NULL |
| `retry_policy_id` | UUID | FK → retry_policies.id, NULL |
| `traffic_split_id` | UUID | FK → traffic_splits.id, NULL |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**PK:** `id`

**FK:** route and policy FKs with ON DELETE SET NULL or CASCADE as appropriate

**Indexes:** `UNIQUE (route_id)`

---

## config_versions

Immutable published runtime snapshots.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `api_id` | UUID | FK → apis.id |
| `version` | INTEGER | NOT NULL |
| `content_hash` | VARCHAR(71) | sha256:... hex |
| `config_snapshot` | JSONB | NOT NULL — full RuntimeConfig |
| `comment` | TEXT | |
| `published_by` | UUID | FK → users.id, NULL |
| `published_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |

**PK:** `id`

**FK:**

- `api_id` → `apis(id)` ON DELETE CASCADE
- `published_by` → `users(id)` ON DELETE SET NULL

**Indexes:**

- `UNIQUE (api_id, version)`
- `UNIQUE (api_id, content_hash)`
- `INDEX (api_id, published_at DESC)`

**Invariant:** Rows are INSERT-only; no UPDATE of `config_snapshot` after publish.

Legacy parallel: `ApiDeployment.js` stored similar snapshot metadata but with broken Sequelize service layer.

---

## gateways

Registered gateway nodes.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `name` | VARCHAR(128) | NOT NULL |
| `region` | VARCHAR(64) | |
| `credential_hash` | VARCHAR(128) | gateway auth secret hash |
| `status` | VARCHAR(16) | registered, live, stale, deregistered |
| `last_heartbeat_at` | TIMESTAMPTZ | |
| `registered_at` | TIMESTAMPTZ | |
| `metadata` | JSONB | version, build info |

**PK:** `id`

**Indexes:**

- `UNIQUE (name, region)` — optional
- `INDEX (status, last_heartbeat_at)`

---

## gateway_api_status

One **current** row per gateway and API. Answers: *What version is this gateway currently serving?*

| Column | Type | Notes |
|--------|------|-------|
| `gateway_id` | UUID | FK → gateways.id |
| `api_id` | UUID | FK → apis.id |
| `active_version` | INTEGER | Last ACK'd active version |
| `last_attempted_version` | INTEGER | Last version activation was attempted |
| `last_status` | VARCHAR(8) | ack, nack, pending |
| `last_error_code` | VARCHAR(64) | On nack |
| `last_diagnostic` | VARCHAR(1024) | Bounded |
| `last_apply_duration_ms` | INTEGER | |
| `last_reported_at` | TIMESTAMPTZ | |

**PK:** `(gateway_id, api_id)`

**FK:**

- `gateway_id` → `gateways(id)` ON DELETE CASCADE
- `api_id` → `apis(id)` ON DELETE CASCADE

**Indexes:**

- `INDEX (api_id, last_status)`
- `INDEX (api_id, active_version)`

---

## config_activation_events

Append-only history of each activation attempt. Answers: *What happened when this gateway attempted this configuration version?*

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `gateway_id` | UUID | FK → gateways.id |
| `api_id` | UUID | FK → apis.id |
| `version` | INTEGER | Attempted version |
| `status` | VARCHAR(8) | ack, nack |
| `content_hash` | VARCHAR(71) | |
| `error_code` | VARCHAR(64) | On nack |
| `diagnostic` | VARCHAR(1024) | Bounded |
| `apply_duration_ms` | INTEGER | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |

**PK:** `id`

**FK:** gateway_id, api_id

**Indexes:**

- `INDEX (gateway_id, api_id, created_at DESC)`
- `INDEX (api_id, version, created_at DESC)`

---

## operational_events

Durable control-plane audit log for **management actions** and platform-level occurrences. Does not replace per-attempt gateway activation detail in `config_activation_events`.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `event_type` | VARCHAR(64) | config_version_created, config_version_activated, gateway_stale, gateway_recovered, ... |
| `entity_type` | VARCHAR(32) | api, gateway, project |
| `entity_id` | UUID | |
| `actor_id` | UUID | FK → users.id, NULL for system |
| `payload` | JSONB | event-specific details |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |

**PK:** `id`

**FK:** `actor_id` → `users(id)` ON DELETE SET NULL

**Indexes:**

- `INDEX (entity_type, entity_id, created_at DESC)`
- `INDEX (event_type, created_at DESC)`

**Examples:**

- `config_version_created` when `POST /config/versions` succeeds
- `config_version_activated` when `POST /config/versions/{v}/activate` succeeds
- `gateway_stale` / `gateway_recovered` from heartbeat monitor

---

## Draft vs Published State

Management tables (`routes`, `upstream_pools`, policies, bindings) hold **draft** state editable by operators.

`config_versions.config_snapshot` holds **published** immutable runtime state consumed by gateways.

Publication workflow:

1. Read draft graph for `api_id`
2. Validate referential integrity
3. Serialize to `RuntimeConfig` JSON
4. INSERT `config_versions` via `POST /config/versions`
5. Optionally `POST /config/versions/{version}/activate` to update `apis.desired_config_version`

Gateways never read draft tables.

---

## Legacy Model Mapping

| Legacy (`server-old/`) | Renovated table | Notes |
|------------------------|-----------------|-------|
| `Project.js` | `projects` + `apis` | Split aggregate into normalized entities |
| `Endpoints.js` | `routes` | MongoDB → PostgreSQL |
| `ApiGateway.js` Routes | `routes` + snapshot | Metadata → published snapshot |
| `ApiSecurityAuth.js` | `api_keys` + snapshot | |
| `ApiDeployment.js` | `config_versions` | Immutable version concept preserved |
| `ApiMonitoring.js` | Prometheus + `operational_events` | Not PostgreSQL per-request logs |
| N/A | `gateways`, `gateway_api_status`, `config_activation_events` | New for distributed runtime |

---

## Redis Key Schema (Not PostgreSQL)

| Key pattern | Value | TTL |
|-------------|-------|-----|
| `ratelimit:{api_id}:{policy_id}:{identity_hash}:{window_start}` | integer count | window_seconds + buffer |

Centrally coordinated cross-gateway **fixed-window** rate limiting via atomic Lua script.

Owned exclusively by Redis; no PostgreSQL mirror.

---

## Migration Notes

- Use Alembic from greenfield; do not migrate legacy MongoDB/Sequelize data automatically
- Legacy `Project.AllEndpoints` JSONB blob denormalization replaced by normalized draft tables + snapshot publication
- API key `secret_digest` uses HMAC-SHA-256 with control-plane pepper — not bcrypt
