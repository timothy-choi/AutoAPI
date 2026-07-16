#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
GATEWAY_B_URL="${GATEWAY_B_URL:-http://localhost:8082}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
PHASE7_API_ID=""
CANARY_KEY=""
STABLE_KEY=""

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_TRAFFIC_FILE=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_TRAFFIC_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    set_smoke_step "Starting cleanup"
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
    log_step "Cleanup completed"
  fi
}

dump_diagnostics() {
  local exit_code="$?"
  if [[ "${exit_code}" -ne 0 ]]; then
    log_step "Phase 7 smoke failed with exit code ${exit_code} at step: ${SMOKE_CURRENT_STEP}"
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${PHASE7_API_ID}"
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

service_from_body() {
  python3 - "${SMOKE_BODY_FILE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
print(payload.get("service", ""))
PY
}

destination_class() {
  local service="$1"
  case "${service}" in
    stable-v1|stable-v2) printf 'stable' ;;
    canary-v1) printf 'canary' ;;
    *) printf 'unknown' ;;
  esac
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

gateway_get_with_key() {
  local gateway_url="$1"
  local user_key="$2"
  local curl_exit status
  set +e
  status="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H 'Host: api.autoapi.local' \
      -H "X-AutoAPI-Test-User: ${user_key}" \
      "${gateway_url}/v1/orders/smoke"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway GET key=${user_key}" "${curl_exit}" "${status}"
    exit "${curl_exit}"
  fi
  printf '%s' "${status}"
}

wait_convergence() {
  local api_id="$1"
  wait_until "convergence CONVERGED for API ${api_id}" 45 2 \
    "smoke_curl --fail ${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence | grep -q '\"derivedState\"[[:space:]]*:[[:space:]]*\"CONVERGED\"'"
}

fetch_traffic_splits() {
  local gateway_url="$1"
  smoke_curl --fail "${gateway_url}/internal/v1/traffic-splits" >"${SMOKE_TRAFFIC_FILE}"
  cat "${SMOKE_TRAFFIC_FILE}"
}

