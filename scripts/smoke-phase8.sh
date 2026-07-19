#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
FAILURE_THRESHOLD="${SMOKE_CB_FAILURE_THRESHOLD:-2}"
OPEN_SECONDS="${SMOKE_CB_OPEN_SECONDS:-5}"
PHASE8_API_ID=""
TARGET_ID=""

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_CB_FILE=""
LAST_HTTP_STATUS=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"
# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_CB_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    set_smoke_step "Starting cleanup"
    timeout 120 docker compose up -d upstream-v1 >/dev/null 2>&1 || true
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
    log_step "Cleanup completed"
  fi
}

dump_diagnostics() {
  local exit_code="$?"
  if [[ "${exit_code}" -ne 0 ]]; then
    log_step "Phase 8 smoke failed with exit code ${exit_code} at step: ${SMOKE_CURRENT_STEP}"
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${PHASE8_API_ID}"
  fi
  cleanup
  return "${exit_code}"
}

error_code_from_body() {
  python3 - "${SMOKE_BODY_FILE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
error = payload.get("error") or {}
print(error.get("code", ""))
PY
}

wait_convergence() {
  local api_id="$1"
  local converged=false
  local response=""
  for _ in $(seq 1 45); do
    response="$(management_curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence")"
    if echo "${response}" | grep -q '"derivedState"[[:space:]]*:[[:space:]]*"CONVERGED"'; then
      converged=true
      break
    fi
    sleep 2
  done
  if [[ "${converged}" != "true" ]]; then
    echo "Convergence did not reach CONVERGED: ${response}" >&2
    exit 1
  fi
}

wait_ready() {
  local url="$1"
  local label="$2"
  wait_until "${label} ready" 45 2 smoke_curl --fail "${url}/readyz" >/dev/null
}

fetch_circuit_breakers() {
  local curl_exit status
  set +e
  status="$(
    smoke_curl \
      -o "${SMOKE_CB_FILE}" \
      -w '%{http_code}' \
      "${GATEWAY_A_URL}/internal/v1/circuit-breakers"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 || "${status}" != "200" ]]; then
    report_curl_failure "fetch circuit-breakers" "${curl_exit}" "${status}"
    exit 1
  fi
  cat "${SMOKE_CB_FILE}"
}

read_breaker_state() {
  fetch_circuit_breakers >/dev/null
  python3 - "${SMOKE_CB_FILE}" "${TARGET_ID}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
target_id = sys.argv[2]
for target in payload.get("targets", []):
    if target.get("targetId") == target_id:
        print(target.get("state", ""))
        raise SystemExit(0)
print("")
PY
}

gateway_get_allow_errors() {
  local curl_exit
  set +e
  LAST_HTTP_STATUS="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_A_URL}/v1/orders/smoke"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway GET" "${curl_exit}" "${LAST_HTTP_STATUS}"
    exit "${curl_exit}"
  fi
  printf '%s' "${LAST_HTTP_STATUS}"
}

trap dump_diagnostics EXIT
SMOKE_HEADERS_FILE="$(mktemp)"
SMOKE_BODY_FILE="$(mktemp)"
SMOKE_CB_FILE="$(mktemp)"

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  set_smoke_step "Starting docker compose"
  docker compose down -v >/dev/null 2>&1 || true
  build_smoke_images_once
  start_smoke_base_stack
  wait_ready "${CONTROL_PLANE_URL}" "control-plane"
fi

smoke_bootstrap_management "${CONTROL_PLANE_URL}"

set_smoke_step "Bootstrapping API with circuit breaker policy"
project_json="$(control_plane_json "create project" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase8-project"}')"
project_id="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "${project_json}")"

api_json="$(control_plane_json "create API" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${project_id}/apis" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase8-orders","host":"api.autoapi.local","basePath":"/"}')"
PHASE8_API_ID="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "${api_json}")"

pool_json="$(control_plane_json "create pool" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE8_API_ID}/upstream-pools" \
  -H 'Content-Type: application/json' \
  -d '{"name":"primary","loadBalancing":"ROUND_ROBIN"}')"
