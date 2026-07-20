# AutoAPI Documentation Index

Documentation for AutoAPI, a **distributed API runtime and traffic-management platform** implemented in [`Server/`](../Server/) (Java 21 / Spring WebFlux).

Legacy implementation lives in [`server-old/`](../server-old/) and is analyzed separately. The active platform includes a PostgreSQL-backed control plane, gateway runtime, policy engine, and Docker Compose deployment — see the [root README](../README.md) for the current feature list and quick start.

---

## Documents

### [LEGACY_ANALYSIS.md](./LEGACY_ANALYSIS.md)

Reverse-engineering report of the existing repository.

**Read this to understand:**
- What AutoAPI actually implemented (vs README claims)
- Module inventory: ApiGenerator, social platform, cloud integrations
- Data models (Sequelize + Mongoose split)
- Why `server.js` cannot boot and ApiGenerator is unwired
- Architectural debt and dead code

**Key finding:** Legacy AutoAPI targeted cloud API **provisioning** (AWS APIGW, Azure APIM), not a custom L7 gateway data plane.

---

### [RENOVATION_OPTIONS.md](./RENOVATION_OPTIONS.md)

Evaluation of three renovation directions:

1. Declarative API deployment and operations control plane
2. API generation and contract automation platform
3. **Distributed API runtime and traffic-management platform** (recommended)

Includes ranking, overlap analysis with DevNest and Cloud Networking Studio, and defensibility after repository inspection.

---

### [PRODUCT_SPEC.md](./PRODUCT_SPEC.md)

Product definition for the renovated platform.

**Read this to understand:**
- Target users, goals, and explicit non-goals
- What AutoAPI is **not** (orchestrator, service mesh, Envoy replacement, etc.)
- MVP, post-MVP, and future requirements
- Core operator workflow from project creation through rollback

---

### [ARCHITECTURE.md](./ARCHITECTURE.md)

System architecture and request-path design.

**Read this to understand:**
- Control plane, PostgreSQL, embedded config distribution, gateway runtime, Redis, telemetry
- Component responsibilities, owned state, failure behavior
- Full external request path (client → gateway → upstream)
- Configuration publication and atomic activation flow
- Why PostgreSQL is excluded from the hot request path

> **Implementation note:** Architecture docs describe FastAPI/Go as the original target stack. The shipped implementation is Java/Spring WebFlux in `Server/` with the same component boundaries.

---

### [DISTRIBUTED_SYSTEMS.md](./DISTRIBUTED_SYSTEMS.md)

Failure-scenario-driven design for distributed mechanisms.

**Read this to understand:**
- Versioned config, atomic activation, ACK/NACK, missed updates, gateway restart
- Distributed rate limiting and Redis failure modes
- Deterministic traffic splitting, backend health, timeouts, retries
- Control-plane outage behavior and telemetry backpressure
- Mechanisms intentionally excluded from MVP

Format: Failure scenario → Mechanism → Tradeoff for each topic.

---

### [API_SPEC.md](./API_SPEC.md)

Management REST API specification under `/api/v1`.

**Read this to understand:**
- Endpoints for projects, APIs, routes, policies, config versions (`POST /config/versions`), activation (`POST /config/versions/{version}/activate`)
- Gateway-facing endpoints: registration, heartbeat, `GET /gateway-config/{api_id}/desired`, snapshots, ACK/NACK
- Convergence and health/readiness APIs
- Separation of management plane vs gateway data-plane traffic

---

### [DATA_MODEL.md](./DATA_MODEL.md)

PostgreSQL schema design for the management plane.

**Read this to understand:**
- Tables, columns, keys, indexes, uniqueness constraints
- Draft vs published state ownership
- Tables: `gateway_api_status` (current), `config_activation_events` (history), `operational_events` (control-plane audit)
- Mapping from legacy models to new schema
- Redis key patterns (ephemeral, not in PostgreSQL)

---

### [POLICY_ENGINE.md](./POLICY_ENGINE.md)

Hierarchical policy engine (Phase 14).

**Read this to understand:**
- Organization → project → gateway group → API → route hierarchy
- Policy bundles, immutable revisions, overrides, and effective policy evaluation
- Gateway integration at publish time and cache invalidation

---

### [ROLLOUTS.md](./ROLLOUTS.md)

Gateway groups and progressive runtime rollouts (Phase 12).

