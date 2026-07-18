#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
GATEWAY_B_URL="${GATEWAY_B_URL:-http://localhost:8082}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
HEALTH_THRESHOLD="${SMOKE_HEALTH_THRESHOLD:-2}"
EJECTION_SECONDS="${SMOKE_EJECTION_SECONDS:-5}"
HEALTH_MAX_EJECTION_PERCENT="${SMOKE_HEALTH_MAX_EJECTION_PERCENT:-100}"
DETECTION_MAX_ATTEMPTS="${SMOKE_DETECTION_MAX_ATTEMPTS:-$((HEALTH_THRESHOLD + 6))}"
RECOVERY_MAX_ATTEMPTS="${SMOKE_RECOVERY_MAX_ATTEMPTS:-$((EJECTION_SECONDS + 20))}"
PHASE7_API_ID=""
CANARY_KEY=""
STABLE_KEY=""

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_TRAFFIC_FILE=""
SMOKE_HEALTH_FILE=""
LAST_HTTP_STATUS=""
CANARY_TARGET_ID=""

# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_TRAFFIC_FILE}" "${SMOKE_HEALTH_FILE}"
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

error_code_from_body() {
  python3 - "${SMOKE_BODY_FILE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
error = payload.get("error") or {}
print(error.get("code", ""))
PY
}

service_is_stable() {
  case "${1:-}" in
    stable-v1|stable-v2) return 0 ;;
    *) return 1 ;;
  esac
}

fetch_upstream_health() {
  local curl_exit=0
  local status=""
  set +e
  status="$(
    smoke_curl \
      -o "${SMOKE_HEALTH_FILE}" \
      -w '%{http_code}' \
      "${GATEWAY_A_URL}/internal/v1/upstream-health"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 || "${status}" != "200" ]]; then
    echo "Failed to fetch internal upstream-health: HTTP ${status:-unknown} (curl exit ${curl_exit})" >&2
    if [[ -f "${SMOKE_HEALTH_FILE}" ]]; then
      cat "${SMOKE_HEALTH_FILE}" >&2
    fi
    return 1
  fi
  cat "${SMOKE_HEALTH_FILE}"
}

read_traffic_counter() {
  local destination_name="$1"
  local field="$2"
  python3 - "${SMOKE_TRAFFIC_FILE}" "${destination_name}" "${field}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
destination_name = sys.argv[2]
field = sys.argv[3]
for policy in payload.get("policies", []):
    for dest in policy.get("destinations", []):
        if dest.get("name") == destination_name:
            print(dest.get(field, 0))
            raise SystemExit(0)
print(0)
PY
}

read_traffic_unavailable_count() {
  python3 - "${SMOKE_TRAFFIC_FILE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
for policy in payload.get("policies", []):
    for dest in policy.get("destinations", []):
        if dest.get("destinationId") is None or dest.get("name") == "unavailable":
            print(dest.get("assignedRequests", 0))
            raise SystemExit(0)
print(0)
PY
}

print_fallback_diagnostics() {
  local attempt="$1"
  local key="$2"
  local health_json="${3:-}"
  local service=""
  local error_code=""
  service="$(service_from_body 2>/dev/null || true)"
  error_code="$(error_code_from_body 2>/dev/null || true)"
  echo "Fallback diagnostics:" >&2
  echo "  attempt=${attempt}" >&2
  echo "  key=${key}" >&2
  echo "  http=${LAST_HTTP_STATUS:-unknown}" >&2
  echo "  service=${service:-<none>}" >&2
  echo "  errorCode=${error_code:-<none>}" >&2
  if [[ -n "${health_json}" ]]; then
    echo "  canaryHealth=${health_json}" >&2
  elif [[ -n "${CANARY_TARGET_ID}" ]]; then
    local parsed=""
    if parsed="$(read_parsed_target_health "$(fetch_upstream_health 2>/dev/null || true)" "${CANARY_TARGET_ID}" 2>/dev/null)"; then
      IFS='|' read -r state failures ejected_until category <<<"${parsed}"
      echo "  canaryHealth=state=${state} consecutiveFailures=${failures} ejectedUntil=${ejected_until:-null} lastFailureCategory=${category:-null}" >&2
    fi
  fi
  if [[ -f "${SMOKE_TRAFFIC_FILE}" ]]; then
    echo "  canaryAssigned=$(read_traffic_counter canary assignedRequests)" >&2
    echo "  stableFallback=$(read_traffic_counter stable fallbackRequests)" >&2
    echo "  unavailable=$(read_traffic_unavailable_count)" >&2
  fi
  if [[ -f "${SMOKE_BODY_FILE}" ]]; then
    echo "  responseBody=$(tr '\n' ' ' <"${SMOKE_BODY_FILE}" | head -c 500)" >&2
  fi
}

