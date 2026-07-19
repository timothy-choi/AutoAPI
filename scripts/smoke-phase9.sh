#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
PHASE9_API_ID=""
CUSTOM_REQUEST_ID="smoke-req-phase9-001"

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
  fi
}

dump_diagnostics() {
  local exit_code="$?"
  if [[ "${exit_code}" -ne 0 ]]; then
    log_step "Phase 9 smoke failed at step: ${SMOKE_CURRENT_STEP}"
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${PHASE9_API_ID}"
  fi
  cleanup
  return "${exit_code}"
}

wait_convergence() {
  local api_id="$1"
  wait_until "convergence" 45 2 bash -c \
    "smoke_curl --fail --silent '${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence' | grep -q CONVERGED"
}

wait_gateway_instance() {
  wait_until "gateway instance heartbeat" 45 2 bash -c \
    "smoke_curl --fail --silent '${CONTROL_PLANE_URL}/api/v1/management/gateways/gateway-a/instances' | grep -q instanceId"
}

wait_metrics() {
  wait_until "prometheus metrics" 30 2 bash -c \
    "smoke_curl --fail --silent '${GATEWAY_A_URL}/actuator/prometheus' | grep -q autoapi_gateway_requests_total"
}

trap dump_diagnostics EXIT

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  set_smoke_step "Starting compose stack"
  build_smoke_images_once
  start_smoke_base_stack
  start_smoke_gateways
fi

set_smoke_step "Waiting for services"
wait_until "control-plane ready" 45 2 smoke_curl --fail "${CONTROL_PLANE_URL}/readyz" >/dev/null
smoke_bootstrap_management "${CONTROL_PLANE_URL}"
wait_until "gateway-a ready" 45 2 smoke_curl --fail "${GATEWAY_A_URL}/readyz" >/dev/null

set_smoke_step "Bootstrap API"
PROJECT_ID="$(smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase9-smoke"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
PHASE9_API_ID="$(smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${PROJECT_ID}/apis" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase9-api","host":"api.autoapi.local"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
POOL_ID="$(smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE9_API_ID}/upstream-pools" \
  -H 'Content-Type: application/json' \
  -d '{"name":"primary","loadBalancing":"ROUND_ROBIN"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
TARGET_ID="$(smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${POOL_ID}/targets" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://upstream-v1:8080","weight":1}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
ROUTE_ID="$(smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE9_API_ID}/routes" \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders","host":"api.autoapi.local","pathPrefix":"/v1/orders","methods":["GET"],"upstreamPoolId":"'"${POOL_ID}"'"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

set_smoke_step "Publish and activate"
smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE9_API_ID}/config/validate" >/dev/null
smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE9_API_ID}/config/publish" >/dev/null
smoke_curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE9_API_ID}/config/activate" \
  -H 'Content-Type: application/json' -d '{}' >/dev/null
wait_convergence "${PHASE9_API_ID}"

set_smoke_step "Request without X-Request-ID generates ID"
smoke_curl -D "${SMOKE_HEADERS_FILE}" -o "${SMOKE_BODY_FILE}" -s \
  -H 'Host: api.autoapi.local' "${GATEWAY_A_URL}/v1/orders/smoke" >/dev/null
GENERATED_ID="$(grep -i '^x-request-id:' "${SMOKE_HEADERS_FILE}" | head -1 | cut -d' ' -f2- | tr -d '\r')"
if [[ -z "${GENERATED_ID}" ]]; then
  echo "Expected generated X-Request-ID response header" >&2
  exit 1
fi
if ! grep -q "${GENERATED_ID}" "${SMOKE_BODY_FILE}"; then
  echo "Expected upstream echo of generated request ID in body" >&2
  exit 1
fi

set_smoke_step "Request with valid X-Request-ID is preserved"
smoke_curl -D "${SMOKE_HEADERS_FILE}" -o "${SMOKE_BODY_FILE}" -s \
  -H 'Host: api.autoapi.local' -H "X-Request-ID: ${CUSTOM_REQUEST_ID}" \
  "${GATEWAY_A_URL}/v1/orders/smoke" >/dev/null
RETURNED_ID="$(grep -i '^x-request-id:' "${SMOKE_HEADERS_FILE}" | head -1 | cut -d' ' -f2- | tr -d '\r')"
if [[ "${RETURNED_ID}" != "${CUSTOM_REQUEST_ID}" ]]; then
  echo "Expected preserved request ID ${CUSTOM_REQUEST_ID}, got ${RETURNED_ID}" >&2
  exit 1
fi
if ! grep -q "${CUSTOM_REQUEST_ID}" "${SMOKE_BODY_FILE}"; then
  echo "Expected upstream echo of custom request ID" >&2
  exit 1
fi

set_smoke_step "Traceparent header accepted"
smoke_curl -D "${SMOKE_HEADERS_FILE}" -o "${SMOKE_BODY_FILE}" -s \
  -H 'Host: api.autoapi.local' \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  "${GATEWAY_A_URL}/v1/orders/smoke" >/dev/null

set_smoke_step "Prometheus metrics available"
wait_metrics
METRICS="$(smoke_curl --fail --silent "${GATEWAY_A_URL}/actuator/prometheus")"
echo "${METRICS}" | grep -q 'autoapi_gateway_requests_total'
echo "${METRICS}" | grep -q 'autoapi_gateway_request_duration_seconds'
echo "${METRICS}" | grep -q 'autoapi_gateway_inflight_requests'

set_smoke_step "Gateway heartbeat visible in management API"
wait_gateway_instance
INSTANCES="$(smoke_curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/management/gateways/gateway-a/instances")"
echo "${INSTANCES}" | grep -q '"operationalStatus"'
echo "${INSTANCES}" | grep -q '"activeSnapshotVersion"'

set_smoke_step "Internal request summaries endpoint"
SUMMARIES="$(smoke_curl --fail --silent "${GATEWAY_A_URL}/internal/v1/request-summaries?limit=5")"
echo "${SUMMARIES}" | grep -q '"summaries"'

set_smoke_step "Structured logs include request ID (best effort)"
if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  LOGS="$(docker compose logs gateway-a 2>/dev/null | tail -200 || true)"
  if [[ -n "${LOGS}" ]]; then
    echo "${LOGS}" | grep -q 'gateway_request_completed' || echo "${LOGS}" | grep -q "${CUSTOM_REQUEST_ID}" || {
      echo "Warning: could not confirm structured completion log in container output" >&2
    }
  fi
fi

echo "Phase 9 smoke passed — request IDs, metrics, heartbeat, and observability endpoints validated"
