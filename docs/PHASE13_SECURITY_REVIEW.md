# Phase 13 Security Review — Management-Plane Identity and RBAC

## Scope

This review covers Phase 13 changes that introduce authenticated management-plane access, role-based authorization, scoped service-account credentials, and bootstrap initialization for AutoAPI's control plane.

## Threat model summary

| Threat | Mitigation |
|--------|------------|
| Unauthenticated control-plane mutation | `ManagementSecurityWebFilter` requires Bearer auth on all `/api/v1/**` routes except explicitly public gateway/service-registration paths |
| Stolen management token | HMAC-SHA256 digest storage with environment pepper; plaintext shown once on create/rotate |
| Over-privileged credentials | Role bindings plus credential scope intersection enforced in `ManagementAuthorizationService` |
| Privilege escalation via delegation | `DelegationValidator` and `requireCanDelegateScopes` gate role/credential creation |
| Gateway/data-plane credential reuse | Management tokens use `aat_` prefix; non-management formats fail parsing |
| Missing route authorization | `ManagementEndpointPolicyRegistry` maps routes to permissions; unregistered routes return 403 |
| Bootstrap replay after init | `BootstrapService` rejects repeat bootstrap once `management_auth_state` is initialized |

## Residual risks

1. **Organization list scoping** — `OrganizationService.list()` returns all organizations; filter auth still requires `organization.read`, but multi-tenant isolation at the service layer is incomplete.
2. **API-scoped routes without project context** — Routes such as `/api/v1/apis/{apiId}/…` authorize at organization scope using the caller's organization, not API ownership lookup. Acceptable for single-org deployments; harden before multi-tenant production.
3. **Domain routers still emit `EventContext.managementApi()`** — Audit events from non-auth routers may show generic actor metadata until migrated to `EventContext.fromPrincipal()`.
4. **No OIDC/JWT** — External identity federation is deferred; only bootstrap and service-account tokens are supported.
5. **Authorization caching** — Permission resolution hits the database per request; acceptable for initial rollout.

## Operational requirements

- Set `AUTOAPI_MANAGEMENT_TOKEN_PEPPER` (≥16 chars) in every non-development environment.
- Set `AUTOAPI_BOOTSTRAP_ADMIN_TOKEN` for first boot only; rotate to service-account credentials after bootstrap.
- Never enable `autoapi.management-auth.security.allow-unauthenticated-development` outside local development.

## Validation performed

- Unit tests: token parser, built-in role matrix, endpoint policy inventory
- Integration tests: bootstrap, scoped viewer isolation, public gateway registration, unauthenticated 401
- Smoke script: `./scripts/smoke-phase13.sh`

## Recommendation

Phase 13 is suitable for controlled rollout when pepper/bootstrap secrets are managed by the deployment platform and smoke-phase13 passes in CI. Follow-up work should prioritize API ownership checks for org-scoped API routes and propagating authenticated principal context into all management event emitters.