**Read this to understand:**
- Label-based gateway group membership and selector precedence
- Progressive rollout stages, deterministic cohort ranking, and manual advance flow
- Effective desired config precedence (rollout assignment vs group vs API default)
- Local smoke script: `./scripts/smoke-phase12.sh`

---

### [MANAGEMENT_AUTH.md](./MANAGEMENT_AUTH.md)

Management-plane identity, RBAC, and scoped credentials (Phase 13).

**Read this to understand:**
- Bootstrap flow, bearer token format, built-in roles, endpoint authorization
- See also [`PHASE13_SECURITY_REVIEW.md`](./PHASE13_SECURITY_REVIEW.md)

---

### [SERVICE_DISCOVERY.md](./SERVICE_DISCOVERY.md)

Dynamic backend membership with lease heartbeats (Phase 10).

---

### [EVENTS.md](./EVENTS.md)

Platform events, transactional outbox, and signed webhooks (Phase 11).

---

### [OBSERVABILITY.md](./OBSERVABILITY.md)

Request correlation, tracing, structured logs, and Prometheus metrics (Phase 9).

---

### [MVP_ROADMAP.md](./MVP_ROADMAP.md)

Original vertical-slice implementation plan (Phases 0–9). **Phases 1–14 are implemented** in `Server/`; AWS deployment and items beyond Phase 14 remain future work.

**Read this to understand:**
- Historical sequencing from single-gateway proxy through observability
- Tasks, affected components, tests, and definition of done per phase
- What was deferred (K8s/Kafka/gRPC before core slice)

---

### [LEGACY_MIGRATION.md](./LEGACY_MIGRATION.md)

File-level classification: KEEP, REFACTOR AS CONCEPT, REWRITE, DELETE FROM NEW ACTIVE ARCHITECTURE.

**Read this to understand:**
- Which legacy paths inform the new design vs must be abandoned
- Controlled rewrite strategy beside `server-old/`
- Migration sequence
- Resume and interview value by role (without invented metrics)

---

## Recommended Reading Order

### For a new contributor

1. [Root README](../README.md) — quick start, architecture, and feature overview
2. **PRODUCT_SPEC.md** — goals and non-goals
3. **ARCHITECTURE.md** + **DISTRIBUTED_SYSTEMS.md** — system shape and failure modes
4. **API_SPEC.md** + **DATA_MODEL.md** — implementation contracts
5. Feature guides — **POLICY_ENGINE.md**, **ROLLOUTS.md**, **SERVICE_DISCOVERY.md**, etc.
6. **LEGACY_MIGRATION.md** — what to ignore in `server-old/`

### For portfolio/interview preparation

1. **PRODUCT_SPEC.md** — elevator pitch and non-goals
2. **ARCHITECTURE.md** — request path diagram
3. **DISTRIBUTED_SYSTEMS.md** — deep-dive talking points
4. **LEGACY_MIGRATION.md** — resume value section
5. **MVP_ROADMAP.md** Phase 8 — failure demonstrations

### For legacy archaeology only

1. **LEGACY_ANALYSIS.md**
2. **LEGACY_MIGRATION.md** classification tables
3. Source: `server-old/` (read-only reference)

---

## Document Relationships

```text
LEGACY_ANALYSIS ──► RENOVATION_OPTIONS ──► PRODUCT_SPEC
                                                │
                    ┌───────────────────────────┼───────────────────────────┐
                    v                           v                           v
              ARCHITECTURE              DISTRIBUTED_SYSTEMS           DATA_MODEL
                    │                           │                           │
                    └─────────────┬─────────────┴─────────────┬─────────────┘
                                  v                           v
                            API_SPEC                    MVP_ROADMAP
                                  │                           │
                                  └───────────┬───────────────┘
                                              v
                                    LEGACY_MIGRATION
```

---

## Constraints Acknowledged Across All Documents

- Project name **AutoAPI** unchanged
- Legacy code preserved in `server-old/` (not deleted)
- Configuration distribution embedded in control plane for MVP
- No unnecessary microservices or resume-driven technology choices
- Distinct from DevNest (orchestration) and Cloud Networking Studio (infrastructure/IaC)
- Every distributed mechanism tied to a documented failure scenario
- MVP feasible for one developer

---

## Next Step After Reading

For a hands-on introduction, follow the [root README quick start](../README.md#getting-started). For a specific feature area, open the guide listed above (policy engine, rollouts, service discovery, etc.).
