# Policy Engine (Phase 14)

Phase 14 introduces a centralized policy engine for AutoAPI. Policies are managed in the control plane, inherited through the resource hierarchy, and flattened into gateway runtime snapshots at publish time. Gateways never resolve inheritance themselves.

## Hierarchy

Policies apply at the following levels (most specific wins for override semantics):

```
Organization
  └── Project
        └── Gateway Group
              └── API
                    └── Route
                          └── (Operation — reserved for future expansion)
```

Lower levels override higher levels unless a policy type uses merge semantics.

## Policy types and merge rules

| Policy type | Merge strategy | Behavior |
|-------------|----------------|----------|
| `rateLimit` | Override | Most specific scope wins |
| `retry` | Override | Most specific scope wins |
| `circuitBreaker` | Override | Most specific scope wins |
| `backendHealth` | Override | Most specific scope wins |
| `trafficSplit` | Override | Most specific scope wins |
| `timeout` | Override | Most specific scope wins |
| `authentication` | Override | Most specific scope wins |
| `cors` | Override | Most specific scope wins |
| `headers` | Merge map | Headers merged by name across levels |
| `logging` | Merge map | Logging fields merged |
| `requestValidation` | Merge map | Validation rules merged |

Override modes on policy overrides:

- `INHERIT` — contribution ignored
- `OVERRIDE` — replaces inherited value
- `MERGE` — merged according to policy type strategy
- `DISABLE` — disables the policy type for the scope
- `APPEND` — appends for list-based types

Unknown policy types default to override semantics and can be added via the type registry without engine changes.

## Policy bundles

Reusable bundles contain multiple policy types. Each bundle supports immutable revisions:

1. Create bundle (`POST /management/organizations/{orgId}/policy-bundles`)
2. Add revision (`POST .../policy-bundles/{bundleId}/revisions`)
3. Assign revision at org/project/gateway-group/api/route scope
4. Upgrade an existing assignment revision (`PATCH /management/policy-bundle-assignments/{assignmentId}`)

Assignments reference a specific revision number. Updating bundle content creates a new revision; existing assignments continue using their pinned revision until explicitly upgraded.

## Effective policy API

```
GET /api/v1/management/apis/{apiId}/effective-policy
GET /api/v1/management/apis/{apiId}/effective-policy?routeId={routeId}
GET /api/v1/management/apis/{apiId}/effective-policy?allRoutes=true
GET /api/v1/management/apis/{apiId}/effective-policy?explain=true
```

Returns flattened policy JSON (for example `rateLimit`, `timeout`, `headers`).

## Dry-run evaluation

```
POST /api/v1/management/policies/evaluate
{
  "apiId": "...",
  "routeId": "...",
  "explain": true
}
```

Returns resolved policy and, when `explain` is true, winning source metadata per policy type.

## Gateway integration

On config publish, the control plane:

1. Evaluates effective policy for every route
2. Overlays supported types onto compiled route sections (`rateLimit`, `retry`, `circuitBreaker`)
3. Embeds `effectivePolicies[]` in the runtime snapshot
4. Recomputes the runtime content hash from the enriched payload

Gateways poll `/api/v1/gateway-config/{apiId}/versions/{version}` and consume the flattened snapshot only.

## Caching

Effective policy results are cached in memory keyed by scope (`api:{id}` or `route:{id}`). A global generation counter invalidates all entries when bundles, assignments, overrides, or hierarchy resources change.

Micrometer metrics:

- `policy.evaluate`
- `policy.cache.hit` / `policy.cache.miss`
- `policy.bundle.assignments`
- `policy.override.count`
- `policy.inheritance.depth`

## RBAC (Phase 13 integration)

| Permission | Capability |
|------------|------------|
| `policy_bundle.read` | View bundles and overrides |
| `policy_bundle.manage` | Create/update bundles and overrides |
| `policy_bundle.assign` | Assign bundles |
| `policy_bundle.detach` | Detach bundles |
| `policy.evaluate` | Dry-run evaluation |
| `policy.effective.read` | View effective policies |

## Events and audit

Platform events: `PolicyBundleCreated`, `PolicyBundleUpdated`, `PolicyBundleRevisionCreated`, `PolicyBundleAssigned`, `PolicyBundleAssignmentRevisionChanged`, `PolicyBundleDetached`, `PolicyEvaluated`, `EffectivePolicyChanged`.

Audit log entries capture actor, scope, previous/new values, bundle revision, and reason.

## Database (Flyway V13)

Tables: `policy_bundles`, `policy_bundle_revisions`, `policy_bundle_assignments`, `policy_overrides`, `effective_policy_cache_metadata`, `policy_audit_log`, `global_policy_cache_generation`.

## Example: inherited rate limit with API override

Organization bundle: `rateLimit.limitCount = 1000`

API override: `rateLimit.limitCount = 500`

Effective at route: **500 RPM** (API override wins)

## Testing

- Unit: `PolicyMergeEngineTest`, `EffectivePolicyRuntimeBridgeTest`
- Integration: `PolicyEngineIntegrationTest`
- Smoke: `scripts/smoke-phase14.sh`
