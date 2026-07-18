# Service Discovery (Phase 10)

Phase 10 adds **dynamic backend membership** for routes. Instead of (or in addition to) static upstream pools, operators define a **logical discovered service**; backends register themselves, renew leases with heartbeats, and the control plane compiles eligible instances into immutable runtime snapshots that gateways load like any other config version.

---

## Concepts

| Term | Meaning |
|------|---------|
| **Discovered service** | Project-scoped logical service (name, selection strategy, registration mode, default scheme/port) |
| **Service instance** | A registered backend endpoint (`host`, `port`, `scheme`, weight, zone/region metadata) |
| **Membership version** | Monotonic counter on the discovered service; bumps on routing-relevant changes |
| **Lease** | Time-bound liveness contract renewed by heartbeats; expired leases transition to `STALE` |

Routes bind **either** a static upstream pool **or** a discovered service (not both). Traffic-split destinations may also reference discovered services.

---

## Lifecycle

```text
Operator                    Control plane                 Gateway
   |                              |                          |
   |-- POST /projects/.../services |                          |
   |-- PUT /routes/{id}/discovered-service                     |
   |-- publish + activate -------->|                          |
   |                              |<----- poll snapshot ------|
   |                              |                          |
Backend A -- register + heartbeat->|                          |
   |                              |-- compile eligible READY --|
   |                              |-- publish/activate (auto) |
   |                              |-------------------------> apply
   |                              |                          |
Backend A stops heartbeats        |                          |
   |                              |-- stale reaper -> STALE  |
   |                              |-- publish/activate       |
   |                              |-------------------------> drop A
   |                              |                          |
Backend A -- heartbeat --------->| READY + membership bump  |
   |                              |-------------------------> include A
```

### Instance statuses

| Status | Eligible for routing? | Typical cause |
|--------|----------------------|---------------|
| `READY` | Yes (if lease valid) | Active registration with fresh lease |
| `STALE` | No | Lease expired; stale reaper |
| `DRAINING` | No | Operator or backend-initiated drain |
| `DEREGISTERED` | No | Explicit deregistration |

Only `READY` instances with unexpired leases are compiled into runtime snapshots (`DiscoveredServiceCompiler.isEligible`).

---

## Registration and security

**Registration modes**

- `OPEN` — any caller may register/heartbeat/drain/deregister for the service ID
- `CREDENTIAL_REQUIRED` — requires `X-Service-Registration-Token` matching a project-scoped credential

Credentials are created via management API; plaintext tokens are returned once at creation.

**Instance operations** (all under `/api/v1/services/{serviceId}/instances/...`):

- `POST .../register` — upsert instance metadata and initial lease
- `POST .../{instanceId}/heartbeat` — extend lease; `STALE` → `READY` triggers recovery + membership bump
- `POST .../{instanceId}/drain` — mark draining, bump membership
- `POST .../{instanceId}/deregister` — terminal state, bump membership

---

## Runtime compilation and propagation

On membership changes (register, stale transition, recovery, drain, deregister), `DiscoveryRuntimePublisher`:

1. Publishes a new immutable config version for every API referencing the service (direct route binding or traffic-split destination)
2. When `autoapi.discovery.auto-activate-on-membership-change` is true (default), activates that version automatically

Compiled instances receive deterministic target IDs derived from `serviceId:instanceId:registrationEpoch` so gateway-local health and circuit-breaker state can survive URL-preserving re-registration.

---

## Gateway selection

When a route (or traffic-split destination) resolves to a discovered service, `DiscoveredServiceSelector` chooses an instance:

| Strategy | Behavior |
|----------|----------|
| `ROUND_ROBIN` | Health- and circuit-aware round robin across eligible instances |
| `CONSISTENT_HASH` | Rendezvous hashing on request ID, API key ID, or a named header |

If no eligible instance exists, the gateway returns a terminal error (no eligible discovered instance).

---

## Configuration

Control-plane properties (`autoapi.discovery.*`):

| Property | Default | Purpose |
|----------|---------|---------|
| `enabled` | `true` | Master switch |
| `default-lease-duration` | `30s` | Lease when not specified on register/heartbeat |
| `min-lease-duration` / `max-lease-duration` | `5s` / `5m` | Validation bounds |
| `stale-reaper-interval` | `5s` | Background lease expiry scan |
| `auto-activate-on-membership-change` | `true` | Publish + activate on membership bumps |

---

## Management API summary

Project scope:

- `POST/GET/PATCH/DELETE /api/v1/projects/{projectId}/services[/{serviceId}]`
- `POST/GET /api/v1/projects/{projectId}/services/{serviceId}/registration-credentials`

Instance scope:

- `GET /api/v1/services/{serviceId}/instances[/{instanceId}]`
- `POST /api/v1/services/{serviceId}/instances/register`
- `POST /api/v1/services/{serviceId}/instances/{instanceId}/heartbeat|drain|deregister`

Route binding:

- `PUT /api/v1/routes/{routeId}/discovered-service` — body `{ "serviceId": "<uuid>" }`
- `DELETE /api/v1/routes/{routeId}/discovered-service`

---

## Metrics

**Control plane** (`/actuator/prometheus`):

- `autoapi_service_discovery_registrations_total`
- `autoapi_service_discovery_heartbeats_total`
- `autoapi_service_discovery_heartbeat_failures_total`
- `autoapi_service_discovery_stale_transitions_total`
- `autoapi_service_discovery_recoveries_total`
- `autoapi_service_discovery_deregistrations_total`
- `autoapi_service_discovery_membership_changes_total`
- `autoapi_service_discovery_snapshot_updates_total`

**Gateway**:

- `autoapi_gateway_service_instance_selections_total`
- `autoapi_gateway_service_no_eligible_instance_total`

---

## Smoke test

End-to-end lifecycle validation:

```bash
./scripts/smoke-phase10.sh
```

Uses Docker Compose mock upstreams (`upstream-v1`, `upstream-v2`) as registrable backends. Set `SMOKE_SKIP_UP=true` when the stack is already running.

---

## Related docs

- [ARCHITECTURE.md](./ARCHITECTURE.md) — system overview
- [DISTRIBUTED_SYSTEMS.md](./DISTRIBUTED_SYSTEMS.md) — convergence and failure scenarios
- [OBSERVABILITY.md](./OBSERVABILITY.md) — gateway metrics and request correlation
