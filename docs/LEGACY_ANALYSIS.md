# AutoAPI Legacy Analysis

This document reverse-engineers the existing AutoAPI repository from implementation evidence. Status labels:

- **OBSERVED** — clearly implemented in code
- **PARTIAL** — implementation exists but is incomplete or disconnected
- **INFERRED** — architecture suggests an intended capability not fully realized
- **ABANDONED/NONFUNCTIONAL** — broken, obsolete, placeholder, or unused

---

## Repository Snapshot

| Path | Contents |
|------|----------|
| `/README.md` | Placeholder: "An automated API generator is on the way! Coming Soon!" |
| `/server-old/` | Entire legacy backend (~270 files) |
| `/server-old/ApiGenerator/` | Core product module (~171 files, 128 JS modules) |
| `/docs/` | Created during renovation planning (this documentation set) |

**Not found:** root `package.json`, Docker files, `.github/workflows`, test suites, frontend, or any code outside `server-old/`.

---

## Original Project Purpose

**INFERRED (strong):** AutoAPI was intended to be a **multi-cloud automated API generation and deployment platform**. Developers would define data models and endpoints, provision cloud databases and serverless functions, configure API gateways, security, monitoring, documentation, billing, and deploy APIs to AWS, Azure, or Google Cloud.

**OBSERVED evidence:**

- README promises an "automated API generator."
- `server-old/ApiGenerator/` contains extensive cloud provisioning helpers for API Gateway, Lambda/Azure Functions/GCP Cloud Functions, RDS/CosmosDB/Cloud SQL/DynamoDB, API keys, usage plans, and monitoring.
- `Project.js` aggregates models, endpoints, databases, gateway config, serverless functions, security, documentation, monitoring, deployment, and billing into a single project document.
- `ApiCreation/ModelFileExtract.js` validates model JSON against database type catalogs and reads from S3.

**INFERRED (secondary):** The platform also aimed to be a **collaborative developer social network** with groups, messaging, notifications, search, GitHub integration, and billing — similar to a "API builder + Dev community" product.

**OBSERVED evidence:**

- `User.js`, `Group.js`, `Messaging/`, `Notifications/`, `Search/`, `Github/` modules are substantial and wired (partially) into `server.js`.
- `Project.js` includes group project support, contributor tracking, view requests, and activity logs.

---

## Intended Users

**INFERRED:**

1. **Individual developers** building REST APIs from schema definitions without manual cloud wiring (`UserId`, solo project fields in `Project.js`).
2. **Team/group developers** collaborating on shared API projects (`GroupProject`, `GroupId`, `Group.js`).
3. **Operators** managing cloud resources across AWS, Azure, and GCP (`ProjectApiCloudProvider`, `CloudServices/` tree).

There is no evidence of enterprise SSO, RBAC beyond basic user/group membership, or multi-tenant isolation design beyond user/group IDs.

---

## Expected Workflow (Inferred from Code Structure)

```text
1. User registers/logs in (UserAuth) optionally with MFA and GitHub OAuth
2. User or group creates a Project (ProjectController — not wired)
3. User defines Models (schema attributes validated by ModelFileExtract)
4. User configures Endpoints linking models, databases, serverless functions
5. User provisions cloud Database instances (AWS RDS, Azure CosmosDB, GCP Cloud SQL, MongoDB Atlas)
6. User configures ApiSecurityAuth (API keys, OAuth, JWT, rate limits)
7. User provisions cloud ApiGateway (AWS APIGateway, Azure APIM, GCP API Gateway)
8. User deploys serverless function handlers (Lambda, Azure Functions, Cloud Functions)
9. ApiDeployment record captures deployment snapshot (version, base URL, rollback info)
10. ApiDocumentation and ApiMonitoring configured; billing tracked via ProjectBillingManagement
11. Social layer: groups, messaging, notifications, search indexing
```

**OBSERVED gap:** Steps 2–10 have controllers and services but **no orchestration service** connects them. Step 11 is partially wired in `server.js` but cannot boot.

