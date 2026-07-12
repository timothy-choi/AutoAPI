#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
GATEWAY_B_URL="${GATEWAY_B_URL:-http://localhost:8082}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
WINDOW_SECONDS="${SMOKE_RATE_LIMIT_WINDOW_SECONDS:-10}"
LIMIT_COUNT="${SMOKE_RATE_LIMIT_COUNT:-5}"

json_field() {
  python3 - "$1" "$2" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
print(payload[sys.argv[2]])
PY
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

request_status() {
  local gateway_url="$1"
  local api_key="${2:-}"
  local args=(--silent -o /tmp/smoke-body.txt -w '%{http_code}' -H 'Host: api.autoapi.local')
  if [[ -n "${api_key}" ]]; then
    args+=(-H "Authorization: Bearer ${api_key}")
  fi
  curl "${args[@]}" "${gateway_url}/v1/orders/smoke"
}

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
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

echo "== Creating API key =="
key_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/api-keys" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase4-smoke-client"}')"
plaintext_key="$(json_field "${key_json}" plaintextKey)"

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

echo "== Auth checks =="
status="$(request_status "${GATEWAY_A_URL}")"
[[ "${status}" == "401" ]] || { echo "Expected 401 without key, got ${status}" >&2; exit 1; }
status="$(request_status "${GATEWAY_A_URL}" "ak_live_bad.malformed")"
[[ "${status}" == "401" ]] || { echo "Expected 401 for malformed key, got ${status}" >&2; exit 1; }
status="$(request_status "${GATEWAY_A_URL}" "${plaintext_key}")"
[[ "${status}" == "200" ]] || { echo "Expected 200 with valid key, got ${status}" >&2; cat /tmp/smoke-body.txt >&2; exit 1; }

echo "== Cross-gateway shared limit =="
for i in $(seq 1 "${LIMIT_COUNT}"); do
  gateway_url="${GATEWAY_A_URL}"
  if (( i % 2 == 0 )); then
    gateway_url="${GATEWAY_B_URL}"
  fi
  status="$(request_status "${gateway_url}" "${plaintext_key}")"
  [[ "${status}" == "200" ]] || { echo "Expected 200 for request ${i}, got ${status}" >&2; exit 1; }
done
status="$(request_status "${GATEWAY_B_URL}" "${plaintext_key}")"
[[ "${status}" == "429" ]] || { echo "Expected shared 429 on request $((LIMIT_COUNT + 1)), got ${status}" >&2; exit 1; }

echo "== Waiting for quota reset =="
reset_ok=false
for _ in $(seq 1 $((WINDOW_SECONDS + 5))); do
  status="$(request_status "${GATEWAY_A_URL}" "${plaintext_key}")"
  if [[ "${status}" == "200" ]]; then
    reset_ok=true
    break
  fi
  sleep 1
done
[[ "${reset_ok}" == "true" ]] || { echo "Quota did not reset within window" >&2; exit 1; }

echo "== Redis outage with FAIL_OPEN =="
docker compose stop redis
sleep 2
status="$(request_status "${GATEWAY_A_URL}" "${plaintext_key}")"
[[ "${status}" == "200" ]] || { echo "Expected 200 with FAIL_OPEN during Redis outage, got ${status}" >&2; exit 1; }

echo "== Switch policy to FAIL_CLOSED =="
curl --fail --silent -X PATCH "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/rate-limit-policies/${policy_id}" \
  -H 'Content-Type: application/json' \
  -d '{"redisFailureMode":"FAIL_CLOSED"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate" >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 4 version 2 FAIL_CLOSED"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/2/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":1}' >/dev/null
wait_convergence "${api_id}" "CONVERGED"

status="$(request_status "${GATEWAY_A_URL}" "${plaintext_key}")"
[[ "${status}" == "503" ]] || { echo "Expected 503 with FAIL_CLOSED during Redis outage, got ${status}" >&2; exit 1; }

echo "== Restore Redis =="
docker compose start redis
for _ in $(seq 1 30); do
  if docker compose exec -T redis redis-cli ping | grep -q PONG; then
    break
  fi
  sleep 1
done
status="$(request_status "${GATEWAY_A_URL}" "${plaintext_key}")"
[[ "${status}" == "200" ]] || { echo "Expected recovery after Redis restore, got ${status}" >&2; exit 1; }

echo "Phase 4 smoke completed successfully."