gateway_get_with_key_allow_errors() {
  local gateway_url="$1"
  local user_key="$2"
  local curl_exit=0
  set +e
  LAST_HTTP_STATUS="$(
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
    report_curl_failure "gateway GET key=${user_key}" "${curl_exit}" "${LAST_HTTP_STATUS}"
    exit "${curl_exit}"
  fi
  printf '%s' "${LAST_HTTP_STATUS}"
}

gateway_get_with_key_expect_200() {
  local gateway_url="$1"
  local user_key="$2"
  local context="$3"
  gateway_get_with_key_allow_errors "${gateway_url}" "${user_key}" >/dev/null
  if [[ "${LAST_HTTP_STATUS}" != "200" ]]; then
    echo "${context}: expected HTTP 200, got ${LAST_HTTP_STATUS}" >&2
    print_fallback_diagnostics "-" "${user_key}"
    exit 1
  fi
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
  gateway_get_with_key_expect_200 "$1" "$2" "gateway GET key=$2"
  printf '%s' "${LAST_HTTP_STATUS}"
}

wait_convergence() {
  local api_id="$1"
  wait_until "convergence CONVERGED for API ${api_id}" 45 2 \
    convergence_reached "${api_id}" "CONVERGED"
}

convergence_reached() {
  local api_id="$1"
  local expected_state="$2"
  smoke_curl --fail "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence" \
    | grep -q "\"derivedState\"[[:space:]]*:[[:space:]]*\"${expected_state}\""
}

compose_service_is_stopped() {
  local service="$1"
  local container_id state
  container_id="$(resolve_container_id "${service}" 2>/dev/null || true)"
  if [[ -z "${container_id}" ]]; then
    return 0
  fi
  state="$(docker inspect -f '{{.State.Status}}' "${container_id}" 2>/dev/null || true)"
  [[ "${state}" == "exited" ]]
}