---

## Existing Architecture

```text
                    ┌─────────────────────────────────────┐
                    │  server-old/server.js (Express)      │
                    │  PARTIAL — cannot start as-is        │
                    └──────────────┬──────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │                         │                         │
         v                         v                         v
  Social/Collab Layer        ApiGenerator (UNWIRED)     Config
  User, Group, Messaging,  Model/Endpoint/Database/   postgres.js
  Notifications, Search,   Gateway/Deployment/Cloud    mongodb.js
  Github, Billing, OpenAI  (128 JS modules)            database.js MISSING
```

### Persistence Split (OBSERVED, broken)

| Store | Intended use | Models |
|-------|-------------|--------|
| PostgreSQL (Sequelize) | Relational project metadata | `Project`, `Model`, `ApiDeployment`, `ProjectStats`, `ProjectManagement`, `ProjectBillingManagement`, `User`, `UserAuth`, `Group`, etc. |
| MongoDB (Mongoose) | Document-style API artifacts | `Endpoints`, `Database`, `ApiGateway`, `ApiDocumentation`, `ApiMonitoring`, `ApiSecurityAuth`, `ApiServerlessFunction`, `ApiTesting` |

**ABANDONED/NONFUNCTIONAL:** All Sequelize models in `ApiGenerator/` require `../config/database`, which **does not exist**. Only `config/postgres.js` exists and is used by `server.js`. The two ORM configurations were never unified.

---

## Module and Service Inventory

### Entry Point — `server-old/server.js` (PARTIAL)

**OBSERVED mounted routes:**

| Mount | Module |
|-------|--------|
| `/userAuth` | `UserAuth/UserAuthRouter.js` |
| `/mfa` | `Mfa/MfaRouter.js` |
| `/notificationAccount` | `Notifications/NotificationAccountRouter.js` |
| `/search` | `Search/SearchRouter.js` |
| `/user` | `User/UserRouter.js` |
| `/group` | `Group/GroupRouter.js` |
| `/userStats` | `User/UserStatsRouter.js` |
| `/notification` | `Notifications/NotificationRouter.js` |
| `/emailNotification` | `Notifications/EmailNotificationsRouter.js` |
| `/notificationWorkflow` | `Notifications/NotificationWorkflowController.js` *(wrong: controller not router)* |
| `/message` | `Messaging/MessageRouter.js` |
| `/messaging` | `Messaging/MessagingController.js` *(wrong: controller not router)* |
| `/messagingSession` | `Messaging/MessagingSessionRouter.js` |
| `/messagingAccount` | `Messaging/MessagingAccountRouter.js` |
| `/github/OAuth` | `Github/GithubOAuthRouter.js` |

**ABANDONED:** Zero imports from `ApiGenerator/`.

**Blocking bugs (OBSERVED):**

- Line 1: `require('express.js')` — invalid module name
- Line 16–18: controllers mounted where routers expected
- Line 41: `saveUninitalized` typo
- Line 92–96: `sequelize.sync().catch((err) => {})` swallows DB errors
- `package.json` declares only `express` and `nodemon`; dozens of imports missing

### ApiGenerator Core (PARTIAL / ABANDONED from HTTP perspective)

