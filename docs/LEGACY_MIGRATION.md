# AutoAPI Legacy Migration Guide

Classification of legacy files under `server-old/` for the renovation to a **Distributed API Runtime and Traffic-Management Platform**. Legacy code is **not deleted** during migration; it remains as archaeological reference.

**Recommended approach:** Controlled rewrite beside legacy code in new directories (`control-plane/`, `gateway/`). Do not attempt to repair `server-old/` in place.

---

## Classification Key

| Action | Meaning |
|--------|---------|
| **KEEP** | Retain as-is in `server-old/` for reference; no active use |
| **REFACTOR AS CONCEPT** | Legacy artifact informs new design; reimplemented cleanly |
| **REWRITE** | New implementation required; legacy code not portable |
| **DELETE FROM NEW ACTIVE ARCHITECTURE** | Must not carry forward into renovated system (legacy files stay on disk) |

---

## Top-Level

| Path | Classification | Rationale |
|------|----------------|-----------|
| `README.md` | REWRITE | Replace with renovation description pointing to `docs/` |
| `server-old/` | KEEP | Entire legacy tree frozen |
| `server-old/package.json` | DELETE FROM NEW | New per-component dependency files |
| `.gitignore` | REFACTOR AS CONCEPT | Extend for Python, Go, new env patterns |

---

## Entry Point and Config

| Path | Classification | Rationale |
|------|----------------|-----------|
| `server-old/server.js` | DELETE FROM NEW | Broken Express monolith; `require('express.js')`; wrong router wiring |
| `server-old/config/postgres.js` | REFACTOR AS CONCEPT | PostgreSQL remains; new SQLAlchemy config in `control-plane/` |
| `server-old/config/mongodb.js` | DELETE FROM NEW | Renovated model is PostgreSQL-only for management state |
| `server-old/config/database.js` | N/A | Missing file; symbol of broken Sequelize setup |
| `server-old/middleware.js` | REFACTOR AS CONCEPT | JWT auth pattern → operator auth in FastAPI (REWRITE) |
| `server-old/passportUtils.js` | DELETE FROM NEW | GitHub OAuth not in MVP scope |
| `server-old/aws-helper.js` | DELETE FROM NEW | S3 model files not in renovation scope |
| `server-old/RabbitMQHelper.js` | DELETE FROM NEW | No message queue in new architecture |

---

## ApiGenerator Core

| Path | Classification | Rationale |
|------|----------------|-----------|
| `ApiGenerator/Project.js` | REFACTOR AS CONCEPT | "Project" organizing unit → `projects` + `apis` tables |
| `ApiGenerator/ProjectService.js` | REWRITE | Denormalized blob mutations → normalized CRUD + publish |
| `ApiGenerator/ProjectController.js` | REWRITE | FastAPI routers |
| `ApiGenerator/ProjectRouter.js` | REFACTOR AS CONCEPT | Route patterns inform `API_SPEC.md`; only unwired router |
| `ApiGenerator/Model.js` | DELETE FROM NEW | Schema/codegen model not in gateway MVP |
| `ApiGenerator/ModelService.js` | DELETE FROM NEW | |
| `ApiGenerator/ModelController.js` | DELETE FROM NEW | |
| `ApiGenerator/Endpoints.js` | REFACTOR AS CONCEPT | Endpoint metadata → `routes` table |
| `ApiGenerator/EndpointService.js` | REWRITE | |
| `ApiGenerator/EndpointsController.js` | REWRITE | |
| `ApiGenerator/Database.js` | DELETE FROM NEW | Cloud database provisioning out of scope |
| `ApiGenerator/DatabaseService.js` | DELETE FROM NEW | |
| `ApiGenerator/DatabaseController.js` | DELETE FROM NEW | |
| `ApiGenerator/ApiGateway.js` | REFACTOR AS CONCEPT | Routes/throttling metadata → snapshot routes + policies |
| `ApiGenerator/ApiGatewayService.js` | REWRITE | MongoDB CRUD → publish pipeline |
| `ApiGenerator/ApiGatewayController.js` | REWRITE | |
| `ApiGenerator/ApiDeployment.js` | REFACTOR AS CONCEPT | Versioned deployment → `config_versions` |
| `ApiGenerator/ApiDeploymentService.js` | REWRITE | Broken Sequelize/Mongoose mix |
| `ApiGenerator/ApiDeploymentController.js` | REWRITE | |
| `ApiGenerator/ApiDocumentation.js` | DELETE FROM NEW | No doc generation in renovation MVP |
| `ApiGenerator/ApiDocumentationService.js` | DELETE FROM NEW | |
| `ApiGenerator/ApiMonitoring.js` | REFACTOR AS CONCEPT | Monitoring → Prometheus, not MongoDB logs |
| `ApiGenerator/ApiMonitoringService.js` | REWRITE | |
| `ApiGenerator/ApiSecurityAuth.js` | REFACTOR AS CONCEPT | API keys + rate limit → `api_keys`, `rate_limit_policies` |
| `ApiGenerator/ApiSecurityService.js` | REWRITE | |
| `ApiGenerator/ApiServerlessFunction.js` | DELETE FROM NEW | Serverless provisioning out of scope |
| `ApiGenerator/ApiServerlessFunctionService.js` | DELETE FROM NEW | |
| `ApiGenerator/ApiTesting.js` | DELETE FROM NEW | Broken model; no test runner |
| `ApiGenerator/ProjectStats.js` | REFACTOR AS CONCEPT | Stats → Prometheus metrics |
| `ApiGenerator/ProjectManagement.js` | DELETE FROM NEW | Admin/changelog not MVP |
| `ApiGenerator/ProjectBillingManagement.js` | DELETE FROM NEW | Billing out of scope |
| `ApiGenerator/ApiCreation/ModelFileExtract.js` | DELETE FROM NEW | Codegen path abandoned |
| `ApiGenerator/ApiCreation/ModelFileContentUpdate.js` | DELETE FROM NEW | |

