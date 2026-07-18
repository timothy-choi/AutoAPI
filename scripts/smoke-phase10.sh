#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

SMOKE_STARTED_AT="$(date +%s)"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
LEASE_SECONDS="${SMOKE_DISCOVERY_LEASE_SECONDS:-10}"
HEARTBEAT_INTERVAL_SECONDS="${SMOKE_DISCOVERY_HEARTBEAT_INTERVAL:-3}"
INSTANCE_A="phase10-instance-a"
INSTANCE_B="phase10-instance-b"
PHASE10_API_ID=""
PHASE10_PROJECT_ID=""
PHASE10_SERVICE_ID=""
HEARTBEAT_A_PID=""
HEARTBEAT_B_PID=""

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
LAST_HTTP_STATUS=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"

cleanup() {
  stop_heartbeat "${HEARTBEAT_A_PID}"
  stop_heartbeat "${HEARTBEAT_B_PID}"
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
    log_step "Phase 10 smoke failed with exit code ${exit_code} at step: ${SMOKE_CURRENT_STEP}"
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${PHASE10_API_ID}"
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

convergence_reached() {
  local api_id="$1"
  local expected_state="$2"
  smoke_curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence" \
    | grep -q "\"derivedState\"[[:space:]]*:[[:space:]]*\"${expected_state}\""
}

wait_convergence() {
  local api_id="$1"
  wait_until "convergence CONVERGED for API ${api_id}" 45 2 \
    convergence_reached "${api_id}" "CONVERGED"
}

instance_status() {
  local service_id="$1"
  local instance_id="$2"
  smoke_curl --fail --silent \
    "${CONTROL_PLANE_URL}/api/v1/services/${service_id}/instances/${instance_id}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])'
}

wait_instance_status() {
  local service_id="$1"
  local instance_id="$2"
  local expected="$3"
  local description="$4"
  wait_until "${description}" 30 2 test "$(instance_status "${service_id}" "${instance_id}")" = "${expected}"
}

stop_heartbeat() {
  local pid="${1:-}"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    wait "${pid}" 2>/dev/null || true
  fi
}

start_heartbeat() {
  local service_id="$1"
  local instance_id="$2"
  (
    while true; do
      smoke_curl --fail --silent \
        -X POST "${CONTROL_PLANE_URL}/api/v1/services/${service_id}/instances/${instance_id}/heartbeat" \
        -H 'Content-Type: application/json' \
        -d "{\"leaseDurationSeconds\":${LEASE_SECONDS}}" >/dev/null 2>&1 || true
      sleep "${HEARTBEAT_INTERVAL_SECONDS}"
    done
  ) &
  printf '%s' "$!"
}

register_instance() {
  local service_id="$1"
  local instance_id="$2"
  local host="$3"
  local upstream_label="$4"
  control_plane_json "register ${instance_id} -> ${upstream_label}" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/services/${service_id}/instances/register" \
    -H 'Content-Type: application/json' \
    -d "{
      \"instanceId\": \"${instance_id}\",
      \"host\": \"${host}\",
      \"port\": 8080,
      \"scheme\": \"http\",
      \"weight\": 100,
      \"leaseDurationSeconds\": ${LEASE_SECONDS}
    }" >/dev/null
}

gateway_get() {
  local curl_exit=0
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
  if [[ "${LAST_HTTP_STATUS}" != "200" ]]; then
    echo "Expected gateway HTTP 200, got ${LAST_HTTP_STATUS}" >&2
    cat "${SMOKE_BODY_FILE}" >&2 || true
    exit 1
  fi
}

gateway_expect_upstream() {
  local expected="$1"
  local context="$2"
  gateway_get
  local actual
  actual="$(service_from_body)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${context}: expected upstream ${expected}, got ${actual}" >&2
    exit 1
  fi
}

collect_upstream_hits() {
  local hits_v1=0
  local hits_v2=0
  local i actual
  for i in $(seq 1 24); do
    gateway_get
    actual="$(service_from_body)"
    case "${actual}" in
      upstream-v1) hits_v1=$((hits_v1 + 1)) ;;
      upstream-v2) hits_v2=$((hits_v2 + 1)) ;;
      *)
        echo "Unexpected upstream service ${actual} on request ${i}" >&2
        exit 1
        ;;
    esac
  done
  printf '%s %s' "${hits_v1}" "${hits_v2}"
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase10-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase10-body.XXXXXX")"
  trap dump_diagnostics EXIT

  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    set_smoke_step "Starting Phase 10 stack"
    docker compose down -v >/dev/null 2>&1 || true
    build_smoke_images_once
    log_step "Starting postgres, redis, mock upstreams, control plane"
    docker compose up -d postgres redis upstream-v1 upstream-v2 control-plane
    wait_until "control-plane ready" 45 2 wait_http_ready "${CONTROL_PLANE_URL}"
  fi

  set_smoke_step "Creating project, API, route scaffold, discovered service"
  project_json="$(control_plane_json "create project" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
    -H 'Content-Type: application/json' \
    -d '{"name":"phase10-discovery","description":"Phase 10 service discovery smoke"}')"
  PHASE10_PROJECT_ID="$(json_field "${project_json}" id)"

  api_json="$(control_plane_json "create API" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE10_PROJECT_ID}/apis" \
    -H 'Content-Type: application/json' \
    -d '{"name":"phase10-orders","host":"api.autoapi.local","basePath":"/"}')"
  PHASE10_API_ID="$(json_field "${api_json}" id)"

  pool_json="$(control_plane_json "create placeholder pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE10_API_ID}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"placeholder","loadBalancing":"ROUND_ROBIN"}')"
  pool_id="$(json_field "${pool_json}" id)"

  route_json="$(control_plane_json "create route" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE10_API_ID}/routes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}")"
  route_id="$(json_field "${route_json}" id)"

  service_json="$(control_plane_json "create discovered service" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE10_PROJECT_ID}/services" \
    -H 'Content-Type: application/json' \
    -d '{
      "name": "orders-backend",
      "description": "Phase 10 logical service",
      "selectionStrategy": "ROUND_ROBIN",
      "registrationMode": "OPEN",
      "defaultScheme": "http",
      "defaultPort": 8080,
      "enabled": true
    }')"
  PHASE10_SERVICE_ID="$(json_field "${service_json}" id)"

  control_plane_mutate "bind route to discovered service" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/discovered-service" \
    -H 'Content-Type: application/json' \
    -d "{\"serviceId\":\"${PHASE10_SERVICE_ID}\"}"

  set_smoke_step "Publishing baseline configuration"
  control_plane_mutate "validate" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE10_API_ID}/config/validate"
  control_plane_mutate "publish" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE10_API_ID}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Phase 10 service discovery baseline"}'
  control_plane_mutate "activate" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${PHASE10_API_ID}/config/versions/1/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":null}'

  set_smoke_step "Registering instance A and starting heartbeats"
  register_instance "${PHASE10_SERVICE_ID}" "${INSTANCE_A}" "upstream-v1" "upstream-v1"
  HEARTBEAT_A_PID="$(start_heartbeat "${PHASE10_SERVICE_ID}" "${INSTANCE_A}")"
  wait_convergence "${PHASE10_API_ID}"

  export AUTOAPI_GATEWAY_API_ID="${PHASE10_API_ID}"
  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    set_smoke_step "Starting gateway-a"
    start_smoke_gateways gateway-a
    wait_until "gateway-a ready" 60 2 wait_http_ready "${GATEWAY_A_URL}"
  fi
  wait_convergence "${PHASE10_API_ID}"

  set_smoke_step "Verifying traffic reaches instance A backend"
  gateway_expect_upstream "upstream-v1" "single-instance routing"

  set_smoke_step "Registering instance B and verifying dual-backend traffic"
  register_instance "${PHASE10_SERVICE_ID}" "${INSTANCE_B}" "upstream-v2" "upstream-v2"
  HEARTBEAT_B_PID="$(start_heartbeat "${PHASE10_SERVICE_ID}" "${INSTANCE_B}")"
  wait_convergence "${PHASE10_API_ID}"

  read -r hits_v1 hits_v2 <<<"$(collect_upstream_hits)"
  log_step "Dual-instance distribution upstream-v1=${hits_v1} upstream-v2=${hits_v2}"
  if [[ "${hits_v1}" -lt 1 || "${hits_v2}" -lt 1 ]]; then
    echo "Expected traffic to both upstream-v1 and upstream-v2, got v1=${hits_v1} v2=${hits_v2}" >&2
    exit 1
  fi

  set_smoke_step "Stopping heartbeats for A and waiting for STALE"
  stop_heartbeat "${HEARTBEAT_A_PID}"
  HEARTBEAT_A_PID=""
  wait_instance_status "${PHASE10_SERVICE_ID}" "${INSTANCE_A}" "STALE" \
    "instance A STALE after lease expiry"
  wait_convergence "${PHASE10_API_ID}"

  set_smoke_step "Verifying stale instance A excluded from runtime routing"
  for _ in $(seq 1 12); do
    gateway_expect_upstream "upstream-v2" "post-stale routing"
  done

  set_smoke_step "Recovering instance A via heartbeat"
  HEARTBEAT_A_PID="$(start_heartbeat "${PHASE10_SERVICE_ID}" "${INSTANCE_A}")"
  wait_instance_status "${PHASE10_SERVICE_ID}" "${INSTANCE_A}" "READY" \
    "instance A READY after recovery heartbeat"
  wait_convergence "${PHASE10_API_ID}"
  read -r hits_v1 hits_v2 <<<"$(collect_upstream_hits)"
  if [[ "${hits_v1}" -lt 1 || "${hits_v2}" -lt 1 ]]; then
    echo "Expected traffic to both backends after recovery, got v1=${hits_v1} v2=${hits_v2}" >&2
    exit 1
  fi

  set_smoke_step "Draining instance B"
  control_plane_mutate "drain instance B" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/services/${PHASE10_SERVICE_ID}/instances/${INSTANCE_B}/drain"
  wait_instance_status "${PHASE10_SERVICE_ID}" "${INSTANCE_B}" "DRAINING" \
    "instance B DRAINING"
  wait_convergence "${PHASE10_API_ID}"
  stop_heartbeat "${HEARTBEAT_B_PID}"
  HEARTBEAT_B_PID=""
  for _ in $(seq 1 12); do
    gateway_expect_upstream "upstream-v1" "post-drain routing"
  done

  set_smoke_step "Deregistering instances"
  control_plane_mutate "deregister instance A" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/services/${PHASE10_SERVICE_ID}/instances/${INSTANCE_A}/deregister"
  control_plane_mutate "deregister instance B" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/services/${PHASE10_SERVICE_ID}/instances/${INSTANCE_B}/deregister"
  stop_heartbeat "${HEARTBEAT_A_PID}"
  HEARTBEAT_A_PID=""
  wait_instance_status "${PHASE10_SERVICE_ID}" "${INSTANCE_A}" "DEREGISTERED" \
    "instance A DEREGISTERED"
  wait_instance_status "${PHASE10_SERVICE_ID}" "${INSTANCE_B}" "DEREGISTERED" \
    "instance B DEREGISTERED"

  set_smoke_step "Verifying management APIs"
  services_json="$(control_plane_json "list discovered services" \
    "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE10_PROJECT_ID}/services")"
  echo "${services_json}" | grep -q "${PHASE10_SERVICE_ID}"
  echo "${services_json}" | grep -q '"membershipVersion"'

  service_detail="$(control_plane_json "get discovered service" \
    "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE10_PROJECT_ID}/services/${PHASE10_SERVICE_ID}")"
  echo "${service_detail}" | grep -q '"name"[[:space:]]*:[[:space:]]*"orders-backend"'

  instances_json="$(control_plane_json "list service instances" \
    "${CONTROL_PLANE_URL}/api/v1/services/${PHASE10_SERVICE_ID}/instances?limit=10")"
  echo "${instances_json}" | grep -q "${INSTANCE_A}"
  echo "${instances_json}" | grep -q "${INSTANCE_B}"
  echo "${instances_json}" | grep -q '"status"[[:space:]]*:[[:space:]]*"DEREGISTERED"'

  credential_json="$(control_plane_json "create registration credential" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE10_PROJECT_ID}/services/${PHASE10_SERVICE_ID}/registration-credentials" \
    -H 'Content-Type: application/json' \
    -d '{"name":"phase10-smoke-credential"}')"
  echo "${credential_json}" | grep -q '"plaintextToken"'

  credentials_list="$(control_plane_json "list registration credentials" \
    "${CONTROL_PLANE_URL}/api/v1/projects/${PHASE10_PROJECT_ID}/services/${PHASE10_SERVICE_ID}/registration-credentials")"
  echo "${credentials_list}" | grep -q '"credentialId"'

  set_smoke_step "Verifying discovery metrics excerpts"
  cp_metrics="$(smoke_curl --fail --silent "${CONTROL_PLANE_URL}/actuator/prometheus")"
  echo "${cp_metrics}" | grep -q 'autoapi_service_discovery_registrations_total'
  echo "${cp_metrics}" | grep -q 'autoapi_service_discovery_heartbeats_total'
  echo "${cp_metrics}" | grep -q 'autoapi_service_discovery_stale_transitions_total'
  echo "${cp_metrics}" | grep -q 'autoapi_service_discovery_recoveries_total'
  echo "${cp_metrics}" | grep -q 'autoapi_service_discovery_deregistrations_total'

  gw_metrics="$(smoke_curl --fail --silent "${GATEWAY_A_URL}/actuator/prometheus")"
  echo "${gw_metrics}" | grep -q 'autoapi_gateway_service_instance_selections_total'

  echo "Phase 10 smoke passed — service discovery lifecycle, routing, and metrics validated"
}

main "$@"