| Component | Files | Status |
|-----------|-------|--------|
| Project CRUD | `Project.js`, `ProjectService.js`, `ProjectController.js` | PARTIAL — no router |
| Model CRUD | `Model.js`, `ModelService.js`, `ModelController.js` | PARTIAL — routed in `ProjectRouter.js` (unmounted) |
| Database CRUD | `Database.js`, `DatabaseService.js`, `DatabaseController.js` | PARTIAL — routed (unmounted) |
| Endpoints CRUD | `Endpoints.js`, `EndpointService.js`, `EndpointsController.js` | PARTIAL — routes in `ProjectRouter.js` but `EndpointController` not imported; typo `EndpointControllerGgetEndpointsById` |
| API Gateway metadata | `ApiGateway.js`, `ApiGatewayService.js`, `ApiGatewayController.js` | PARTIAL — MongoDB CRUD only, no router |
| Deployment metadata | `ApiDeployment.js`, `ApiDeploymentService.js`, `ApiDeploymentController.js` | PARTIAL — Sequelize/Mongoose API confusion |
| Documentation metadata | `ApiDocumentation.js`, `ApiDocumentationService.js` | PARTIAL — no generation logic |
| Monitoring metadata | `ApiMonitoring.js`, `ApiMonitoringService.js` | PARTIAL — broken export `module.exports = ApiMonitoring, ApiSchema` |
| Security auth metadata | `ApiSecurityAuth.js`, `ApiSecurityService.js` | PARTIAL |
| Serverless metadata | `ApiServerlessFunction.js`, `ApiServerlessFunctionService.js` | PARTIAL |
| API testing | `ApiTesting.js` | ABANDONED — model only, forward-reference bug prevents load |
| Project stats | `ProjectStats.js`, `ProjectStatsService.js` | PARTIAL |
| Project management | `ProjectManagement.js`, `ProjectManagementService.js` | PARTIAL |
| Billing | `ProjectBillingManagement.js`, `ProjectBillingManagementService.js` | PARTIAL — not connected to Stripe/PayPal modules |
| Model file I/O | `ApiCreation/ModelFileExtract.js`, `ModelFileContentUpdate.js` | PARTIAL — S3 via `aws-helper.js`, not routed |

**Only router:** `ProjectRouter.js` — covers Model, Database, Endpoints only.

### Cloud Services — `server-old/ApiGenerator/CloudServices/` (PARTIAL)

#### AWS (`CloudServices/AWS/`) — **OBSERVED: most complete**

| Area | Key symbols | Implementation |
|------|-------------|----------------|
| API Gateway | `ApiGatewayHelper.createApiGateway`, `createResource`, `createMethod`, `deleteApiGateway` | Real `aws-sdk` APIGateway with polling (`waitUntil`) |
| Lambda | `LambdaHelper.js`, `LambdaController.js` | Real Lambda SDK operations |
| Security | `SecurityAuthService.createApiKey`, `createUsagePlan`, `linkApiKeyToUsagePlan` | Real API Gateway security |
| Monitoring | `ApiMonitoringHelper.js` | CloudTrail integration |
| Database | `RDSController.js`, `DynamoDBController.js`, `MongoDBOperationsController.js` | Real SDK helpers + embedded Lambda functions |
| Infra | `AWSHelperFunctions.js` | VPC, subnets, EC2, S3 (~500 lines) |
| OAuth | `AwsOauthFlow.js`, `AWSOauthHelperFunction/` | OAuth flow scaffolding |

Embedded Lambda subprojects: `LambdaMetricsFunction/`, `RDSMetricsLambdaFunction/`, `DynamoDBMetricsFunction/`, `MongoDBDataOperationHandler/`, `AWSLogMonitoringFunction/`.

#### Azure (`CloudServices/Azure/`) — **OBSERVED: substantial**

| Area | Key symbols |
|------|-------------|
| API Gateway | `@azure/arm-apimanagement` in `ApiGatewayHelper.js` |
| Serverless | `ServerlessFunctionHelper.js`, `AzureTriggerFunction/` |
| Database | `CosmosDBHelper.js`, `AzureRDSController.js`, SQL/PostgreSQL/MySQL helpers |
| Monitoring | `ApiMonitoringLogService.js`, `AzureLoggingFunction/` |
| Security | `SecurityAuthService.js` |

#### Google Cloud (`CloudServices/GoogleCloud/`) — **PARTIAL**

| Area | Status |
|------|--------|
| API Gateway | OBSERVED — REST via axios in `ApiGatewayHelper.js` |
| Cloud SQL | OBSERVED — `RelationalDBHelper.js` |
| Cloud Functions | PARTIAL — `ServerlessFunctionHelper.js` |
| Logging/Monitoring | PARTIAL — `GCloudHelperOperations.js` references undefined `PROJECT_ID`, `CloudSchedulerClient` |
| Deploy | PARTIAL — `deployCloudFunction` uses `exec('gcloud ...')` without awaiting |

