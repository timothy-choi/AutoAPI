# Management Authentication and RBAC (Phase 13)

AutoAPI's management plane requires authenticated identity for all `/api/v1/**` control-plane routes except explicitly public gateway and service-registration endpoints.

## Identity model

- **Organizations** are the top-level tenant boundary. Existing projects are associated with a default organization at migration time.
- **Management principals** may be `USER`, `SERVICE_ACCOUNT`, `BOOTSTRAP_ADMIN`, or internal `SYSTEM` actors.
- **Role bindings** grant built-in roles at organization or project scope.
- **Scoped credentials** further restrict effective permissions; both role grants and credential scopes must allow an action.

## Token format

Management tokens use the prefix configured in `autoapi.management-auth.token.prefix` (default `aat`):

```text
aat_<publicTokenId>_<secret>
```

Only an HMAC-SHA256 digest of the secret is stored. Plaintext is returned once on create or rotate.

## Bootstrap

Set a one-time bootstrap secret in the environment:

```bash
export AUTOAPI_MANAGEMENT_TOKEN_PEPPER="<at least 16 characters>"
export AUTOAPI_BOOTSTRAP_ADMIN_TOKEN="<one-time secret>"
```

Initialize the platform administrator:

```bash
curl -X POST http://localhost:8081/api/v1/management/bootstrap \
  -H "Authorization: Bearer ${AUTOAPI_BOOTSTRAP_ADMIN_TOKEN}"
```

This creates a service account with `ORGANIZATION_OWNER`, issues a durable credential, and marks management auth initialized.

## Built-in roles

See `BuiltInRole` in `Server/src/main/java/com/autoapi/controlplane/managementauth/BuiltInRole.java` for the authoritative permission matrix.

| Role | Scope | Highlights |
|------|-------|------------|
| `ORGANIZATION_OWNER` | Organization | Full management permissions including credential lifecycle |
| `ORGANIZATION_ADMIN` | Organization | Same as owner except credential create/rotate/revoke |
| `ORGANIZATION_VIEWER` | Organization | Read-only across organization resources |
| `PROJECT_ADMIN` | Project | Full project management except org-level membership |
| `PROJECT_OPERATOR` | Project | Operate APIs, routes, policies, rollouts, webhooks |
| `PROJECT_VIEWER` | Project | Read-only within a project |
| `SECURITY_ADMIN` | Project | Policy and API key management |
| `SERVICE_ACCOUNT` roles | Via bindings | Effective permissions = role grants ∩ credential scopes |

## Endpoint authorization

Every authenticated management route must appear in `ManagementEndpointPolicyRegistry`. Unregistered routes return HTTP 403. Public exceptions:

- `/healthz`, `/readyz`, `/actuator/health/**`
- `/api/v1/gateway-config/**`
- Gateway register/heartbeat/config-status
- Service instance register/heartbeat

Run `./scripts/smoke-phase13.sh` after bootstrap to validate RBAC in Docker Compose.

## Security review

See [`PHASE13_SECURITY_REVIEW.md`](PHASE13_SECURITY_REVIEW.md) for threat model, residual risks, and operational guidance.

## Separation

- **Gateway registration/heartbeat/config fetch** remain unauthenticated machine endpoints under `/api/v1/gateways/**` and `/api/v1/gateway-config/**`.
- **Proxied API keys** (`ak_live_…`) are rejected by the management authentication filter.
- **Service registration tokens** (`sr_live_…`) are only valid for service instance registration paths.

## Configuration

```yaml
autoapi:
  management-auth:
    enabled: true
    token:
      pepper: ${AUTOAPI_MANAGEMENT_TOKEN_PEPPER:}
    bootstrap:
      token: ${AUTOAPI_BOOTSTRAP_ADMIN_TOKEN:}
    security:
      allow-unauthenticated-development: false  # never enable in production
```