pool_id="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "${pool_json}")"

target_json="$(control_plane_json "create target" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/targets" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://upstream-v1:8080","weight":1,"enabled":true}')"
TARGET_ID="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "${target_json}")"

route_json="$(control_plane_json "create route" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE8_API_ID}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}")"
route_id="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "${route_json}")"

cb_json="$(control_plane_json "create circuit breaker policy" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE8_API_ID}/circuit-breaker-policies" \
  -H 'Content-Type: application/json' \
  -d "{
    \"name\": \"phase8-breaker\",
    \"failureThreshold\": ${FAILURE_THRESHOLD},
    \"rollingWindowSeconds\": 60,
    \"openDurationSeconds\": ${OPEN_SECONDS},
    \"halfOpenMaxRequests\": 1,
    \"successThreshold\": 1,
    \"failurePredicate\": {
      \"countHttp5xx\": true,
      \"countConnectFailure\": true,
      \"countConnectTimeout\": true,
      \"countReadTimeout\": true,
      \"countTlsFailure\": true,
      \"countTransportException\": true,
      \"countHttp429\": false
    },
    \"enabled\": true
  }")"
cb_policy_id="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "${cb_json}")"

control_plane_mutate "bind circuit breaker" \
  -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/circuit-breaker-policy" \
  -H 'Content-Type: application/json' \
  -d "{\"circuitBreakerPolicyId\":\"${cb_policy_id}\"}"

control_plane_mutate "validate" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE8_API_ID}/config/validate"
control_plane_mutate "publish" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE8_API_ID}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 8 circuit breaker"}'
control_plane_mutate "activate" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE8_API_ID}/config/versions/1/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":null}'

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  export AUTOAPI_GATEWAY_API_ID="${PHASE8_API_ID}"
  set_smoke_step "Starting gateway-a with API ${PHASE8_API_ID}"
  start_smoke_gateways gateway-a
  wait_ready "${GATEWAY_A_URL}" "gateway-a"
fi

set_smoke_step "Waiting for convergence"
wait_convergence "${PHASE8_API_ID}"

set_smoke_step "Healthy baseline request"
status="$(gateway_get_allow_errors)"
if [[ "${status}" != "200" ]]; then
  echo "Expected healthy 200, got ${status}" >&2
  exit 1
fi

set_smoke_step "Inject repeated upstream failures"
docker compose stop upstream-v1 >/dev/null
sleep 1
for _ in $(seq 1 "${FAILURE_THRESHOLD}"); do
  gateway_get_allow_errors >/dev/null || true
done

wait_until "circuit breaker OPEN" 10 1 test "$(read_breaker_state)" = "OPEN"

set_smoke_step "Requests rejected while OPEN"
gateway_get_allow_errors >/dev/null || true
if [[ "$(error_code_from_body)" != "CIRCUIT_BREAKER_OPEN" ]]; then
  echo "Expected CIRCUIT_BREAKER_OPEN while breaker open, got $(error_code_from_body)" >&2
  exit 1
fi

set_smoke_step "Restore upstream and wait for HALF_OPEN probe"
docker compose up -d upstream-v1 >/dev/null
wait_until "upstream-v1 healthy" 30 2 bash -c 'docker compose ps upstream-v1 2>/dev/null | grep -q "(healthy)"'
wait_until "breaker cooldown elapsed" "$((OPEN_SECONDS + 3))" 1 sleep 1

set_smoke_step "HALF_OPEN probe and recovery"
wait_until "HALF_OPEN probe succeeds" 30 2 test "$(gateway_get_allow_errors)" = "200"

wait_until "circuit breaker CLOSED" 10 1 test "$(read_breaker_state)" = "CLOSED"

set_smoke_step "Phase 8 smoke passed"
log_step "Circuit breaker lifecycle verified: healthy -> OPEN -> rejected -> HALF_OPEN -> CLOSED"