#### MongoDB Atlas (`CloudServices/MongoDB/`) — **OBSERVED**

- `MongoDBInstanceHelper.js` — cluster/project CRUD via Atlas API
- `MongoDBController.js` — HTTP handlers (unrouted)
- `MongoDBMetricsFunction/` — metrics Lambda

**Critical distinction:** Cloud `ApiGatewayHelper` modules provision **managed cloud API gateways** (AWS API Gateway, Azure APIM, GCP API Gateway). They do **not** implement a custom L7 reverse-proxy data plane. Gateway metadata in `ApiGateway.js` stores routes/throttling as MongoDB documents unrelated to live request proxying.

### Social and Platform Modules (PARTIAL)

| Module | Path | Purpose | Wired? |
|--------|------|---------|--------|
| UserAuth | `UserAuth/` | Registration, login, bcrypt passwords, MFA secrets | Yes |
| MFA | `Mfa/` | TOTP verify/enable/disable via speakeasy | Yes |
| User | `User/` | Rich user profile, projects, groups, social graph (~58 endpoints) | Yes |
| UserStats | `User/UserStats*` | Per-user API usage statistics | Yes |
| Group | `Group/` | Group projects, join requests, chatroom refs | Yes |
| Messaging | `Messaging/` | Messages, sessions, accounts REST | Yes (miswired) |
| WebSocket | `Messaging/WebSocketHandler.js` | Real-time chat on port 8020, Kafka producer | **No** |
| Notifications | `Notifications/` | Push (web-push), email (nodemailer), workflows | Yes (broken exports) |
| Search | `Search/` | Elasticsearch indexing for users/groups/projects | Yes |
| Github | `Github/` | OAuth + Octokit REST helpers | OAuth yes; REST controller no |
| Billing | `Billing/Stripe/`, `Billing/PayPal/` | Stripe Connect, PayPal OAuth | **No** |
| OpenAI | `OpenAI/` | Chat completion helpers | **No** |

### Shared Utilities

| File | Status | Purpose |
|------|--------|---------|
| `middleware.js` | PARTIAL | JWT `AuthenticateToken` — only used by unrouted workflow router |
| `passportUtils.js` | PARTIAL | GitHub token refresh; missing `axios` import |
| `aws-helper.js` | ABANDONED | S3 bucket/file operations; only used by ApiCreation |
| `RabbitMQHelper.js` | ABANDONED | `sendMessage`, `consumeMessage` via amqplib; never imported |
| `config/postgres.js` | OBSERVED | Sequelize instance on `POSTGRES_URI` |
| `config/mongodb.js` | OBSERVED | Mongoose connect on `MONGODB_URI` |
| `config/database.js` | **MISSING** | Required by all Sequelize models |

---

## Existing Data Model

### Central Aggregate — `Project` (Sequelize, `Project.js`)

**OBSERVED key fields:**

- Identity: `Id` (UUID PK), `ProjectApiName`, `ProjectVersion`, `ProjectApiType`, `ProjectApiCloudProvider`
- Ownership: `UserId`, `GroupId`, `GroupProject`, `IsPrivate`
- Status: `ProjectStatus`, `ProjectHealthStatus`, `IsAvailable`
- Embedded JSONB arrays: `AllProjectModels`, `AllEndpoints`, `AllDatabases`, `ApiGateway`, `ApiServerlessFunctions`, `ApiSecurityAuth`, `ApiDocumentation`, `ApiMonitoringInfo`, `ApiDeploymentInfo`, `ApiTesting`
- Collaboration: `ProjectContributors`, `ProjectViewRequestsRecieved`, `AwaitingUserProjectUserRequests`
- External: `GithubUrl`, `GithubProjectRepoInfo`, `AllProjectFileBucket`, `ProjectBucket`
- References: `ProjectStatsId`, `ProjectManagementId`