---

## Cloud Services (Entire Tree)

| Path | Classification | Rationale |
|------|----------------|-----------|
| `ApiGenerator/CloudServices/AWS/**` | DELETE FROM NEW | Vendor provisioning; antithetical to self-hosted gateway |
| `ApiGenerator/CloudServices/Azure/**` | DELETE FROM NEW | |
| `ApiGenerator/CloudServices/GoogleCloud/**` | DELETE FROM NEW | |
| `ApiGenerator/CloudServices/MongoDB/**` | DELETE FROM NEW | |

**Note:** `CloudServices/AWS/ApiGateway/ApiGatewayHelper.js` (`createRestApi`, `createResource`) is useful **historical context** for what legacy considered "API gateway" — managed cloud resource, not L7 data plane.

Embedded Lambda/function subprojects (43 `package.json` files): **KEEP** in legacy, **DELETE FROM NEW**.

---

## Social and Platform Modules

| Path | Classification | Rationale |
|------|----------------|-----------|
| `User/User.js` | DELETE FROM NEW | Social platform out of scope |
| `User/UserService.js` | DELETE FROM NEW | |
| `User/UserController.js` | DELETE FROM NEW | |
| `User/UserRouter.js` | DELETE FROM NEW | |
| `User/UserStats.js` | DELETE FROM NEW | |
| `UserAuth/UserAuth.js` | REFACTOR AS CONCEPT | Operator auth only (simplified `users` table) |
| `UserAuth/UserAuthService.js` | REWRITE | |
| `Mfa/MfaRouter.js` | DELETE FROM NEW | MFA not MVP |
| `Group/Group.js` | DELETE FROM NEW | |
| `Messaging/**` | DELETE FROM NEW | |
| `Messaging/WebSocketHandler.js` | DELETE FROM NEW | |
| `Messaging/MessagingSessionTracker.js` | REFACTOR AS CONCEPT | Redis usage pattern only — not session tracking |
| `Notifications/**` | DELETE FROM NEW | |
| `Search/**` | DELETE FROM NEW | |
| `Github/**` | DELETE FROM NEW | |
| `Billing/Stripe/**` | DELETE FROM NEW | |
| `Billing/PayPal/**` | DELETE FROM NEW | |
| `OpenAI/**` | DELETE FROM NEW | |

---

## Controlled Rewrite Strategy

### Why Not Repair Legacy?

1. `server.js` cannot boot; `package.json` missing ~25 dependencies
2. ApiGenerator unwired — 90%+ dead from HTTP
3. Systemic ORM confusion (Sequelize models call Mongoose APIs)
4. Architectural mismatch: cloud provisioner vs custom gateway data plane
5. Scope contamination: social + billing + API builder in one monolith

### Directory Layout (New)

```text
AutoAPI/
  server-old/          # KEEP frozen — do not import from new code
  control-plane/       # Python FastAPI
  gateway/             # Go data plane
  deploy/              # docker-compose, AWS, demo scripts
  tests/               # integration tests
  docs/                # architecture documentation
```

### Import Boundary Rule

New code **must not** `require()` or import from `server-old/`. Copy concepts, not code.

### Optional Legacy Archive (Post-Renovation)