fetch_traffic_splits() {
  local gateway_url="$1"
  smoke_curl --fail "${gateway_url}/internal/v1/traffic-splits" >"${SMOKE_TRAFFIC_FILE}"
  cat "${SMOKE_TRAFFIC_FILE}"
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-body.XXXXXX")"
  SMOKE_TRAFFIC_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-traffic.XXXXXX")"
  SMOKE_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase7-health.XXXXXX")"
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

  canary_target_json="$(control_plane_json "add canary-v1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${canary_pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://canary-v1:8080","enabled":true,"weight":1}')"
  CANARY_TARGET_ID="$(json_field "${canary_target_json}" id)"

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

  health_policy_json="$(control_plane_json "create backend health policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/backend-health-policies" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"phase7-passive-health\",\"consecutiveFailureThreshold\":${HEALTH_THRESHOLD},\"ejectionDurationSeconds\":${EJECTION_SECONDS},\"maxEjectionPercent\":${HEALTH_MAX_EJECTION_PERCENT},\"enabled\":true}")"
  health_policy_id="$(json_field "${health_policy_json}" id)"

  control_plane_mutate "bind health policy to stable pool" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${stable_pool_id}/backend-health-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"backendHealthPolicyId\":\"${health_policy_id}\"}"
  control_plane_mutate "bind health policy to canary pool" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${canary_pool_id}/backend-health-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"backendHealthPolicyId\":\"${health_policy_id}\"}"

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

  set_smoke_step "Verifying canary key reaches canary while healthy"
  gateway_get_with_key_expect_200 "${GATEWAY_A_URL}" "${CANARY_KEY}" "pre-stop canary probe"
  pre_stop_service="$(service_from_body)"
  if [[ "$(destination_class "${pre_stop_service}")" != "canary" ]]; then
    echo "Expected ${CANARY_KEY} to reach canary before shutdown, got ${pre_stop_service}" >&2
    exit 1
  fi

  fetch_traffic_splits "${GATEWAY_A_URL}" >/dev/null
  baseline_canary_assigned="$(read_traffic_counter canary assignedRequests)"
  baseline_stable_fallback="$(read_traffic_counter stable fallbackRequests)"
  baseline_unavailable="$(read_traffic_unavailable_count)"
  log_step "Baseline counters canaryAssigned=${baseline_canary_assigned} stableFallback=${baseline_stable_fallback} unavailable=${baseline_unavailable}"

  set_smoke_step "Stopping canary upstream and verifying passive-health fallback"
  docker compose stop canary-v1
  wait_until "canary-v1 stopped" 30 2 compose_service_is_stopped canary-v1

  ejected=false
  fallback_observed=false
  observed_502=0
  detection_attempt=0
  health_json=""

  for detection_attempt in $(seq 1 "${DETECTION_MAX_ATTEMPTS}"); do
    gateway_get_with_key_allow_errors "${GATEWAY_A_URL}" "${CANARY_KEY}" >/dev/null

    if [[ "${LAST_HTTP_STATUS}" == "200" ]]; then
      service="$(service_from_body)"
      if service_is_stable "${service}"; then
        fallback_observed=true
        log_step "Passive-health detection request ${detection_attempt} returned stable fallback (${service})"
        break
      fi
      echo "Unexpected HTTP 200 to ${service} for ${CANARY_KEY} during canary outage detection" >&2
      print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
      exit 1
    fi

    case "${LAST_HTTP_STATUS}" in
      502|503)
        observed_502=$((observed_502 + 1))
        log_step "Passive-health detection request ${detection_attempt} returned ${LAST_HTTP_STATUS}"
        ;;
      *)
        echo "Unexpected HTTP ${LAST_HTTP_STATUS} while detecting canary failure (attempt ${detection_attempt})" >&2
        print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
        exit 1
        ;;
    esac

    health_json="$(fetch_upstream_health)"
    parsed_health=""
    if ! parsed_health="$(read_parsed_target_health "${health_json}" "${CANARY_TARGET_ID}")"; then
      print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
      exit 1
    fi
    IFS='|' read -r state failures ejected_until category <<<"${parsed_health}"
    log_step "  canary target state=${state} consecutiveFailures=${failures} lastFailureCategory=${category:-null} ejectedUntil=${ejected_until:-null}"

    if [[ "${state}" == "EJECTED" ]]; then
      ejected=true
    fi

    if [[ "${ejected}" == "true" ]]; then
      gateway_get_with_key_allow_errors "${GATEWAY_A_URL}" "${CANARY_KEY}" >/dev/null
      if [[ "${LAST_HTTP_STATUS}" == "200" ]] && service_is_stable "$(service_from_body)"; then
        fallback_observed=true
        break
      fi
    fi

    sleep 1
  done

  if [[ "${ejected}" != "true" ]]; then
    echo "canary-v1 did not become EJECTED within ${DETECTION_MAX_ATTEMPTS} detection attempts" >&2
    print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
    exit 1
  fi

  if [[ "${fallback_observed}" != "true" ]]; then
    echo "Expected stable fallback after passive-health ejection:" >&2
    echo "  key=${CANARY_KEY}" >&2
    echo "  http=${LAST_HTTP_STATUS:-unknown}" >&2
    echo "  service=$(service_from_body 2>/dev/null || echo '<none>')" >&2
    echo "  errorCode=$(error_code_from_body 2>/dev/null || echo '<none>')" >&2
    print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
    exit 1
  fi

  if [[ "${observed_502}" -lt 1 ]]; then
    echo "Expected at least one transport failure while detecting canary outage, got ${observed_502}" >&2
    print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
    exit 1
  fi

  fetch_traffic_splits "${GATEWAY_A_URL}" >/dev/null
  current_stable_fallback="$(read_traffic_counter stable fallbackRequests)"
  if [[ "${current_stable_fallback}" -lt $((baseline_stable_fallback + 1)) ]]; then
    echo "Expected stable fallbackRequests to increase by at least 1, baseline=${baseline_stable_fallback} current=${current_stable_fallback}" >&2
    print_fallback_diagnostics "${detection_attempt}" "${CANARY_KEY}" "${health_json}"
    exit 1
  fi
  log_step "Stable fallback counter delta=$((current_stable_fallback - baseline_stable_fallback))"

  gateway_get_with_key_expect_200 "${GATEWAY_A_URL}" "${STABLE_KEY}" "stable key during canary outage"
  if ! service_is_stable "$(service_from_body)"; then
    echo "Stable key should remain stable during canary outage, got $(service_from_body)" >&2
    exit 1
  fi

  set_smoke_step "Restarting canary and verifying recovery"
  docker compose start canary-v1
  wait_compose_service_healthy canary-v1 "canary-v1 healthy"

  recovered=false
  recovery_attempt=0
  for recovery_attempt in $(seq 1 "${RECOVERY_MAX_ATTEMPTS}"); do
    health_json="$(fetch_upstream_health)"
    parsed_health=""
    if parsed_health="$(read_parsed_target_health "${health_json}" "${CANARY_TARGET_ID}")"; then
      IFS='|' read -r state failures ejected_until category <<<"${parsed_health}"
      if [[ "${state}" == "HEALTHY" ]]; then
        gateway_get_with_key_allow_errors "${GATEWAY_A_URL}" "${CANARY_KEY}" >/dev/null
        if [[ "${LAST_HTTP_STATUS}" == "200" ]] && [[ "$(destination_class "$(service_from_body)")" == "canary" ]]; then
          recovered=true
          break
        fi
      fi
    fi
    sleep 1
  done

  if [[ "${recovered}" != "true" ]]; then
    echo "Canary key did not return to canary after recovery and ejection expiry" >&2
    print_fallback_diagnostics "${recovery_attempt}" "${CANARY_KEY}" "${health_json}"
    exit 1
  fi
  log_step "Canary key ${CANARY_KEY} returned to canary after ejection expiry"

  set_smoke_step "Verifying gateway readiness"
  smoke_curl --fail "${GATEWAY_A_URL}/readyz" >/dev/null
  smoke_curl --fail "${GATEWAY_B_URL}/readyz" >/dev/null

  log_step "Phase 7 traffic split smoke passed"
}

main "$@"
