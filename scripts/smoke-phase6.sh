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

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_HEALTH_FILE=""
SMOKE_RETRY_FILE=""
SMOKE_SNAPSHOT_FILE=""

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_HEALTH_FILE}" "${SMOKE_RETRY_FILE}" "${SMOKE_SNAPSHOT_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    docker compose down -v >/dev/null 2>&1 || true
  fi
}

# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-retry-lib.sh
source "${ROOT}/scripts/smoke-retry-lib.sh"

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
  local status
  set +e
  status="$(curl --silent --show-error \
    -D "${SMOKE_HEADERS_FILE}" \
    -o "${SMOKE_BODY_FILE}" \
    -w '%{http_code}' \
    "$@")"
  local curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 || "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    echo "${context}: HTTP ${status:-unknown} (curl exit ${curl_exit})" >&2
    cat "${SMOKE_HEADERS_FILE}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi
}

control_plane_json() {
  local context="$1"
  shift
  control_plane_mutate "${context}" "$@"
  cat "${SMOKE_BODY_FILE}"
}

wait_convergence() {
  local api_id="$1"
  wait_until "convergence CONVERGED for API ${api_id}" 45 2 \
    curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence" \
      | grep -q '"derivedState"[[:space:]]*:[[:space:]]*"CONVERGED"'
}

assert_pre_failover_setup() {
  local api_id="$1"
  local route_id="$2"
  local retry_policy_id="$3"
  local retry_json snapshot_json

  retry_json="$(fetch_retry_status)"
  assert_nonblank_gateway_id "${retry_json}"

  snapshot_json="$(curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1")"
  assert_published_retry_policy "${snapshot_json}"

  status="$(gateway_get "phase6-prime-budget")"
  if [[ "${status}" != "200" ]]; then
    echo "Setup request failed with HTTP ${status}" >&2
    print_retry_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${api_id}"
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
  curl --fail --silent "${GATEWAY_A_URL}/internal/v1/upstream-health" >"${SMOKE_HEALTH_FILE}"
  cat "${SMOKE_HEALTH_FILE}"
}

fetch_retry_status() {
  curl --fail --silent "${GATEWAY_A_URL}/internal/v1/retry-status" >"${SMOKE_RETRY_FILE}"
  cat "${SMOKE_RETRY_FILE}"
}

gateway_get() {
  local path_suffix="${1:-smoke}"
  local curl_exit=0
  local status=""
  set +e
  status="$(
    curl --silent --show-error \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_A_URL}/v1/orders/${path_suffix}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    echo "Gateway GET transport error for /v1/orders/${path_suffix} (curl exit ${curl_exit})" >&2
    exit 1
  fi
  printf '%s' "${status}"
}

