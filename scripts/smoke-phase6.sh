#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
HEALTH_THRESHOLD="${SMOKE_HEALTH_THRESHOLD:-2}"
RETRY_DRIVE_MAX="${SMOKE_RETRY_DRIVE_MAX:-30}"
BUDGET_EXHAUST_REQUESTS="${SMOKE_BUDGET_EXHAUST_REQUESTS:-40}"
PHASE6_API_ID=""

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_HEALTH_FILE=""
SMOKE_RETRY_FILE=""
SMOKE_SNAPSHOT_FILE=""

# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-retry-lib.sh
source "${ROOT}/scripts/smoke-retry-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_HEALTH_FILE}" "${SMOKE_RETRY_FILE}" "${SMOKE_SNAPSHOT_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    set_smoke_step "Starting cleanup"
    timeout 120 docker compose down -v >/dev/null 2>&1 || true
    log_step "Cleanup completed"
  fi
}

dump_diagnostics() {
  local exit_code="$?"

  if [[ "${exit_code}" -ne 0 ]]; then
    log_step "Phase 6 smoke failed with exit code ${exit_code} at step: ${SMOKE_CURRENT_STEP}"
    dump_compose_smoke_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${PHASE6_API_ID}"
    collect_gateway_thread_dump "gateway-a"
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

convergence_converged() {
  local api_id="$1"
  smoke_curl --fail "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence" \
    | grep -q '"derivedState"[[:space:]]*:[[:space:]]*"CONVERGED"'
}

wait_convergence() {
  local api_id="$1"
  wait_until "convergence CONVERGED for API ${api_id}" 45 2 convergence_converged "${api_id}"
}

upstream_v1_running() {
  docker compose ps --status running upstream-v1 | grep -q upstream-v1
}

assert_pre_failover_setup() {
  local api_id="$1"
  local route_id="$2"
  local retry_policy_id="$3"
  local retry_json snapshot_json status

  retry_json="$(fetch_retry_status)"
  assert_nonblank_gateway_id "${retry_json}"

  snapshot_json="$(smoke_curl --fail "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1")"
  assert_published_retry_policy "${snapshot_json}"

  set_smoke_step "Priming retry budget with setup GET"
  status="$(gateway_get "phase6-prime-budget")"
  log_step "Setup GET completed with HTTP ${status}"
  if [[ "${status}" != "200" ]]; then
    echo "Setup request failed with HTTP ${status}" >&2
    exit 1
  fi

  retry_json="$(fetch_retry_status)"
  assert_nonblank_gateway_id "${retry_json}"
  assert_retry_budget_entry "${retry_json}" "${api_id}" "${route_id}" "${retry_policy_id}"
}

assert_budget_exhausted() {
  local retry_json="$1"
  python3 - "${retry_json}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
budgets = payload.get("budgets") or []
if not budgets:
    raise SystemExit("retry budget entry missing after exhaustion drive")
for entry in budgets:
    used = int(entry.get("retriesUsed") or 0)
    capacity = int(entry.get("retryCapacity") or 0)
    if capacity >= 1 and used >= capacity:
        raise SystemExit(0)
raise SystemExit(f"retry budget not exhausted: used={used} capacity={capacity}")
PY
}

fetch_upstream_health() {
  local curl_exit status
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
    report_curl_failure "fetch upstream-health" "${curl_exit}" "${status}"
    exit 1
  fi
  cat "${SMOKE_HEALTH_FILE}"
}

fetch_retry_status() {
  local curl_exit status
  set +e
  status="$(
    smoke_curl \
      -o "${SMOKE_RETRY_FILE}" \
      -w '%{http_code}' \
      "${GATEWAY_A_URL}/internal/v1/retry-status"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 || "${status}" != "200" ]]; then
    report_curl_failure "fetch retry-status" "${curl_exit}" "${status}"
    exit 1
  fi
  cat "${SMOKE_RETRY_FILE}"
}

gateway_get() {
  local path_suffix="${1:-smoke}"
  local curl_exit status
  set +e
  status="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_A_URL}/v1/orders/${path_suffix}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway GET /v1/orders/${path_suffix}" "${curl_exit}" "${status}"
    exit "${curl_exit}"
  fi
  printf '%s' "${status}"
}