Design pattern: **denormalized project blob** with embedded copies of sub-resources rather than normalized foreign keys.

### Mongoose Document Models (ApiGenerator)

| Model | File | Key fields |
|-------|------|------------|
| Endpoints | `Endpoints.js` | `ProjectId`, `EndpointType`, headers, models, databases, serverless function refs, response schemas |
| Database | `Database.js` | `Name`, `Type`, `ModelTablesInfo`, `DatabaseCloudInfo`, backup/billing |
| ApiGateway | `ApiGateway.js` | `ProjectId`, `Routes`, `Throttling`, `Subscription`, `DeploymentStatus` |
| ApiSecurityAuth | `ApiSecurityAuth.js` | `AuthenticationType`, `ApiKeyInfo`, `OAuthInfo`, `JwtInfo`, `RateLimit` |
| ApiMonitoring | `ApiMonitoring.js` | `MonitoringServiceInfo`, `MonitoringLogs` (request/response/latency) |
| ApiServerlessFunction | `ApiServerlessFunction.js` | `FunctionName`, `Runtime`, `Routes`, `DeployedUrl`, metrics |
| ApiDocumentation | `ApiDocumentation.js` | `Endpoints`, `Authentication`, `RateLimit`, `DocumentationInfo` |
| ApiTesting | `ApiTesting.js` | Postman-style collection schemas (**won't load** — forward reference) |

### Sequelize Supporting Models

| Model | File | Purpose |
|-------|------|---------|
| Model | `Model.js` | Schema definition with `ModelAttributes` JSONB array |
| ApiDeployment | `ApiDeployment.js` | Deployment snapshot with rollback info |
| ProjectStats | `ProjectStats.js` | Views, API calls, error rates, geo distribution |
| ProjectManagement | `ProjectManagement.js` | Admin logs, changelog, error/security logs |
| ProjectBillingManagement | `ProjectBillingManagement.js` | STRIPE/PAYPAL enum, bills, usage reports |

### User/Group Social Models

- `User.js` — ~30 fields including JSONB arrays for projects, groups, followers, payment info
- `Group.js` — group membership, join requests, project refs, chatroom
- `UserAuth.js` — credentials separate from profile
- Messaging models: `Message.js`, `Messaging.js`, `MessagingSession.js`, `MessagingAccount.js`
- Notification models: `Notification.js`, `NotificationAccount.js`, `EmailNotifications.js`

---

## Existing API Surface

### Wired (but non-bootable) — `server.js`

Social/collaboration REST endpoints under mounts listed above. Example patterns from `UserRouter.js`:

- `GET /user/:userId`, `POST /user`, `PUT /user/:userId/...` (58 routes)
- `GroupRouter.js` — 18 group management routes
- `UserAuthRouter.js` — `/register`, `/login`, `/logout`, CRUD

### Unwired — `ProjectRouter.js` (ApiGenerator)

**Model routes (OBSERVED):**

- `GET /Model/:modelId`, `GET /Model/name/:modelName`
- `POST /Model`
- `PUT /Model/:modelId/Version/:version`, attribute CRUD, description, creation file, database info, changelog
- `DELETE /Model/:modelId`

**Database routes:** ~20 PUT routes for metadata mutation + GET/POST/DELETE

**Endpoints routes:** ~30 routes (broken import for `EndpointController`)

### Unwired Cloud Controllers (~30 files)

Each expects `req.body` with cloud credentials and resource parameters. Example: `CloudServices/AWS/ApiGateway/ApiGatewayController.js` wraps `ApiGatewayHelper.createApiGateway`.

No unified API prefix, no authentication middleware on ApiGenerator controllers.

---

## Infrastructure Integrations

| Technology | Usage | Status |
|------------|-------|--------|
| PostgreSQL | Sequelize via `config/postgres.js` | PARTIAL — models require missing `database.js` |
| MongoDB | Mongoose via `config/mongodb.js` | OBSERVED connection; ApiGenerator models use it |
| Redis | `MessagingSessionTracker.js` — session hash store | ABANDONED — not wired to server |
| RabbitMQ | `RabbitMQHelper.js` | ABANDONED |
| Kafka | `WebSocketHandler.js` producer | ABANDONED |
| Elasticsearch | `Search/SearchClient.js` | PARTIAL — top-level await, empty node URL |
| AWS S3 | `aws-helper.js` | PARTIAL — used by model file extract |
| AWS SDK | Extensive in CloudServices/AWS | OBSERVED in helpers, unrouted |
| Azure SDK | CloudServices/Azure | OBSERVED |
| GCP APIs | CloudServices/GoogleCloud | PARTIAL |
| MongoDB Atlas API | CloudServices/MongoDB | OBSERVED |
| web-push | VAPID push notifications | PARTIAL |
| nodemailer | Email notifications | PARTIAL |
| Stripe | `Billing/Stripe/StripeAccountHelper.js` | ABANDONED |
| PayPal | `Billing/PayPal/PayPal.js` | ABANDONED |
| OpenAI | `OpenAI/OpenAIHelper.js` (ESM) | ABANDONED |
| GitHub OAuth | passport-github2 in server.js | PARTIAL |
| Octokit | `Github/GithubHelper.js` | ABANDONED (controller unrouted) |

**Not found:** Docker, Kubernetes, Terraform, Prometheus, Redis rate limiting, custom gateway binary, gRPC, GitHub Actions CI.

---

## API Gateway Functionality (Legacy)

Two distinct concepts exist:

### 1. Metadata Layer — `ApiGateway.js` / `ApiGatewayService.js` (PARTIAL)

**OBSERVED:** MongoDB document CRUD for `Routes`, `Throttling`, `Subscription`, `DeploymentStatus`, `SecurityInfo`.

**NOT OBSERVED:** HTTP request handling, reverse proxying, route matching on live traffic, configuration propagation to nodes.

### 2. Cloud Provisioning Layer — `CloudServices/*/ApiGateway/` (PARTIAL)

**OBSERVED:** SDK calls to create/manage **vendor-managed** API gateways:

- AWS: `createRestApi`, `createResource`, `createMethod` (`ApiGatewayHelper.js`)
- Azure: APIM service/API/operation creation
- GCP: API Gateway REST resource creation

**INFERRED intent:** AutoAPI would provision cloud-native gateways as the runtime for generated APIs, not operate its own gateway fleet.

This is fundamentally different from the proposed renovation direction (custom Go gateway data plane).

---

## Deployment Functionality

**OBSERVED:** `ApiDeployment.js` stores deployment snapshots: `Version`, `BaseUrl`, `Status`, `Environment`, `DeploymentTarget`, `RollbackInfo`, embedded copies of models/endpoints/gateway config.

**OBSERVED:** `ApiDeploymentService.js` provides CRUD setters (`setVersion`, `setBaseUrl`, `addDeploymentLog`, etc.).

**NOT OBSERVED:**

- Deployment orchestrator connecting Project → cloud provisioning → deployment record
- CI/CD pipeline, blue/green, canary
- Configuration versioning with gateway ACK/NACK
- Immutable snapshot publication

**ABANDONED/NONFUNCTIONAL:** Service uses Mongoose APIs (`.findById`, `.save`, `.destroy`, `mongoose.types.ObjectId`) on a Sequelize model. `createApiDeployment` references undefined `ProjectId`.

---

## Monitoring

### App-level — `ApiMonitoring.js` (PARTIAL)

Stores `MonitoringLogs` with request/response/status/latency. Service has Sequelize-style queries on Mongoose and broken exports.

### Cloud-level (PARTIAL)

- AWS CloudTrail: `ApiMonitoringHelper.js`
- Azure Log Analytics: `ApiMonitoringLogService.js`
- GCP Pub/Sub logging sinks: `GCloudMonitoringService.js`

**NOT OBSERVED:** Unified pipeline from cloud logs to app monitoring store. No Prometheus, no gateway request metrics, no distributed tracing.

---

## Authentication and Security

### Platform auth (PARTIAL)

- Session + passport GitHub OAuth (`server.js`)
- JWT in `middleware.js` `AuthenticateToken` (checks session token or Authorization header)
- UserAuth with bcrypt (`UserAuthService.js`)
- MFA with speakeasy TOTP (`MfaController.js`) — bugs: wrong variable names, no auth middleware

### API security config (PARTIAL, metadata only)

- `ApiSecurityAuth.js`: stores API key, OAuth, JWT config and rate limit metadata
- Cloud helpers create real AWS API keys and usage plans

**NOT OBSERVED:** Gateway-enforced API key validation on live requests, distributed rate limiting, mTLS.

---

## Testing Infrastructure

**ABANDONED:** `ApiTesting.js` defines Postman-style test collection schemas but has no service, controller, or router. Model has forward-reference bug (`PostmanApiFolderSchema` used before definition).

**NOT FOUND:** Unit tests, integration tests, CI test runs anywhere in repository.

---

## Architectural Debt

1. **Dual ORM split without unified config** — Sequelize models require missing `config/database.js`; MongoDB models separate; services mix APIs.
2. **ApiGenerator entirely unwired** — 90%+ of product code unreachable from HTTP.
3. **No dependency management** — `package.json` lists only `express`; ~25 packages imported.
4. **Copy-paste defect patterns** — `modules.export` typos, `DateTypes`, `.remove()` on arrays, `throw new Exception`, `res.status().body()`, inverted null checks, filter without reassignment.
5. **No orchestration layer** — cloud helpers exist in isolation; no workflow connects model → deploy → gateway.
6. **Denormalized project blob** — scalability and consistency challenges; no versioned immutable config.
7. **Social + API builder scope creep** — two products in one monolith.
8. **Server cannot boot** — `express.js` import, wrong router/controller wiring, missing modules.
9. **Cloud credentials in request body** — no secrets management pattern observed.
10. **Embedded Lambda/function subprojects** — 43 nested `package.json` files with no monorepo tooling or deployment automation.

---

## Dead or Disconnected Code

| Path | Reason |
|------|--------|
| Entire `ApiGenerator/` (from HTTP) | Not imported in `server.js` |
| All `CloudServices/*/Controller.js` | No routers |
| `ProjectController.js`, `ApiDeploymentController.js`, etc. | No routers |
| `ApiCreation/*.js` | Not routed |
| `Billing/Stripe/`, `Billing/PayPal/` | Not mounted |
| `OpenAI/` | Not mounted; ESM/CJS mismatch |
| `Github/GithubController.js` | No router |
| `Messaging/WebSocketHandler.js` | Never required |
| `Messaging/MessagingSessionTracker.js` | Only used by WebSocket handler |
| `RabbitMQHelper.js` | Never imported |
| `aws-helper.js` | Only ApiCreation (itself unrouted) |
| `middleware.js` | Only unrouted notification workflow |
| `ApiTesting.js` | Model-only, load error |

---

## Summary: Legacy Identity vs Renovation Target

| Dimension | Legacy AutoAPI | Proposed Renovation |
|-----------|---------------|---------------------|
| Core value | Generate and deploy APIs to cloud vendor gateways | Operate custom L7 gateway fleet |
| Data plane | Cloud-managed (AWS APIGW, APIM, GCP APIGW) | Self-hosted Go reverse proxy |
| Config model | Denormalized project blobs + MongoDB docs | Immutable versioned snapshots |
| Distributed systems focus | Minimal (Redis sessions, Kafka planned) | Config propagation, convergence, global rate limits |
| Scope | API builder + social platform + billing | API runtime and traffic management |

The legacy codebase provides **conceptual overlap** (routes, API keys, rate limits, gateway metadata, deployment snapshots) but **no reusable data-plane implementation** for the renovation target.
