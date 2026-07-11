#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

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

echo "== Starting PostgreSQL, upstreams, and control plane =="
docker compose up --build -d postgres upstream-v1 upstream-v2 control-plane
wait_ready "${CONTROL_PLANE_URL}" "Control plane"

echo "== Creating project, API, pool, route =="
project_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase2b-platform","description":"Phase 2B bootstrap"}')"
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

curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}" >/dev/null

echo "== Publishing and activating version 1 =="
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate" >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 2B version 1"}' >/dev/null
curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":null}' >/dev/null

echo "== Starting gateway with API ID ${api_id} =="
export AUTOAPI_GATEWAY_API_ID="${api_id}"
docker compose up --build -d gateway
wait_ready "${GATEWAY_URL}" "Gateway"

echo "== Verifying version 1 routes to upstream-v1 =="
response="$(curl --fail --silent -H 'Host: api.autoapi.local' "${GATEWAY_URL}/v1/orders/1")"
echo "${response}" | grep -q upstream-v1

echo "Phase 2B bootstrap completed. API ID=${api_id}"
echo "Gateway routes /v1/orders to upstream-v1 via activated version 1."
