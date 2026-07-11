#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${SMOKE_BASE_URL:-http://localhost:8080}"
CONTROL_PLANE_URL="${SMOKE_CONTROL_PLANE_URL:-http://localhost:8081}"

wait_ready() {
  local ready=false
  for _ in $(seq 1 45); do
    if curl --fail --silent "${BASE_URL}/readyz" >/dev/null; then
      ready=true
      break
    fi
    sleep 2
  done
  if [[ "${ready}" != "true" ]]; then
    echo "Gateway did not become ready" >&2
    exit 1
  fi
}

json_field() {
  python3 - "$1" "$2" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
print(payload[sys.argv[2]])
PY
}

wait_ready

curl --fail --silent \
  -H "Host: api.autoapi.local" \
  "${BASE_URL}/v1/orders/123" >/dev/null

project_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"smoke-platform","description":"compose smoke"}')"
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

validate_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate")"
valid="$(json_field "${validate_json}" valid)"
if [[ "${valid}" != "True" && "${valid}" != "true" ]]; then
  echo "Config validation failed: ${validate_json}" >&2
  exit 1
fi

version_json="$(curl --fail --silent -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Smoke test initial config"}')"
version="$(json_field "${version_json}" version)"
if [[ "${version}" != "1" ]]; then
  echo "Expected version 1, got ${version}" >&2
  exit 1
fi

curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1" | grep -q pathPrefix

echo "Control plane smoke checks passed"
