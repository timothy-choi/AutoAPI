# Platform Events and Webhooks (Phase 11)

AutoAPI records durable platform events for control-plane mutations and delivers filtered events to webhook subscribers asynchronously.

## Event model

Events are stored append-only in `platform_events` with a global monotonic `sequence` for cursor pagination. Each event includes actor, resource, correlation/causation IDs, and a versioned `eventType` name (for example `project.created.v1`).

## Transactional outbox

Domain mutations insert an event in the same database transaction. The `webhook_dispatch_status` column tracks outbox processing (`PENDING` → `DISPATCHED`/`SKIPPED`). A background dispatcher creates webhook deliveries for matching subscriptions.

## Management APIs

- `GET /api/v1/management/events`
- `GET /api/v1/management/events/{eventId}`
- `GET /api/v1/management/audit`
- Webhook subscription and delivery APIs under `/api/v1/management/projects/{projectId}/...`

## Webhook signing

Deliveries use HMAC-SHA256 over `timestamp + "." + rawJSONBody` with header `X-AutoAPI-Signature: v1=<hex>`.

## Configuration

See `autoapi.events.*` and `autoapi.webhooks.*` in `application.yml`. Set `AUTOAPI_WEBHOOK_SECRET_MASTER_KEY` to a Base64-encoded 32-byte key in production.

## Phase 12 rollout events

Gateway group and rollout lifecycle events are versioned and webhook-deliverable:

- `gateway_group.created.v1`, `gateway_group.updated.v1`, `gateway_group.deleted.v1`, `gateway_group.membership_changed.v1`
- `runtime_rollout.created.v1`, `runtime_rollout.started.v1`, `runtime_rollout.paused.v1`, `runtime_rollout.resumed.v1`, `runtime_rollout.cancelled.v1`, `runtime_rollout.failed.v1`, `runtime_rollout.succeeded.v1`
- `runtime_rollout.stage.started.v1`, `runtime_rollout.stage.observing.v1`, `runtime_rollout.stage.succeeded.v1`, `runtime_rollout.stage.failed.v1`
- `runtime_rollout.rollback.started.v1`, `runtime_rollout.rollback.succeeded.v1`, `runtime_rollout.rollback.failed.v1`

See [`ROLLOUTS.md`](./ROLLOUTS.md) for rollout semantics and operator workflow.
