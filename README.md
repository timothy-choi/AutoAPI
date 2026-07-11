# AutoAPI

AutoAPI is being renovated into a **distributed API runtime and traffic-management platform** — a self-hosted L7 API gateway with versioned configuration propagation, multi-node traffic policy, and global rate limiting (roadmap).

## Repository layout

| Path | Purpose |
|------|---------|
| [`Server/`](Server/) | Renovated **Java 21 / Spring WebFlux** gateway data plane (active development) |
| [`server-old/`](server-old/) | Frozen legacy implementation (reference only — do not modify) |
| [`docs/`](docs/) | Architecture and roadmap documentation — start at [`docs/README.md`](docs/README.md) |

## Currently implemented

**Phase 1 — local Java gateway vertical slice**

- Static JSON runtime configuration loaded at startup
- Host, longest path-prefix, and HTTP method routing
- Nonblocking reverse proxy to a configured upstream (Reactor Netty)
- Request ID generation or propagation (`X-Request-ID`)
- Operational `/healthz` and `/readyz` endpoints
- Controlled JSON error responses

See [`Server/README.md`](Server/README.md) for build, run, and configuration details.

## Roadmap (not yet implemented)

The following are documented in `docs/` but **not** implemented in this repository yet:

- FastAPI control plane and PostgreSQL
- Immutable configuration versions, polling, ACK/NACK, convergence
- API-key authentication and distributed rate limiting (Redis)
- Health-aware backend selection, retries, canary traffic splitting
- AWS deployment

Do not assume these capabilities exist until the corresponding roadmap phase lands in `Server/` or future services.

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
