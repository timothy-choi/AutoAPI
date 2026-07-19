# AutoAPI Documentation Index

Renovation planning documentation for transforming AutoAPI from a legacy multi-cloud API generator monolith into a **Distributed API Runtime and Traffic-Management Platform**.

Legacy implementation lives in `server-old/` and is analyzed separately from proposed architecture. **No production code has been modified** as part of this documentation effort.

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

Complete proposed system architecture.

**Read this to understand:**
- FastAPI control plane, PostgreSQL, embedded config distribution, Go gateway, Redis, telemetry
- Component responsibilities, owned state, failure behavior
- Full external request path (client → gateway → upstream)
- Configuration publication and atomic activation flow
- Why PostgreSQL is excluded from the hot request path

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

### [MVP_ROADMAP.md](./MVP_ROADMAP.md)

Vertical-slice implementation plan (Phases 0–9).

**Read this to understand:**
- Sequential phases from single-gateway proxy through AWS deployment
- Tasks, affected components, tests, and definition of done per phase
- What to build first and what to defer (no K8s/Kafka/gRPC before core slice)
- **First Resume-Ready Milestone** (Phases 1, 2A–2C, 3, 4 + basic metrics + one failure demo)

---

### [ROLLOUTS.md](./ROLLOUTS.md)

Gateway groups and progressive runtime rollouts (Phase 12).

**Read this to understand:**
- Label-based gateway group membership and selector precedence
- Progressive rollout stages, deterministic cohort ranking, and manual advance flow
- Effective desired config precedence (rollout assignment vs group vs API default)
- Local smoke script and test commands for Phase 12

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

### For a new contributor implementing the renovation

1. **PRODUCT_SPEC.md** — what we are building and why
2. **LEGACY_ANALYSIS.md** — what existed and what not to repeat
3. **RENOVATION_OPTIONS.md** — confirm direction choice
4. **ARCHITECTURE.md** — system shape
5. **DATA_MODEL.md** + **API_SPEC.md** — implementation contracts
6. **DISTRIBUTED_SYSTEMS.md** — failure-mode rationale
7. **MVP_ROADMAP.md** — where to start coding (Phase 0)
8. **LEGACY_MIGRATION.md** — what to touch and what to ignore in `server-old/`

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
- No production code modified during documentation phase
- Legacy code not deleted
- Configuration distribution embedded in control plane for MVP
- No unnecessary microservices or resume-driven technology choices
- Distinct from DevNest (orchestration) and Cloud Networking Studio (infrastructure/IaC)
- Every distributed mechanism tied to a documented failure scenario
- MVP feasible for one developer

---

## Next Step After Reading

Begin **MVP Roadmap Phase 0**: repository scaffold and legacy freeze, then Phase 1 single-gateway vertical slice. See [MVP_ROADMAP.md](./MVP_ROADMAP.md).
