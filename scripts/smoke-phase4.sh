#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
GATEWAY_B_URL="${GATEWAY_B_URL:-http://localhost:8082}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
WINDOW_SECONDS="${SMOKE_RATE_LIMIT_WINDOW_SECONDS:-300}"
LIMIT_COUNT="${SMOKE_RATE_LIMIT_COUNT:-5}"

SMOKE_STARTED_AT="$(date +%s)"
CURRENT_STEP="initialization"

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
CONTROL_PLANE_CONTAINER_ID=""
UPSTREAM_V1_CONTAINER_ID=""
UPSTREAM_V2_CONTAINER_ID=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-management-auth-lib.sh
source "${ROOT}/scripts/smoke-management-auth-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    set_smoke_step "Starting cleanup"
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
    log_step "Cleanup completed"
  fi
}

dump_diagnostics() {
  local exit_code="$?"

  if [[ "${exit_code}" -ne 0 ]]; then
    echo "Phase 4 smoke failed during: ${CURRENT_STEP} (exit=${exit_code})" >&2
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}"
  fi

  cleanup
  return "${exit_code}"
}

json_field() {
  python3 - "$1" "$2" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
print(payload[sys.argv[2]])
PY
}

error_code_from_body() {
  python3 - "${SMOKE_BODY_FILE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
print(payload["error"]["code"])
PY
}

header_value() {
  local header_name="$1"
  python3 - "${SMOKE_HEADERS_FILE}" "${header_name}" <<'PY'
import sys
headers_path, wanted = sys.argv[1], sys.argv[2].lower()
with open(headers_path, encoding="utf-8") as handle:
    for line in handle:
        if ":" not in line:
            continue
        name, value = line.split(":", 1)
        if name.strip().lower() == wanted:
            print(value.strip())
            break
PY
}

create_api_key() {
  local api_id="$1"
  local key_name="$2"
  control_plane_json "create API key ${key_name}" \
    -X POST \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"${key_name}\"}" \
    "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/api-keys"
}

control_plane_mutate() {
  local context="$1"
  shift
  local status curl_exit
  set +e
  status="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H \"$(smoke_management_auth_header)\" \
      "$@"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 || "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    report_curl_failure "${context}" "${curl_exit}" "${status}"
    cat "${SMOKE_HEADERS_FILE}" >&2 || true
    cat "${SMOKE_BODY_FILE}" >&2 || true
    exit 1
  fi
}

control_plane_json() {
  local context="$1"
  shift
  control_plane_mutate "${context}" "$@"
  cat "${SMOKE_BODY_FILE}"
}

convergence_reached() {
  local api_id="$1"
  local expected_state="$2"
  smoke_curl --fail "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence" \
    | grep -q "\"derivedState\"[[:space:]]*:[[:space:]]*\"${expected_state}\""
}

wait_convergence() {
  local api_id="$1"
  local expected_state="$2"
  wait_until "convergence ${expected_state} for API ${api_id}" 45 2 \
    convergence_reached "${api_id}" "${expected_state}"
}

flush_redis() {
  docker compose exec -T redis redis-cli FLUSHDB >/dev/null
}

request_status() {
  local gateway_url="$1"
  local api_key="${2:-}"
  local args=(
    -D "${SMOKE_HEADERS_FILE}"
    -o "${SMOKE_BODY_FILE}"
    -w '%{http_code}'
    -H 'Host: api.autoapi.local'
  )
  if [[ -n "${api_key}" ]]; then
    args+=(-H "Authorization: Bearer ${api_key}")
  fi
  smoke_curl "${args[@]}" "${gateway_url}/v1/orders/smoke"
}

auth_request_status() {
  local label="$1"
  local gateway_url="$2"
  local api_key="${3:-}"
  local curl_exit status

  log_step "Auth check: ${label} started"
  set +e
  status="$(request_status "${gateway_url}" "${api_key}")"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "auth check ${label}" "${curl_exit}" "${status:-}"
    dump_compose_smoke_diagnostics "${gateway_url}" "${CONTROL_PLANE_URL}"
    exit "${curl_exit}"
  fi
  log_step "Auth check: ${label} completed (http=${status})"
  printf '%s' "${status}"
}

quota_request_status() {
  local label="$1"
  local gateway_url="$2"
  local api_key="$3"
  local curl_exit status

  log_step "Quota check: ${label} started"
  set +e
  status="$(request_status "${gateway_url}" "${api_key}")"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "quota check ${label}" "${curl_exit}" "${status:-}"
    dump_compose_smoke_diagnostics "${gateway_url}" "${CONTROL_PLANE_URL}"
    exit "${curl_exit}"
  fi
  log_step "Quota check: ${label} completed (http=${status})"
  printf '%s' "${status}"
}

