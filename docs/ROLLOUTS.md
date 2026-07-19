# Gateway Groups and Progressive Rollouts (Phase 12)

Phase 12 adds **gateway groups** and **progressive runtime rollouts** so operators can promote configuration versions to labeled gateway cohorts instead of activating globally in one step.

---

## Concepts

### Gateway labels

Gateways carry operator-managed `admin_labels` and optional runtime-supplied labels. Labels are validated for bounded size, safe keys/values, and must not contain secrets or use the reserved `autoapi.*` namespace.

### Gateway groups

A gateway group binds to one API and selects member gateways via:

1. **Explicit exclude** (highest precedence)
2. **Explicit include**
3. **Label selector** (`matchLabels`)

Groups track a `desiredConfigVersion` that becomes the effective desired config for member gateways when no active rollout assignment applies.

### Runtime rollouts

Rollouts move a gateway group from a **source** config version to a **target** version using either:

- `ALL_AT_ONCE` — single 100% stage
- `PROGRESSIVE_PERCENTAGE` — monotonically increasing percentage stages

Cohort membership is **deterministic**: gateways are ranked by a stable hash of `(rolloutId, gatewayId)` and selected in order for each stage. Expanding a stage always preserves gateways already in the cohort.

---

## Operator workflow

1. **Register gateways** with heartbeats and rollout labels (`region`, `environment`, etc.).
2. **Create a gateway group** for the API with a label selector matching production gateways.
3. **Publish and activate v1** at the API (or set the group desired version).
4. **Publish v2** without global activation.
5. **Preview rollout** — verify eligible gateway count and per-stage cohort sizes.
6. **Create draft rollout** and **start** — first stage cohort receives rollout assignments.
7. **Verify effective desired config** per gateway via `GET /api/v1/gateway-config/{apiId}/desired?gatewayId=...`.
8. **Pause / resume** as needed (manual progression mode).
9. **Advance stages** until 100% cohort is assigned, then advance once more to complete.
10. On success, the **group desired version** updates to the rollout target.

---

## Management API

| Area | Base path |
|------|-----------|
| Gateway groups | `/api/v1/management/projects/{projectId}/gateway-groups` |
| Rollouts | `/api/v1/management/projects/{projectId}/rollouts` |

Key rollout actions: `POST .../preview`, `POST .../start`, `POST .../pause`, `POST .../resume`, `POST .../advance`, `POST .../cancel`, `POST .../rollback`.

---

## Effective desired config precedence

For `GET /gateway-config/{apiId}/desired?gatewayId=`:

1. Rollback assignment
2. Rollout assignment
3. Gateway group desired version
4. API default desired version

---

## Local verification

### Unit and integration tests

```bash
cd Server
./gradlew test --tests 'com.autoapi.controlplane.rollout.*'
```

### Smoke script (3 gateways)

```bash
chmod +x scripts/smoke-phase12.sh
./scripts/smoke-phase12.sh
```

Requires Docker. The script starts `gateway-a`, `gateway-b`, and `gateway-c`, labels them for group membership, runs a manual progressive rollout from v1 to v2, and verifies group desired version on completion.

Set `SMOKE_SKIP_UP=true` to reuse an already-running compose stack.

---

## Configuration

Rollouts are enabled by default (`autoapi.rollouts.enabled=true`). See `application.yml` for reconciler poll interval, max stages, label limits, and default stage timeouts.

The background **rollout reconciler** evaluates running stages (convergence, timeouts, automatic progression). Manual rollouts use the `advance` API; the reconciler still tracks assignment lifecycle events.
