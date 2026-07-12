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

SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase4-headers.XXXXXX")"
SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase4-body.XXXXXX")"
cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}"
  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    docker compose down -v >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

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
  curl --fail --silent \
    -X POST \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"${key_name}\"}" \
    "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/api-keys"
}

wait_ready() {
  local url="$1"
  local label="$2"
  local ready=false
  for _ in $(seq 1 45); do
    if curl --fail --silent "${url}/readyz" >/dev/null; then
      ready=true
      break
    fi
    sleep 2
  done
  if [[ "${ready}" != "true" ]]; then
    echo "${label} did not become ready" >&2
    exit 1
  fi
}

wait_convergence() {
  local api_id="$1"
  local expected_state="$2"
  local converged=false
  local response=""
  for _ in $(seq 1 45); do
    response="$(curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence")"
    state="$(json_field "${response}" derivedState)"
    if [[ "${state}" == "${expected_state}" ]]; then
      converged=true
      break
    fi
    sleep 2
  done
  if [[ "${converged}" != "true" ]]; then
    echo "Convergence did not reach ${expected_state}: ${response}" >&2
    exit 1
  fi
}

flush_redis() {
  docker compose exec -T redis redis-cli FLUSHDB >/dev/null
}

request_status() {
  local gateway_url="$1"
  local api_key="${2:-}"
  local args=(
    --silent
    -D "${SMOKE_HEADERS_FILE}"
    -o "${SMOKE_BODY_FILE}"
    -w '%{http_code}'
    -H 'Host: api.autoapi.local'
  )
  if [[ -n "${api_key}" ]]; then
    args+=(-H "Authorization: Bearer ${api_key}")
  fi
  curl "${args[@]}" "${gateway_url}/v1/orders/smoke"
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

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  docker compose down -v >/dev/null 2>&1 || true
  echo "== Starting Phase 4 stack =="
  docker compose up --build -d postgres redis upstream-v1 upstream-v2 control-plane
  wait_ready "${CONTROL_PLANE_URL}" "Control plane"
fi

echo "== Creating project, API, pool, route =="
project_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase4-platform","description":"Phase 4 smoke"}')"
project_id="$(json_field "${project_json}" id)"

api_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${project_id}/apis" \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders-api","host":"api.autoapi.local","basePath":"/"}')"
api_id="$(json_field "${api_json}" id)"

pool_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/upstream-pools" \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders-v1","loadBalancing":"ROUND_ROBIN"}')"
pool_id="$(json_field "${pool_json}" id)"

curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/targets" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}' >/dev/null

route_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}")"
route_id="$(json_field "${route_json}" id)"

echo "== Creating API keys =="
auth_key_json="$(create_api_key "${api_id}" "phase4-smoke-auth-client")"
rate_limit_key_json="$(create_api_key "${api_id}" "phase4-smoke-rate-limit-client")"
AUTH_KEY="$(json_field "${auth_key_json}" plaintextKey)"
RATE_LIMIT_KEY="$(json_field "${rate_limit_key_json}" plaintextKey)"

echo "== Creating rate-limit policy =="
policy_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"phase4-smoke-limit\",\"limitCount\":${LIMIT_COUNT},\"windowSeconds\":${WINDOW_SECONDS},\"identitySource\":\"API_KEY\",\"redisFailureMode\":\"FAIL_OPEN\"}")"
policy_id="$(json_field "${policy_json}" id)"