gateway_post() {
  local path_suffix="$1"
  local idempotency_key="${2:-}"
  local curl_exit=0
  local status=""
  local headers=(-H 'Host: api.autoapi.local' -H 'Content-Type: application/json')
  if [[ -n "${idempotency_key}" ]]; then
    headers+=(-H "Idempotency-Key: ${idempotency_key}")
  fi
  set +e
  status="$(
    curl --silent --show-error \
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
    echo "Gateway POST transport error for /v1/orders/${path_suffix} (curl exit ${curl_exit})" >&2
    exit 1
  fi
  printf '%s' "${status}"
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-body.XXXXXX")"
  SMOKE_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-health.XXXXXX")"
  SMOKE_RETRY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-retry.XXXXXX")"
  SMOKE_SNAPSHOT_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase6-snapshot.XXXXXX")"
  trap cleanup EXIT

  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    docker compose down -v >/dev/null 2>&1 || true
    echo "== Starting Phase 6 stack =="
    docker compose up --build -d postgres redis upstream-v1 upstream-v2 control-plane
    wait_until "Control plane ready" 45 2 wait_http_ready "${CONTROL_PLANE_URL}"
  fi

  echo "== Creating project, API, pool, route =="
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

  echo "== Creating backend health policy =="
  health_json="$(control_plane_json "create backend health policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/backend-health-policies" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-passive-health\",\"consecutiveFailureThreshold\":${HEALTH_THRESHOLD},\"ejectionDurationSeconds\":5,\"maxEjectionPercent\":50,\"enabled\":true}")"
  health_policy_id="$(json_field "${health_json}" id)"

  control_plane_mutate "bind health policy to pool" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/backend-health-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"backendHealthPolicyId\":\"${health_policy_id}\"}"

  echo "== Creating retry policy =="
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

  echo "== Publishing and activating =="
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
    docker compose up --build -d gateway-a
  fi
  wait_until "Gateway A ready" 45 2 wait_http_ready "${GATEWAY_A_URL}"
  wait_convergence "${api_id}"

  echo "== Verifying normal requests succeed =="
  for i in $(seq 1 4); do
    status="$(gateway_get "phase6-normal-${i}")"
    if [[ "${status}" != "200" ]]; then
      echo "Normal request ${i} failed with HTTP ${status}" >&2
      exit 1
    fi
  done

  echo "== Verifying retry policy activation before failover =="
  assert_pre_failover_setup "${api_id}" "${route_id}" "${retry_policy_id}"

  echo "== Stopping upstream-v1 =="
  docker compose stop upstream-v1

  echo "== Driving GET retry failover =="
  retry_success=false
  for attempt in $(seq 1 "${RETRY_DRIVE_MAX}"); do
    status="$(gateway_get "phase6-retry-get-${attempt}")"
    if [[ "${status}" == "200" ]]; then
      service="$(service_from_body)"
      if [[ "${service}" == "upstream-v2" ]]; then
        health_json="$(fetch_upstream_health)"
        parsed="$(read_parsed_target_health "${health_json}" "${target_v1_id}")"
        IFS=$'\t' read -r v1_state v1_failures _ _ <<<"${parsed}"
        if [[ "${v1_failures:-0}" -ge 1 ]]; then
          retry_success=true
          echo "  GET retry succeeded on attempt ${attempt}; upstream-v2 served; v1 failures=${v1_failures}"
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
    print_retry_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${api_id}"
    exit 1
  fi

  echo "== POST without Idempotency-Key must not retry =="
  post_no_key_502=false
  for attempt in $(seq 1 10); do
    status="$(gateway_post "phase6-post-no-key-${attempt}" "")"
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

  echo "== POST with Idempotency-Key may retry =="
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

  echo "== Restarting upstream-v1 before budget exhaustion =="
  docker compose start upstream-v1
  wait_until "upstream-v1 running after restart" 15 2 \
    docker compose ps --status running upstream-v1 | grep -q upstream-v1

  echo "== Building retry budget capacity =="
  for i in $(seq 1 20); do
    status="$(gateway_get "phase6-budget-prime-${i}")"
    if [[ "${status}" != "200" ]]; then
      echo "Budget prime request ${i} failed with HTTP ${status}" >&2
      exit 1
    fi
  done

  echo "== Driving retries to exhaust budget =="
  docker compose stop upstream-v1
  for i in $(seq 1 "${BUDGET_EXHAUST_REQUESTS}"); do
    gateway_get "phase6-budget-${i}" >/dev/null || true
  done
  retry_status="$(fetch_retry_status)"
  assert_budget_exhausted "${retry_status}"

  echo "== Stopping remaining upstream to prove budget denial =="
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
    print_retry_diagnostics "${GATEWAY_A_URL}" "${CONTROL_PLANE_URL}" "${api_id}"
    exit 1
  fi

  curl --fail --silent "${GATEWAY_A_URL}/readyz" >/dev/null

  echo "== Restarting upstreams for recovery =="
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
    echo "Routing did not recover after upstream-v1 restart" >&2
    exit 1
  fi

  echo "Phase 6 retry smoke passed"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
