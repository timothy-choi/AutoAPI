# AutoAPI Observability Guide

Phase 9 adds production-grade distributed observability across the gateway and control plane.

## Request correlation

- Inbound `X-Request-ID` is accepted when valid (`[A-Za-z0-9._:-]{1,128}`).
- Missing or malformed values generate `req-<uuid>`.
- The same request ID is returned to clients, logged, traced, and forwarded upstream.
- Retries reuse the same request ID; each upstream attempt uses `attemptId = <requestId>-attempt-<n>`.

## Tracing

- W3C `traceparent` / `tracestate` extraction and injection.
- Server span: `gateway.request`
- Client spans: `gateway.upstream.attempt`
- OpenTelemetry SDK with optional OTLP export (`OTEL_EXPORTER_OTLP_ENDPOINT`).
- Tracing failures never fail user requests; disable with `autoapi.gateway.observability.tracing-enabled=false`.

## Structured logs

Events include:

- `gateway_request_completed`
- `gateway_target_selected`
- `gateway_retry_scheduled`
- `gateway_fallback_selected`
- `gateway_request_rejected`
- `runtime_snapshot_activated`
- `gateway_heartbeat_failed`

Logs are JSON objects emitted at INFO without secrets, bodies, or authorization headers.

## Metrics

Key Prometheus metrics:

- `autoapi_gateway_requests_total`
- `autoapi_gateway_request_duration_seconds`
- `autoapi_gateway_inflight_requests`
- `autoapi_gateway_upstream_attempts_total`
- `autoapi_gateway_upstream_duration_seconds`
- `autoapi_gateway_retries_total`
- `autoapi_gateway_fallback_total`
- `autoapi_gateway_rejections_total`
- `autoapi_gateway_upstream_errors_total`
- `autoapi_gateway_runtime_snapshot_info`
- `autoapi_gateway_runtime_snapshot_activations_total`

Existing Phase 4–8 metrics remain available.

### Example queries

```promql
sum(rate(autoapi_gateway_requests_total[5m])) by (route, status_class)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(autoapi_gateway_request_duration_seconds_bucket[5m])) by (le, route)
)
```

```promql
sum(rate(autoapi_gateway_retries_total[5m])) by (route, reason)
```

## Gateway status

Management APIs:

- `GET /api/v1/management/gateways`
- `GET /api/v1/management/gateways/{gatewayId}`
- `GET /api/v1/management/gateways/{gatewayId}/instances`
- `GET /api/v1/management/observability/requests`

Operational status is derived from heartbeat age:

- `READY` — recent heartbeat
- `STALE` — heartbeat older than `autoapi.controlplane.gateway-stale-after`
- `OFFLINE` — heartbeat older than 3× stale threshold

Gateway internal debugging:

- `GET /internal/v1/request-summaries`

## Local observability stack

Optional profile:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile observability up
```

## Privacy

Observability excludes API keys, authorization headers, cookies, raw bodies, and unbounded path/query labels.