curl --fail --silent -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/policy-binding" \
  -H 'Content-Type: application/json' \
  -d "{\"authenticationRequired\":true,\"rateLimitPolicyId\":\"${policy_id}\"}" >/dev/null

echo "== Publishing and activating =="
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate" >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 4 version 1"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":null}' >/dev/null

export AUTOAPI_GATEWAY_API_ID="${api_id}"
if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  docker compose up --build -d gateway-a gateway-b
fi
wait_ready "${GATEWAY_A_URL}" "Gateway A"
wait_ready "${GATEWAY_B_URL}" "Gateway B"
wait_convergence "${api_id}" "CONVERGED"

echo "== Clearing Redis quota state =="
flush_redis

echo "== Auth checks =="
status="$(request_status "${GATEWAY_A_URL}")"
assert_status "missing Authorization header via gateway-a" "401" "${status}"
assert_error_code "missing Authorization header via gateway-a" "INVALID_API_KEY"

status="$(request_status "${GATEWAY_A_URL}" "ak_live_bad.malformed")"
assert_status "malformed API key via gateway-a" "401" "${status}"
assert_error_code "malformed API key via gateway-a" "INVALID_API_KEY"

status="$(request_status "${GATEWAY_A_URL}" "${AUTH_KEY}")"
assert_status "valid AUTH_KEY via gateway-a" "200" "${status}"

echo "== Cross-gateway shared limit =="
for i in $(seq 1 "${LIMIT_COUNT}"); do
  gateway_name="gateway-a"
  gateway_url="${GATEWAY_A_URL}"
  if (( i % 2 == 0 )); then
    gateway_name="gateway-b"
    gateway_url="${GATEWAY_B_URL}"
  fi
  status="$(request_status "${gateway_url}" "${RATE_LIMIT_KEY}")"
  assert_status "shared-limit request ${i} via ${gateway_name}" "200" "${status}"
done

status="$(request_status "${GATEWAY_B_URL}" "${RATE_LIMIT_KEY}")"
assert_status "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b" "429" "${status}"
assert_error_code "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b" "RATE_LIMIT_EXCEEDED"
remaining="$(header_value "RateLimit-Remaining")"
if [[ "${remaining}" != "0" ]]; then
  echo "shared-limit request $((LIMIT_COUNT + 1)) via gateway-b: expected RateLimit-Remaining: 0, got ${remaining}" >&2
  cat "${SMOKE_HEADERS_FILE}" >&2
  cat "${SMOKE_BODY_FILE}" >&2
  exit 1
fi

echo "== Publishing short-window policy for reset behavior =="
RESET_WINDOW_SECONDS=10
curl --fail --silent -X PATCH "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies/${policy_id}" \
  -H 'Content-Type: application/json' \
  -d "{\"windowSeconds\":${RESET_WINDOW_SECONDS}}" >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate" >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 4 version 2 short reset window"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/2/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":1}' >/dev/null
wait_convergence "${api_id}" "CONVERGED"

echo "== Waiting for quota reset =="
reset_ok=false
for _ in $(seq 1 $((RESET_WINDOW_SECONDS + 5))); do
  status="$(request_status "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
  if [[ "${status}" == "200" ]]; then
    reset_ok=true
    break
  fi
  sleep 1
done
[[ "${reset_ok}" == "true" ]] || {
  echo "Quota did not reset within window for RATE_LIMIT_KEY" >&2
  cat "${SMOKE_HEADERS_FILE}" >&2
  cat "${SMOKE_BODY_FILE}" >&2
  exit 1
}

echo "== Redis outage with FAIL_OPEN =="
docker compose stop redis
sleep 2
status="$(request_status "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
assert_status "FAIL_OPEN during Redis outage via gateway-a" "200" "${status}"

echo "== Switch policy to FAIL_CLOSED =="
curl --fail --silent -X PATCH "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies/${policy_id}" \
  -H 'Content-Type: application/json' \
  -d '{"redisFailureMode":"FAIL_CLOSED"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate" >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 4 version 3 FAIL_CLOSED"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/3/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":2}' >/dev/null
wait_convergence "${api_id}" "CONVERGED"

status="$(request_status "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
assert_status "FAIL_CLOSED during Redis outage via gateway-a" "503" "${status}"

echo "== Restore Redis =="
docker compose start redis
for _ in $(seq 1 30); do
  if docker compose exec -T redis redis-cli ping | grep -q PONG; then
    break
  fi
  sleep 1
done
status="$(request_status "${GATEWAY_A_URL}" "${RATE_LIMIT_KEY}")"
assert_status "recovery after Redis restore via gateway-a" "200" "${status}"

echo "Phase 4 smoke completed successfully."