gateway_post() {
  local path_suffix="$1"
  local idempotency_key="${2:-}"
  local curl_exit status
  local headers=(-H 'Host: api.autoapi.local' -H 'Content-Type: application/json')
  if [[ -n "${idempotency_key}" ]]; then
    headers+=(-H "Idempotency-Key: ${idempotency_key}")
  fi
  set +e
  status="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      "${headers[@]}" \
      -d '{"phase6":true}' \
      "${GATEWAY_A_URL}/v1/orders/${path_suffix}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway POST /v1/orders/${path_suffix}" "${curl_exit}" "${status}"
    exit "${curl_exit}"
  fi
  printf '%s' "${status}"
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-body.XXXXXX")"
  SMOKE_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-health.XXXXXX")"
  SMOKE_RETRY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-retry.XXXXXX")"
  SMOKE_SNAPSHOT_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-snapshot.XXXXXX")"
  trap dump_diagnostics EXIT

  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    set_smoke_step "Starting control-plane stack"
    docker compose down -v >/dev/null 2>&1 || true
    build_smoke_images_once
    start_smoke_base_stack
    wait_until "Control plane ready" 45 2 wait_http_ready "${CONTROL_PLANE_URL}"
    log_step "Control plane ready"
  fi

  set_smoke_step "Creating project, API, pool, route"
  project_json="$(control_plane_json "create project" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
    -H 'Content-Type: application/json' \
    -d '{"name":"phase6-retry","description":"Phase 6 retry smoke"}')"
  project_id="$(json_field "${project_json}" id)"

  api_json="$(control_plane_json "create API" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${project_id}/apis" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-api","host":"api.autoapi.local","basePath":"/"}')"
  api_id="$(json_field "${api_json}" id)"
  PHASE6_API_ID="${api_id}"

  pool_json="$(control_plane_json "create upstream pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-v1","loadBalancing":"ROUND_ROBIN"}')"
  pool_id="$(json_field "${pool_json}" id)"

  target_v1_json="$(control_plane_json "create upstream-v1 target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}')"
  target_v1_id="$(json_field "${target_v1_json}" id)"

  control_plane_mutate "create upstream-v2 target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v2:8080","enabled":true,"weight":1}'

  route_json="$(control_plane_json "create route" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}")"
  route_id="$(json_field "${route_json}" id)"

  set_smoke_step "Creating backend health policy"
  health_json="$(control_plane_json "create backend health policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/backend-health-policies" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-passive-health\",\"consecutiveFailureThreshold\":${HEALTH_THRESHOLD},\"ejectionDurationSeconds\":5,\"maxEjectionPercent\":50,\"enabled\":true}")"
  health_policy_id="$(json_field "${health_json}" id)"

  control_plane_mutate "bind health policy to pool" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/backend-health-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"backendHealthPolicyId\":\"${health_policy_id}\"}"

  set_smoke_step "Creating retry policy"
  retry_json="$(control_plane_json "create retry policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/retry-policies" \
    -H 'Content-Type: application/json' \
    -d '{
      "name": "safe-orders-retry",
      "maxAttempts": 2,
      "perAttemptTimeoutMs": 1000,
      "retryOnConnectFailure": true,
      "retryOnConnectionReset": true,
      "retryOnDnsFailure": true,
      "retryOnResponseTimeout": true,
      "retryableMethods": ["GET", "POST"],
      "requireIdempotencyKeyForUnsafeMethods": true,
      "budgetPercent": 50,
      "budgetMinRetriesPerSecond": 2,
      "budgetWindowSeconds": 10,
      "enabled": true
    }')"
  retry_policy_id="$(json_field "${retry_json}" id)"

  control_plane_mutate "bind retry policy to route" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/retry-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"retryPolicyId\":\"${retry_policy_id}\"}"

  set_smoke_step "Publishing and activating configuration"
  control_plane_mutate "validate configuration" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate"
  control_plane_mutate "publish configuration version 1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Phase 6 retry"}'
  control_plane_mutate "activate configuration version 1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":null}'

  export AUTOAPI_GATEWAY_API_ID="${api_id}"
  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    start_smoke_gateways gateway-a
  fi
  wait_until "Gateway A ready" 45 2 wait_http_ready "${GATEWAY_A_URL}"
  wait_convergence "${api_id}"
  log_step "Gateway ready"

  set_smoke_step "Verifying normal requests succeed"
  for i in $(seq 1 4); do
    status="$(gateway_get "phase6-normal-${i}")"
    if [[ "${status}" != "200" ]]; then
      echo "Normal request ${i} failed with HTTP ${status}" >&2
      exit 1
    fi
  done

  set_smoke_step "Verifying retry policy activation before failover"
  assert_pre_failover_setup "${api_id}" "${route_id}" "${retry_policy_id}"

  set_smoke_step "Stopping upstream-v1"
  docker compose stop upstream-v1

  set_smoke_step "Sending failover GET"
  retry_success=false
  for attempt in $(seq 1 "${RETRY_DRIVE_MAX}"); do
    status="$(gateway_get "phase6-retry-get-${attempt}")"
    log_step "Failover GET attempt ${attempt} completed with HTTP ${status}"
    if [[ "${status}" == "200" ]]; then
      service="$(service_from_body)"
      if [[ "${service}" == "upstream-v2" ]]; then
        health_json="$(fetch_upstream_health)"
        parsed="$(read_parsed_target_health "${health_json}" "${target_v1_id}")"
        IFS='|' read -r v1_state v1_failures _ _ <<<"${parsed}"
        if [[ "${v1_failures:-0}" -ge 1 ]]; then
          retry_success=true
          log_step "Failover GET verified on attempt ${attempt}; upstream-v2 served; v1 failures=${v1_failures}"
          break
        fi
      fi
    elif [[ "${status}" != "502" && "${status}" != "504" ]]; then
      echo "Unexpected HTTP ${status} during GET retry drive" >&2
      exit 1
    fi
  done
  if [[ "${retry_success}" != "true" ]]; then
    echo "GET retry failover did not succeed within ${RETRY_DRIVE_MAX} attempts" >&2
    exit 1
  fi
  log_step "Failover GET completed"

  set_smoke_step "Testing unsafe POST without idempotency key"
  post_no_key_502=false
  for attempt in $(seq 1 10); do
    status="$(gateway_post "phase6-post-no-key-${attempt}" "")"
    log_step "POST without key attempt ${attempt} completed with HTTP ${status}"
    if [[ "${status}" == "502" || "${status}" == "504" ]]; then
      post_no_key_502=true
      break
    fi
    if [[ "${status}" == "200" ]]; then
      echo "POST without key unexpectedly succeeded (RR selected healthy target)" >&2
      break
    fi
  done
  if [[ "${post_no_key_502}" != "true" ]]; then
    echo "Expected POST without Idempotency-Key to surface first-attempt failure (502/504)" >&2
    exit 1
  fi

  set_smoke_step "Testing POST with Idempotency-Key retry"
  post_with_key_ok=false
  for attempt in $(seq 1 "${RETRY_DRIVE_MAX}"); do
    status="$(gateway_post "phase6-post-with-key-${attempt}" "phase6-smoke-key-${attempt}")"
    if [[ "${status}" == "200" ]]; then
      post_with_key_ok=true
      break
    fi
  done
  if [[ "${post_with_key_ok}" != "true" ]]; then
    echo "POST with Idempotency-Key did not succeed via retry" >&2
    exit 1
  fi

  set_smoke_step "Restarting upstream-v1 before budget exhaustion"
  docker compose start upstream-v1
  wait_until "upstream-v1 running after restart" 15 2 upstream_v1_running

  set_smoke_step "Building retry budget capacity"
  for i in $(seq 1 20); do
    status="$(gateway_get "phase6-budget-prime-${i}")"
    if [[ "${status}" != "200" ]]; then
      echo "Budget prime request ${i} failed with HTTP ${status}" >&2
      exit 1
    fi
  done

  set_smoke_step "Testing retry-budget exhaustion"
  docker compose stop upstream-v1
  for i in $(seq 1 "${BUDGET_EXHAUST_REQUESTS}"); do
    status="$(gateway_get "phase6-budget-${i}")"
    if [[ "${status}" != "200" && "${status}" != "502" && "${status}" != "504" ]]; then
      echo "Budget drive request ${i} returned unexpected HTTP ${status}" >&2
      exit 1
    fi
  done
  retry_status="$(fetch_retry_status)"
  assert_budget_exhausted "${retry_status}"

  set_smoke_step "Stopping remaining upstream to prove budget denial"
  docker compose stop upstream-v2
  budget_denied=false
  for attempt in $(seq 1 15); do
    status="$(gateway_get "phase6-budget-check-${attempt}")"
    if [[ "${status}" == "502" || "${status}" == "504" ]]; then
      budget_denied=true
      break
    fi
  done
  if [[ "${budget_denied}" != "true" ]]; then
    echo "Expected terminal 502/504 when both upstreams are down and budget exhausted" >&2
    exit 1
  fi

  smoke_curl --fail "${GATEWAY_A_URL}/readyz" >/dev/null

  set_smoke_step "Restarting upstreams for recovery"
  docker compose start upstream-v1 upstream-v2
  recovered=false
  for attempt in $(seq 1 30); do
    status="$(gateway_get "phase6-recovery-${attempt}")"
    if [[ "${status}" == "200" ]]; then
      recovered=true
      break
    fi
    sleep 1
  done
  if [[ "${recovered}" != "true" ]]; then
    echo "Routing did not recover after upstream restart" >&2
    exit 1
  fi

  log_step "Phase 6 retry smoke passed"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