print_failure() {
  local context="$1"
  local expected_status="$2"
  local actual_status="$3"
  echo "${context}: expected HTTP ${expected_status}, got ${actual_status}" >&2
  cat "${SMOKE_HEADERS_FILE}" >&2
  cat "${SMOKE_BODY_FILE}" >&2
}

assert_status() {
  local context="$1"
  local expected_status="$2"
  local actual_status="$3"
  if [[ "${actual_status}" != "${expected_status}" ]]; then
    print_failure "${context}" "${expected_status}" "${actual_status}"
    exit 1
  fi
}

assert_error_code() {
  local context="$1"
  local expected_code="$2"
  local actual_code
  actual_code="$(error_code_from_body)"
  if [[ "${actual_code}" != "${expected_code}" ]]; then
    echo "${context}: expected error code ${expected_code}, got ${actual_code}" >&2
    cat "${SMOKE_HEADERS_FILE}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase4-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase4-body.XXXXXX")"
  trap dump_diagnostics EXIT

  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    set_smoke_step "Tearing down previous compose stack"
    docker compose down -v >/dev/null 2>&1 || true

    set_smoke_step "Building smoke images once"
    build_smoke_images_once

    start_smoke_base_stack
    wait_until "Control plane ready" 45 2 wait_http_ready "${CONTROL_PLANE_URL}"
    log_step "Control plane ready"

    CONTROL_PLANE_CONTAINER_ID="$(compose_container_id control-plane)"
    UPSTREAM_V1_CONTAINER_ID="$(compose_container_id upstream-v1)"
    UPSTREAM_V2_CONTAINER_ID="$(compose_container_id upstream-v2)"
  fi

  set_smoke_step "Creating project, API, pool, route"
  project_json="$(control_plane_json "create project" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
    -H 'Content-Type: application/json' \
    -d '{"name":"phase4-platform","description":"Phase 4 smoke"}')"
  project_id="$(json_field "${project_json}" id)"

  api_json="$(control_plane_json "create API" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${project_id}/apis" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-api","host":"api.autoapi.local","basePath":"/"}')"
  api_id="$(json_field "${api_json}" id)"

  pool_json="$(control_plane_json "create upstream pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-v1","loadBalancing":"ROUND_ROBIN"}')"
  pool_id="$(json_field "${pool_json}" id)"

  control_plane_mutate "create upstream target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}'

  route_json="$(control_plane_json "create route" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}")"
  route_id="$(json_field "${route_json}" id)"

  set_smoke_step "Creating API keys"
  auth_key_json="$(create_api_key "${api_id}" "phase4-smoke-auth-client")"
  rate_limit_key_json="$(create_api_key "${api_id}" "phase4-smoke-rate-limit-client")"
  AUTH_KEY="$(json_field "${auth_key_json}" plaintextKey)"
  RATE_LIMIT_KEY="$(json_field "${rate_limit_key_json}" plaintextKey)"

  set_smoke_step "Creating rate-limit policy"
  policy_json="$(control_plane_json "create rate-limit policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"phase4-smoke-limit\",\"limitCount\":${LIMIT_COUNT},\"windowSeconds\":${WINDOW_SECONDS},\"identitySource\":\"API_KEY\",\"redisFailureMode\":\"FAIL_OPEN\"}")"
  policy_id="$(json_field "${policy_json}" id)"

  control_plane_mutate "bind route policy" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/policy-binding" \
    -H 'Content-Type: application/json' \
    -d "{\"authenticationRequired\":true,\"rateLimitPolicyId\":\"${policy_id}\"}"

  set_smoke_step "Publishing and activating configuration"
  control_plane_mutate "validate configuration" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate"
  control_plane_mutate "publish configuration version 1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Phase 4 version 1"}'
  control_plane_mutate "activate configuration version 1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":null}'

  export AUTOAPI_GATEWAY_API_ID="${api_id}"
  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    set_smoke_step "Starting gateways without rebuilding dependencies"
    start_smoke_gateways gateway-a gateway-b

    assert_compose_container_unchanged control-plane "${CONTROL_PLANE_CONTAINER_ID}"
    assert_compose_container_unchanged upstream-v1 "${UPSTREAM_V1_CONTAINER_ID}"
    assert_compose_container_unchanged upstream-v2 "${UPSTREAM_V2_CONTAINER_ID}"
  fi

  set_smoke_step "Waiting for gateway readiness"
  wait_until "Gateway A ready" 45 2 wait_http_ready "${GATEWAY_A_URL}"
  wait_until "Gateway B ready" 45 2 wait_http_ready "${GATEWAY_B_URL}"
  wait_convergence "${api_id}" "CONVERGED"
  log_step "Gateways ready"

  set_smoke_step "Clearing Redis quota state"
  flush_redis
  log_step "Redis cleared"

  set_smoke_step "Authentication checks"
  status="$(auth_request_status "missing key" "${GATEWAY_A_URL}")"
  assert_status "missing Authorization header via gateway-a" "401" "${status}"
  assert_error_code "missing Authorization header via gateway-a" "INVALID_API_KEY"

  status="$(auth_request_status "malformed key" "${GATEWAY_A_URL}" "ak_live_bad.malformed")"
  assert_status "malformed API key via gateway-a" "401" "${status}"
  assert_error_code "malformed API key via gateway-a" "INVALID_API_KEY"

  status="$(auth_request_status "valid key" "${GATEWAY_A_URL}" "${AUTH_KEY}")"
  assert_status "valid AUTH_KEY via gateway-a" "200" "${status}"

  set_smoke_step "Cross-gateway shared limit"
  for i in $(seq 1 "${LIMIT_COUNT}"); do
    gateway_name="gateway-a"
    gateway_url="${GATEWAY_A_URL}"
    if (( i % 2 == 0 )); then
      gateway_name="gateway-b"
      gateway_url="${GATEWAY_B_URL}"
    fi
    status="$(quota_request_status "shared-limit request ${i} via ${gateway_name}" "${gateway_url}" "${RATE_LIMIT_KEY}")"
    assert_status "shared-limit request ${i} via ${gateway_name}" "200" "${status}"
  done

  status="$(quota_request_status "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b" "${GATEWAY_B_URL}" "${RATE_LIMIT_KEY}")"
  assert_status "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b" "429" "${status}"
  assert_error_code "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b" "RATE_LIMIT_EXCEEDED"
  remaining="$(header_value "RateLimit-Remaining")"
  if [[ "${remaining}" != "0" ]]; then
    echo "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b: expected RateLimit-Remaining: 0, got ${remaining}" >&2
    cat "${SMOKE_HEADERS_FILE}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi

  set_smoke_step "Publishing short-window policy for reset behavior"
  RESET_WINDOW_SECONDS=10
  control_plane_mutate "patch rate-limit window for reset behavior" \
    -X PATCH "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies/${policy_id}" \
    -H 'Content-Type: application/json' \
    -d "{\"windowSeconds\":${RESET_WINDOW_SECONDS}}"
  control_plane_mutate "validate configuration after reset-window patch" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate"
  control_plane_mutate "publish configuration version 2" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Phase 4 version 2 short reset window"}'
  control_plane_mutate "activate configuration version 2" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/2/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":1}'
  wait_convergence "${api_id}" "CONVERGED"

  set_smoke_step "Waiting for quota reset"
  reset_ok=false
  for _ in $(seq 1 $((RESET_WINDOW_SECONDS + 5))); do
    status="$(quota_request_status "quota reset probe" "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
    if [[ "${status}" == "200" ]]; then
      reset_ok=true
      break
    fi
    sleep 1
  done
  [[ "${reset_ok}" == "true" ]] || {
    echo "Quota did not reset within window for RATE_LIMIT_KEY" >&2
    exit 1
  }

  set_smoke_step "Redis outage with FAIL_OPEN"
  docker compose stop redis
  sleep 2
  status="$(quota_request_status "FAIL_OPEN during Redis outage" "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
  assert_status "FAIL_OPEN during Redis outage via gateway-a" "200" "${status}"

  set_smoke_step "Switch policy to FAIL_CLOSED"
  control_plane_mutate "patch rate-limit failure mode to FAIL_CLOSED" \
    -X PATCH "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies/${policy_id}" \
    -H 'Content-Type: application/json' \
    -d '{"redisFailureMode":"FAIL_CLOSED"}'
  control_plane_mutate "validate configuration after FAIL_CLOSED patch" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate"
  control_plane_mutate "publish configuration version 3" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Phase 4 version 3 FAIL_CLOSED"}'
  control_plane_mutate "activate configuration version 3" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/3/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":2}'
  wait_convergence "${api_id}" "CONVERGED"

  status="$(quota_request_status "FAIL_CLOSED during Redis outage" "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
  assert_status "FAIL_CLOSED during Redis outage via gateway-a" "503" "${status}"

  set_smoke_step "Restore Redis"
  docker compose start redis
  redis_responds() {
    docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG
  }
  wait_until "Redis ping after restore" 30 1 redis_responds
  status="$(quota_request_status "recovery after Redis restore" "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
  assert_status "recovery after Redis restore via gateway-a" "200" "${status}"

  log_step "Phase 4 smoke completed successfully"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