fallback_count() {
  python3 - "${SMOKE_TRAFFIC_FILE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
total = 0
for policy in payload.get("policies", []):
    for dest in policy.get("destinations", []):
        if dest.get("name") == "canary":
            total = dest.get("fallbackRequests", 0)
            break
print(total)
PY
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-body.XXXXXX")"
  SMOKE_TRAFFIC_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-traffic.XXXXXX")"
  trap dump_diagnostics EXIT

  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    set_smoke_step "Starting Phase 7 stack"
    docker compose down -v >/dev/null 2>&1 || true
    build_smoke_images_once
    log_step "Starting postgres, redis, stable/canary upstreams, control plane"
    docker compose up -d postgres redis stable-v1 stable-v2 canary-v1 control-plane
    wait_until "Control plane ready" 45 2 wait_http_ready "${CONTROL_PLANE_URL}"
  fi

  set_smoke_step "Creating project, API, pools, route, traffic split"
  project_json="$(control_plane_json "create project" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
    -H 'Content-Type: application/json' \
    -d '{"name":"phase7-traffic-split","description":"Phase 7 smoke"}')"
  project_id="$(json_field "${project_json}" id)"

  api_json="$(control_plane_json "create API" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${project_id}/apis" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-api","host":"api.autoapi.local","basePath":"/"}')"
  api_id="$(json_field "${api_json}" id)"
  PHASE7_API_ID="${api_id}"

  stable_pool_json="$(control_plane_json "create stable pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"stable-pool","loadBalancing":"ROUND_ROBIN"}')"
  stable_pool_id="$(json_field "${stable_pool_json}" id)"

  control_plane_mutate "add stable-v1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${stable_pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://stable-v1:8080","enabled":true,"weight":1}'
  control_plane_mutate "add stable-v2" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${stable_pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://stable-v2:8080","enabled":true,"weight":1}'

  canary_pool_json="$(control_plane_json "create canary pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"canary-pool","loadBalancing":"ROUND_ROBIN"}')"
  canary_pool_id="$(json_field "${canary_pool_json}" id)"

  control_plane_mutate "add canary-v1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${canary_pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://canary-v1:8080","enabled":true,"weight":1}'

  route_json="$(control_plane_json "create route" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${stable_pool_id}\",\"enabled\":true}")"
  route_id="$(json_field "${route_json}" id)"

  policy_json="$(control_plane_json "create traffic split policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/traffic-split-policies" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-canary","selectionKey":"HEADER","selectionKeyName":"X-AutoAPI-Test-User","fallbackMode":"FALLBACK_TO_PRIMARY","enabled":true}')"
  policy_id="$(json_field "${policy_json}" id)"

  control_plane_mutate "add stable destination" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/traffic-split-policies/${policy_id}/destinations" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"stable\",\"upstreamPoolId\":\"${stable_pool_id}\",\"weight\":80,\"priority\":0,\"primary\":true}"
  control_plane_mutate "add canary destination" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/traffic-split-policies/${policy_id}/destinations" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"canary\",\"upstreamPoolId\":\"${canary_pool_id}\",\"weight\":20,\"priority\":1,\"primary\":false}"

  control_plane_mutate "bind traffic split to route" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/traffic-split-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"trafficSplitPolicyId\":\"${policy_id}\"}"

  set_smoke_step "Publishing and activating configuration"
  control_plane_mutate "validate" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate"
  control_plane_mutate "publish" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Phase 7 traffic split"}'
  control_plane_mutate "activate" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":null}'

  export AUTOAPI_GATEWAY_API_ID="${api_id}"
  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    docker compose up -d --no-deps gateway-a gateway-b
  fi
  wait_until "Gateway A ready" 60 2 wait_http_ready "${GATEWAY_A_URL}"
  wait_until "Gateway B ready" 60 2 wait_http_ready "${GATEWAY_B_URL}"
  wait_convergence "${api_id}"

  set_smoke_step "Verifying sticky assignment for user-0042"
  gateway_get_with_key "${GATEWAY_A_URL}" "user-0042" >/dev/null
  class_a="$(destination_class "$(service_from_body)")"
  gateway_get_with_key "${GATEWAY_A_URL}" "user-0042" >/dev/null
  class_a_repeat="$(destination_class "$(service_from_body)")"
  gateway_get_with_key "${GATEWAY_B_URL}" "user-0042" >/dev/null
  class_b="$(destination_class "$(service_from_body)")"
  if [[ "${class_a}" != "${class_a_repeat}" || "${class_a}" != "${class_b}" ]]; then
    echo "Cross-gateway stickiness failed: a=${class_a} repeat=${class_a_repeat} b=${class_b}" >&2
    exit 1
  fi
  log_step "Sticky key user-0042 -> ${class_a} on both gateways"

  set_smoke_step "Verifying 80/20 distribution over 1000 keys"
  stable_count=0
  canary_count=0
  for i in $(seq 1 1000); do
    key="$(printf 'user-%04d' "${i}")"
    gateway_get_with_key "${GATEWAY_A_URL}" "${key}" >/dev/null
    case "$(destination_class "$(service_from_body)")" in
      stable) stable_count=$((stable_count + 1)) ;;
      canary) canary_count=$((canary_count + 1)) ;;
      *)
        echo "Unknown service for key ${key}: $(service_from_body)" >&2
        exit 1
        ;;
    esac
  done
  log_step "Distribution stable=${stable_count} canary=${canary_count}"
  if [[ "${stable_count}" -lt 760 || "${stable_count}" -gt 840 ]]; then
    echo "Stable count ${stable_count} outside 760-840 tolerance" >&2
    exit 1
  fi
  if [[ "${canary_count}" -lt 160 || "${canary_count}" -gt 240 ]]; then
    echo "Canary count ${canary_count} outside 160-240 tolerance" >&2
    exit 1
  fi

  set_smoke_step "Finding deterministic canary key"
  CANARY_KEY=""
  STABLE_KEY=""
  for i in $(seq 1 2000); do
    key="$(printf 'user-%04d' "${i}")"
    gateway_get_with_key "${GATEWAY_A_URL}" "${key}" >/dev/null
    class="$(destination_class "$(service_from_body)")"
    if [[ -z "${CANARY_KEY}" && "${class}" == "canary" ]]; then
      CANARY_KEY="${key}"
    fi
    if [[ -z "${STABLE_KEY}" && "${class}" == "stable" ]]; then
      STABLE_KEY="${key}"
    fi
    if [[ -n "${CANARY_KEY}" && -n "${STABLE_KEY}" ]]; then
      break
    fi
  done
  if [[ -z "${CANARY_KEY}" || -z "${STABLE_KEY}" ]]; then
    echo "Could not find canary/stable keys in bounded range" >&2
    exit 1
  fi
  log_step "Canary key=${CANARY_KEY} stable key=${STABLE_KEY}"

  set_smoke_step "Stopping canary upstream and verifying fallback"
  docker compose stop canary-v1
  wait_until "canary-v1 stopped" 30 2 "docker compose ps canary-v1 | grep -q Exit"

  gateway_get_with_key "${GATEWAY_A_URL}" "${CANARY_KEY}" >/dev/null
  fallback_service="$(service_from_body)"
  if [[ "$(destination_class "${fallback_service}")" != "stable" ]]; then
    echo "Expected fallback to stable for ${CANARY_KEY}, got ${fallback_service}" >&2
    exit 1
  fi
  fetch_traffic_splits "${GATEWAY_A_URL}" >/dev/null
  if [[ "$(fallback_count)" -lt 1 ]]; then
    echo "Expected canary fallback counter increment" >&2
    exit 1
  fi

  gateway_get_with_key "${GATEWAY_A_URL}" "${STABLE_KEY}" >/dev/null
  if [[ "$(destination_class "$(service_from_body)")" != "stable" ]]; then
    echo "Stable key should remain stable during canary outage" >&2
    exit 1
  fi

  set_smoke_step "Restarting canary and verifying recovery"
  docker compose start canary-v1
  wait_until "canary-v1 healthy" 60 3 "docker compose ps canary-v1 | grep -q healthy"

  recovered=false
  for _ in $(seq 1 30); do
    gateway_get_with_key "${GATEWAY_A_URL}" "${CANARY_KEY}" >/dev/null
    if [[ "$(destination_class "$(service_from_body)")" == "canary" ]]; then
      recovered=true
      break
    fi
    sleep 2
  done
  if [[ "${recovered}" != "true" ]]; then
    echo "Canary key did not return to canary after recovery" >&2
    exit 1
  fi

  set_smoke_step "Verifying gateway readiness"
  smoke_curl --fail "${GATEWAY_A_URL}/readyz" >/dev/null
  smoke_curl --fail "${GATEWAY_B_URL}/readyz" >/dev/null

  log_step "Phase 7 traffic split smoke passed"
}

main "$@"