After MVP stable, consider git tag `legacy-pre-renovation` or move `server-old/` → `legacy/server-old-v1/` in a dedicated commit. Not required for Phase 0.

---

## Migration Sequence

| Step | Action | Outcome |
|------|--------|---------|
| 1 | Complete documentation (this set) | Shared understanding |
| 2 | Phase 0 scaffold | New dirs, legacy frozen |
| 3 | Implement gateway Phase 1 | Working proxy without legacy |
| 4 | Implement control plane Phase 2 | Publication pipeline |
| 5 | Phases 3–8 | Distributed features |
| 6 | Phase 9 AWS | Production-like deployment |
| 7 | Update root README | Describe renovated product |
| 8 | Optional legacy archive tag | Clean contributor experience |

No step requires deleting or modifying `server-old/` until explicitly chosen later.

---

## Concept Preservation Map

| Legacy concept | Renovated artifact |
|----------------|-------------------|
| `Project.ProjectApiName` | `apis.name`, `apis.host` |
| `Endpoints` path/method | `routes.path_prefix`, `routes.methods` |
| `ApiGateway.Routes` | Published snapshot `routes[]` |
| `ApiGateway.Throttling` | `rate_limit_policies` |
| `ApiSecurityAuth.ApiKeyInfo` | `api_keys` + gateway auth middleware |
| `ApiSecurityAuth.RateLimit` | Redis distributed rate limit |
| `ApiDeployment.Version`, `RollbackInfo` | `config_versions`, rollback API |
| `Project.ApiGateway` JSONB embed | Normalized tables + immutable snapshot |
| Cloud gateway provisioning | **Replaced by** Go reverse proxy fleet |

---

## Resume and Interview Value

This section describes **authentic engineering themes** demonstrated by the renovation. It does not invent metrics, user counts, or production claims.

### Backend Software Engineer

- Design and implement FastAPI management REST API with validation, publication workflow, and relational schema
- Build Go HTTP middleware chain: routing, auth, rate limiting, proxy, timeouts
- Implement request lifecycle concerns: IDs, deadlines, error mapping to HTTP status codes
- **Interview topics:** REST API design, middleware ordering, idempotent retries, connection pool management

### Cloud Infrastructure Software Engineer

- Docker Compose local stack; AWS deployment with RDS, ElastiCache, ALB (Phase 9)
- Infrastructure-as-code for gateway fleet and control plane
- Secrets management and network segmentation (gateway tier vs data tier)
- **Interview topics:** Why ALB for L7 ingress vs NLB for L4 pass-through; RDS availability impact on control plane only

### Platform Engineer

- Gateway registration, heartbeat, convergence reporting
- Operator workflows: validate → create version → activate → observe ACK/NACK → rollback (activate prior)
- Configuration versioning as platform contract between control and data plane
- **Interview topics:** Platform API design; how to onboard a new gateway node; blast radius of bad config

### Distributed Systems Software Engineer

- Immutable versioned snapshots and atomic activation
- Eventual convergence with ACK/NACK feedback
- Missed update handling (jump to latest snapshot)
- Distributed rate limiting with Redis and explicit failure modes
- Control-plane outage isolation
- **Interview topics:** CAP tradeoffs for config distribution; compare polling vs streaming; stale gateway handling

### Cloud Networking Software Engineer

- L7 host/path/method routing and reverse proxy semantics
- Traffic splitting with deterministic hashing (canary stickiness)
- Health-aware backend selection and timeout/cancellation
- Global vs per-node rate limit coordination
- **Interview topics:** Difference from service mesh and vendor API Gateway; retry amplification; when to fail-open vs fail-closed on Redis

### Differentiation from Other Portfolio Projects

| Project | Focus | AutoAPI renovation avoids |
|---------|-------|---------------------------|
| DevNest | Workspace orchestration, node reconciliation | Generic orchestration, deployment workers |
| Cloud Networking Studio | Infrastructure topology, Terraform, drift | IaC generation, K8s reconciliation |
| **AutoAPI** | L7 API request path, traffic policy, gateway fleet | Overlap intentionally minimized |

---

## Summary Counts

| Classification | Approximate file count (legacy) |
|----------------|--------------------------------|
| KEEP (frozen reference) | ~270 |
| REFACTOR AS CONCEPT | ~15 core ApiGenerator models |
| REWRITE | All services, controllers, new stack |
| DELETE FROM NEW ACTIVE ARCHITECTURE | ~200+ (cloud, social, billing, messaging) |

The renovated AutoAPI is a **rewrite with conceptual lineage**, not a refactor of `server-old/`.
