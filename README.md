# AutoAPI

AutoAPI is being renovated into a **distributed API runtime and traffic-management platform** — a self-hosted L7 API gateway with versioned configuration propagation, multi-node traffic policy, and global rate limiting (roadmap).

## Repository layout

| Path | Purpose |
|------|---------|
| [`Server/`](Server/) | Renovated **Java 21 / Spring WebFlux** gateway data plane (active development) |
| [`server-old/`](server-old/) | Frozen legacy implementation (reference only — do not modify) |
| [`docs/`](docs/) | Architecture and roadmap documentation — start at [`docs/README.md`](docs/README.md) |

## Currently implemented

**Phase 1 — Java gateway data plane**

- Static JSON runtime configuration loaded at startup
- Host, longest path-prefix, and HTTP method routing
- Nonblocking reverse proxy to a configured upstream (Reactor Netty)
- Request ID generation or propagation (`X-Request-ID`)
- Operational `/healthz` and `/readyz` endpoints
- Controlled JSON error responses

**Phase 2A — control-plane configuration compilation (in-process)**

- PostgreSQL-backed draft management (projects, APIs, pools, targets, routes)
- Validation and deterministic compilation into immutable configuration versions
- Management REST API at `/api/v1/**`
- **Live gateway still uses static Phase 1 file config** — no polling or activation yet

See [`Server/README.md`](Server/README.md) for build, run, management API, and configuration details.

**Phase 10 — dynamic service discovery**

- Logical **discovered services** with self-registration, lease heartbeats, stale reaper, drain, and deregister
- Routes bind discovered services via `PUT /api/v1/routes/{routeId}/discovered-service`
- Membership changes auto-publish and activate runtime snapshots; gateways select eligible instances (round robin or consistent hash)
- See [`docs/SERVICE_DISCOVERY.md`](docs/SERVICE_DISCOVERY.md) and run `./scripts/smoke-phase10.sh`

**Phase 11 — platform events and webhooks**

- Durable platform events with transactional outbox and signed webhook delivery
- See [`docs/EVENTS.md`](docs/EVENTS.md) and run `./scripts/smoke-phase11.sh`

**Phase 12 — gateway groups and progressive rollouts**

- Project-scoped gateway groups with label selectors and explicit membership
- Progressive runtime configuration rollouts with deterministic cohorts, pause/resume, and rollback
- See [`docs/ROLLOUTS.md`](docs/ROLLOUTS.md) and run `./scripts/smoke-phase12.sh`

**Phase 13 — management-plane identity, RBAC, and scoped credentials**

- Organization-scoped tenant boundary with default organization migration for existing projects
- Bearer management tokens (`aat_<publicId>_<secret>`) with HMAC-SHA256 digests
- Bootstrap administrator initialization via `POST /api/v1/management/bootstrap`
- Built-in roles, role bindings, service accounts, and scoped credentials
- Management authentication filter protecting `/api/v1/**` (gateway and service-registration paths exempt)
- See [`docs/MANAGEMENT_AUTH.md`](docs/MANAGEMENT_AUTH.md), [`docs/PHASE13_SECURITY_REVIEW.md`](docs/PHASE13_SECURITY_REVIEW.md), and run `./scripts/smoke-phase13.sh`

See [`Server/README.md`](Server/README.md) for build, run, management API, and configuration details. Phases 1–13 are implemented in `Server/`. Run the matching smoke script under `scripts/` (e.g. `./scripts/smoke-phase13.sh`).

## Quick start (Phase 1)

```bash
docker compose up --build
curl -H "Host: api.autoapi.local" http://localhost:8080/v1/orders/123
```

## CI/CD

Every relevant push to **any branch** runs Java verification, Compose validation, integration checks, and container build/smoke validation. Pull requests and non-`main` branch pushes validate but **do not** publish container images.

| Trigger | Validation | GHCR publication |
|---------|------------|------------------|
| Push to any branch (relevant paths) | Yes | No |
| Pull request | Yes | No |
| Push to `main` | Yes | `:main` and `:sha-<short>` |
| Semantic version tag `v*.*.*` | Yes | Semver tags and `latest` |

No cloud deployment is performed. See [`Server/README.md`](Server/README.md#cicd) for full policy details.
