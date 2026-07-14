#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
HEALTH_THRESHOLD="${SMOKE_HEALTH_THRESHOLD:-2}"
POSITION_MAX="${SMOKE_POSITION_MAX:-4}"
BUDGET_INITIAL_CAPACITY=""
RETRY_DRIVE_MAX="${SMOKE_RETRY_DRIVE_MAX:-30}"
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

gateway_get_budget() {
  local path_suffix="${1:-smoke}"
  local curl_exit status
  set +e
  status="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_A_URL}/v1/budget-test/${path_suffix}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway GET /v1/budget-test/${path_suffix}" "${curl_exit}" "${status}"
    exit "${curl_exit}"
  fi
  printf '%s' "${status}"
}

gateway_get_budget_timed() {
  local path_suffix="${1:-smoke}"
  local curl_exit metrics
  set +e
  metrics="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}|%{time_total}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_A_URL}/v1/budget-test/${path_suffix}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway GET /v1/budget-test/${path_suffix}" "${curl_exit}" "${metrics%%|*}"
    exit "${curl_exit}"
  fi
  printf '%s' "${metrics}"
}

assert_budget_capacity_unchanged() {
  local retry_json="$1"
  local api_id="$2"
  local route_id="$3"
  local policy_id="$4"
  local expected_capacity="$5"
  python3 - "${retry_json}" "${api_id}" "${route_id}" "${policy_id}" "${expected_capacity}" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
api_id, route_id, policy_id, expected_capacity = sys.argv[2:6]
expected_capacity = int(expected_capacity)
for entry in payload.get("budgets") or []:
    if (
        entry.get("apiId") == api_id
        and entry.get("routeId") == route_id
        and entry.get("policyId") == policy_id
    ):
        capacity = int(entry.get("retryCapacity") or 0)
        if capacity == expected_capacity:
            raise SystemExit(0)
        raise SystemExit(
            f"retry budget capacity changed unexpectedly: expected {expected_capacity}, got {capacity}"
        )
print(json.dumps(payload, indent=2), file=sys.stderr)
raise SystemExit(f"retry budget entry not found for route={route_id} policy={policy_id}")
PY
}

position_selector_for_v1_first() {
  local attempt service
  for attempt in $(seq 1 "${POSITION_MAX}"); do
    gateway_get "phase6-position-${attempt}" >/dev/null
    service="$(service_from_body)"
    log_step "Positioning request ${attempt} completed on ${service}"
    if [[ "${service}" == "upstream-v2" ]]; then
      log_step "Round-robin positioned: next selection should target upstream-v1 first"
      return 0
    fi
  done
  echo "Could not position round-robin for upstream-v1-first within ${POSITION_MAX} attempts" >&2
  exit 1
}

position_budget_for_v1_first_stopped() {
  local api_id="$1"
  local route_id="$2"
  local policy_id="$3"
  local attempt retry_before retry_after metrics status service elapsed_sec elapsed_ms

  for attempt in $(seq 1 "${POSITION_MAX}"); do
    retry_before="$(fetch_retry_status)"
    metrics="$(gateway_get_budget_timed "phase6-budget-position-stopped-${attempt}")"
    IFS='|' read -r status elapsed_sec <<<"${metrics}"
    service="$(service_from_body)"
    elapsed_ms="$(python3 - "${elapsed_sec}" <<'PY'
import sys
print(int(float(sys.argv[1]) * 1000))
PY
)"
    retry_after="$(fetch_retry_status)"

    if [[ "${status}" == "200" && "${service}" == "upstream-v2" ]]; then
      assert_retry_budget_field_deltas \
        "${retry_before}" "${retry_after}" \
        "${api_id}" "${route_id}" "${policy_id}" \
        -1 0 0 0 0
      log_step "Budget positioned for upstream-v1-first while v1 stopped (attempt ${attempt}, elapsedMs=${elapsed_ms})"
      return 0
    fi
    log_step "Budget positioning attempt ${attempt}: HTTP ${status} service=${service} (not direct v2)"
  done

  echo "Could not position budget round-robin for upstream-v1-first while v1 stopped within ${POSITION_MAX} attempts" >&2
  exit 1
}

run_budget_allowed_retry() {
  local api_id="$1"
  local route_id="$2"
  local policy_id="$3"
  local label="$4"
  local path_suffix="$5"
  local expected_capacity="$6"
  local retry_before retry_after metrics status service elapsed_sec elapsed_ms

  retry_before="$(fetch_retry_status)"
  assert_budget_capacity_unchanged \
    "${retry_before}" "${api_id}" "${route_id}" "${policy_id}" "${expected_capacity}"

  metrics="$(gateway_get_budget_timed "${path_suffix}")"
  IFS='|' read -r status elapsed_sec <<<"${metrics}"
  service="$(service_from_body)"
  elapsed_ms="$(python3 - "${elapsed_sec}" <<'PY'
import sys
print(int(float(sys.argv[1]) * 1000))
PY
)"
  retry_after="$(fetch_retry_status)"

  log_budget_retry_diagnostics \
    "${label}" "${status}" "${service}" "${elapsed_ms}" \
    "${retry_before}" "${retry_after}" \
    "${api_id}" "${route_id}" "${policy_id}"

  if [[ "${status}" != "200" ]]; then
    echo "Expected budget retry ${label} HTTP 200, got ${status}" >&2
    exit 1
  fi
  if [[ "${service}" != "upstream-v2" ]]; then
    echo "Expected budget retry ${label} terminal service upstream-v2, got ${service}" >&2
    exit 1
  fi
  assert_retry_budget_allowed_retry_deltas \
    "${retry_before}" "${retry_after}" \
    "${api_id}" "${route_id}" "${policy_id}"
  assert_budget_capacity_unchanged \
    "${retry_after}" "${api_id}" "${route_id}" "${policy_id}" "${expected_capacity}"
  log_step "budget test: retry ${label} allowed"
}

run_budget_denied_retry() {
  local api_id="$1"
  local route_id="$2"
  local policy_id="$3"
  local label="$4"
  local path_suffix="$5"
  local expected_capacity="$6"
  local expected_used="$7"
  local retry_before retry_after metrics status service elapsed_sec elapsed_ms

  retry_before="$(fetch_retry_status)"
  assert_budget_capacity_unchanged \
    "${retry_before}" "${api_id}" "${route_id}" "${policy_id}" "${expected_capacity}"
  assert_retry_budget_capacity \
    "${retry_before}" "${api_id}" "${route_id}" "${policy_id}" \
    "${expected_capacity}" "${expected_used}"

  metrics="$(gateway_get_budget_timed "${path_suffix}")"
  IFS='|' read -r status elapsed_sec <<<"${metrics}"
  service="$(service_from_body)"
  elapsed_ms="$(python3 - "${elapsed_sec}" <<'PY'
import sys
print(int(float(sys.argv[1]) * 1000))
PY
)"
  retry_after="$(fetch_retry_status)"

  log_budget_retry_diagnostics \
    "denied ${label}" "${status}" "${service}" "${elapsed_ms}" \
    "${retry_before}" "${retry_after}" \
    "${api_id}" "${route_id}" "${policy_id}"

  if [[ "${status}" != "502" && "${status}" != "504" ]]; then
    echo "Expected budget retry ${label} terminal 502/504, got ${status}" >&2
    exit 1
  fi
  assert_retry_budget_denied_retry_deltas \
    "${retry_before}" "${retry_after}" \
    "${api_id}" "${route_id}" "${policy_id}"
  assert_retry_budget_capacity \
    "${retry_after}" "${api_id}" "${route_id}" "${policy_id}" \
    "${expected_capacity}" "${expected_used}"
  assert_budget_capacity_unchanged \
    "${retry_after}" "${api_id}" "${route_id}" "${policy_id}" "${expected_capacity}"
  log_step "budget test: retry ${label} denied -> terminal ${status}"
}

prime_budget_capacity_and_position_v1_first() {
  local api_id="$1"
  local budget_route_id="$2"
  local budget_policy_id="$3"

  initialize_budget_entry "${api_id}" "${budget_route_id}" "${budget_policy_id}"
  position_budget_selector_for_v1_first \
    "${api_id}" "${budget_route_id}" "${budget_policy_id}"
}

initialize_budget_entry() {
  local api_id="$1"
  local budget_route_id="$2"
  local budget_policy_id="$3"
  local retry_before retry_after status service health_json
  local before_originals before_used before_attempts before_successes before_capacity
  local after_originals after_used after_attempts after_successes after_capacity

  set_smoke_step "Initializing dedicated budget"
  retry_before="$(fetch_retry_status)"
  mapfile -t _budget_before < <(
    read_retry_budget_counters_or_zero \
      "${retry_before}" "${api_id}" "${budget_route_id}" "${budget_policy_id}"
  )
  before_originals="${_budget_before[0]}"
  before_used="${_budget_before[1]}"
  before_capacity="${_budget_before[2]}"
  before_attempts="${_budget_before[3]}"
  before_successes="${_budget_before[4]}"

  status="$(gateway_get_budget "phase6-budget-init")"
  service="$(service_from_body)"
  retry_after="$(fetch_retry_status)"

  if [[ "${status}" != "200" ]]; then
    health_json="$(fetch_upstream_health 2>/dev/null || true)"
    print_budget_setup_failure_diagnostics \
      "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
      "${retry_before}" "${retry_after}" "${status}" "${service}" "${health_json}"
    echo "Budget initialization request failed with HTTP ${status}" >&2
    exit 1
  fi

  set +e
  assert_budget_direct_request_no_retry \
    "${retry_before}" "${retry_after}" \
    "${api_id}" "${budget_route_id}" "${budget_policy_id}"
  local prime_status=$?
  set -e
  if [[ ${prime_status} -ne 0 ]]; then
    health_json="$(fetch_upstream_health 2>/dev/null || true)"
    print_budget_setup_failure_diagnostics \
      "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
      "${retry_before}" "${retry_after}" "${status}" "${service}" "${health_json}"
    echo "Budget prime unexpectedly retried; upstream readiness or selector setup is invalid" >&2
    exit 1
  fi

  mapfile -t _budget_after < <(
    read_retry_budget_counters \
      "${retry_after}" "${api_id}" "${budget_route_id}" "${budget_policy_id}"
  )
  after_originals="${_budget_after[0]}"
  after_used="${_budget_after[1]}"
  after_capacity="${_budget_after[2]}"
  after_attempts="${_budget_after[3]}"
  after_successes="${_budget_after[4]}"

  if [[ "${after_capacity}" -lt 1 ]]; then
    echo "Unexpected budget capacity ${after_capacity}; dedicated policy floor must be positive" >&2
    exit 1
  fi
  if [[ "${after_capacity}" -ge 120 ]]; then
    echo "Unexpected budget capacity ${after_capacity}; matched main-route budget instead of dedicated policy" >&2
    exit 1
  fi

  BUDGET_INITIAL_CAPACITY="${after_capacity}"
  log_step "budget initialization:"
  log_step "  HTTP ${status}"
  log_step "  service=${service}"
  log_step "  originalRequests ${before_originals} -> ${after_originals}"
  log_step "  retriesUsed ${before_used} -> ${after_used}"
  log_step "  retryAttempts ${before_attempts} -> ${after_attempts}"
  log_step "  retrySuccesses ${before_successes} -> ${after_successes}"
  log_retry_status_entry \
    "budget test capacity initialized" \
    "${retry_after}" "${api_id}" "${budget_route_id}" "${budget_policy_id}"
  log_step "budget test: capacity initialized to ${BUDGET_INITIAL_CAPACITY}"
}

position_budget_selector_for_v1_first() {
  local api_id="$1"
  local budget_route_id="$2"
  local budget_policy_id="$3"
  local attempt retry_before retry_after status service metrics health_json

  set_smoke_step "Positioning dedicated budget selector"
  for attempt in $(seq 1 "${POSITION_MAX}"); do
    retry_before="$(fetch_retry_status)"
    metrics="$(gateway_get_budget_timed "phase6-budget-position-${attempt}")"
    IFS='|' read -r status _elapsed <<<"${metrics}"
    service="$(service_from_body)"
    retry_after="$(fetch_retry_status)"

    if [[ "${status}" != "200" ]]; then
      health_json="$(fetch_upstream_health 2>/dev/null || true)"
      print_budget_setup_failure_diagnostics \
        "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
        "${retry_before}" "${retry_after}" "${status}" "${service}" "${health_json}"
      echo "Budget positioning request ${attempt} failed with HTTP ${status}" >&2
      exit 1
    fi

    set +e
    assert_budget_direct_request_no_retry \
      "${retry_before}" "${retry_after}" \
      "${api_id}" "${budget_route_id}" "${budget_policy_id}"
    local position_status=$?
    set -e
    if [[ ${position_status} -ne 0 ]]; then
      health_json="$(fetch_upstream_health 2>/dev/null || true)"
      print_budget_setup_failure_diagnostics \
        "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
        "${retry_before}" "${retry_after}" "${status}" "${service}" "${health_json}"
      echo "Budget positioning request ${attempt} unexpectedly retried" >&2
      exit 1
    fi

    log_step "Budget positioning request ${attempt} completed on ${service}"
    if [[ "${service}" == "upstream-v2" ]]; then
      log_step "Budget round-robin positioned: next selection should target upstream-v1 first"
      return 0
    fi
  done

  echo "Could not position budget round-robin for upstream-v1-first within ${POSITION_MAX} attempts" >&2
  exit 1
}

demonstrate_budget_exhaustion() {
  local api_id="$1"
  local budget_route_id="$2"
  local budget_policy_id="$3"
  local i retry_json
  local budget_test_start budget_test_end budget_test_duration_sec

  budget_test_start="$(date +%s)"

  set_smoke_step "Restarting upstream-v1 for budget initialization"
  docker compose start upstream-v1 upstream-v2

  set_smoke_step "Waiting for upstream-v1 health"
  if ! wait_compose_service_healthy "upstream-v1" "upstream-v1 healthy after restart" 30 1; then
    exit 1
  fi
  if ! wait_compose_service_healthy "upstream-v2" "upstream-v2 healthy for budget initialization" 30 1; then
    exit 1
  fi
  log_step "upstream-v1 restarted and healthy"

  prime_budget_capacity_and_position_v1_first \
    "${api_id}" "${budget_route_id}" "${budget_policy_id}"

  if [[ -z "${BUDGET_INITIAL_CAPACITY}" ]]; then
    echo "Budget initial capacity was not established" >&2
    exit 1
  fi

  set_smoke_step "Stopping upstream-v1 for budget exhaustion"
  docker compose stop upstream-v1

  for i in $(seq 1 "${BUDGET_INITIAL_CAPACITY}"); do
    set_smoke_step "Executing allowed budget retry ${i}"
    if [[ "${i}" -gt 1 ]]; then
      position_budget_for_v1_first_stopped \
        "${api_id}" "${budget_route_id}" "${budget_policy_id}"
    fi
    run_budget_allowed_retry \
      "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
      "${i}" "phase6-budget-retry-${i}" \
      "${BUDGET_INITIAL_CAPACITY}"
    retry_json="$(fetch_retry_status)"
    assert_retry_budget_capacity \
      "${retry_json}" "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
      "${BUDGET_INITIAL_CAPACITY}" "${i}"
    log_step "budget test: retry ${i} allowed -> used ${i}"
  done

  set_smoke_step "Positioning dedicated budget selector before denied retry"
  position_budget_for_v1_first_stopped \
    "${api_id}" "${budget_route_id}" "${budget_policy_id}"

  set_smoke_step "Executing denied budget retry"
  run_budget_denied_retry \
    "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
    "$((BUDGET_INITIAL_CAPACITY + 1))" "phase6-budget-retry-denied" \
    "${BUDGET_INITIAL_CAPACITY}" "${BUDGET_INITIAL_CAPACITY}"

  retry_json="$(fetch_retry_status)"
  assert_retry_budget_capacity \
    "${retry_json}" "${api_id}" "${budget_route_id}" "${budget_policy_id}" \
    "${BUDGET_INITIAL_CAPACITY}" "${BUDGET_INITIAL_CAPACITY}"
  log_step "budget test: used remains ${BUDGET_INITIAL_CAPACITY}"

  budget_test_end="$(date +%s)"
  budget_test_duration_sec="$((budget_test_end - budget_test_start))"
  log_step "budget exhaustion test durationSec=${budget_test_duration_sec}"

  log_step "retry-budget exhaustion demonstrated"
}

demonstrate_get_retry_failover() {
  local api_id="$1"
  local route_id="$2"
  local policy_id="$3"
  local run_id="$4"
  local proof_path="phase6-failover-proof-${run_id}"
  local retry_before retry_after status service metrics duration_sec duration_ms curl_exit

  retry_before="$(fetch_retry_status)"
  assert_retry_budget_entry "${retry_before}" "${api_id}" "${route_id}" "${policy_id}"
  log_retry_status_entry "retry status before" "${retry_before}" "${api_id}" "${route_id}" "${policy_id}"

  set_smoke_step "Sending failover proof GET"
  set +e
  metrics="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}|%{time_total}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_A_URL}/v1/orders/${proof_path}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "failover GET /v1/orders/${proof_path}" "${curl_exit}" "${metrics%%|*}"
    exit "${curl_exit}"
  fi
  IFS='|' read -r status duration_sec <<<"${metrics}"
  service="$(service_from_body)"
  duration_ms="$(python3 - "${duration_sec}" <<'PY'
import sys
print(int(float(sys.argv[1]) * 1000))
PY
)"

  log_step "failover proof request: HTTP ${status} service=${service} elapsedMs=${duration_ms}"

  retry_after="$(fetch_retry_status)"
  log_retry_status_entry "retry status after" "${retry_after}" "${api_id}" "${route_id}" "${policy_id}"

  if [[ "${status}" != "200" ]]; then
    print_failover_proof_diagnostics \
      "${api_id}" "${route_id}" "${policy_id}" \
      "${retry_before}" "${retry_after}" \
      "${status}" "${service}" "${duration_ms}" \
      "${proof_path}" "gateway-a"
    echo "Expected failover proof HTTP 200, got ${status}" >&2
    exit 1
  fi
  if [[ "${service}" != "upstream-v2" ]]; then
    print_failover_proof_diagnostics \
      "${api_id}" "${route_id}" "${policy_id}" \
      "${retry_before}" "${retry_after}" \
      "${status}" "${service}" "${duration_ms}" \
      "${proof_path}" "gateway-a"
    echo "Expected terminal service upstream-v2, got ${service}" >&2
    exit 1
  fi

  if ! assert_retry_failover_budget_proof \
    "${retry_before}" "${retry_after}" \
    "${api_id}" "${route_id}" "${policy_id}"; then
    print_failover_proof_diagnostics \
      "${api_id}" "${route_id}" "${policy_id}" \
      "${retry_before}" "${retry_after}" \
      "${status}" "${service}" "${duration_ms}" \
      "${proof_path}" "gateway-a"
    echo "Retry budget counter deltas did not prove failover" >&2
    exit 1
  fi

  if [[ "${duration_ms}" -ge 750 ]]; then
    log_step "Failover duration ${duration_ms}ms supports retry-after-timeout pattern"
  fi

  log_step "GET retry failover demonstrated"
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
      "budgetWindowSeconds": 60,
      "enabled": true
    }')"
  retry_policy_id="$(json_field "${retry_json}" id)"

  control_plane_mutate "bind retry policy to route" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${route_id}/retry-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"retryPolicyId\":\"${retry_policy_id}\"}"

  set_smoke_step "Creating budget-test pool, route, and exhaustion policy"
  budget_pool_json="$(control_plane_json "create budget-test pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"budget-test-pool","loadBalancing":"ROUND_ROBIN"}')"
  budget_pool_id="$(json_field "${budget_pool_json}" id)"

  control_plane_mutate "create budget-test upstream-v1 target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${budget_pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}'

  control_plane_mutate "create budget-test upstream-v2 target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${budget_pool_id}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v2:8080","enabled":true,"weight":1}'

  budget_route_json="$(control_plane_json "create budget-test route" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"budget-test-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/budget-test\",\"methods\":[\"GET\"],\"upstreamPoolId\":\"${budget_pool_id}\",\"enabled\":true}")"
  budget_route_id="$(json_field "${budget_route_json}" id)"

  budget_retry_json="$(control_plane_json "create budget-exhaustion retry policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/retry-policies" \
    -H 'Content-Type: application/json' \
    -d '{
      "name": "phase6-budget-exhaustion",
      "maxAttempts": 2,
      "perAttemptTimeoutMs": 100,
      "retryOnConnectFailure": true,
      "retryOnConnectionReset": true,
      "retryOnDnsFailure": true,
      "retryOnResponseTimeout": true,
      "retryableMethods": ["GET"],
      "requireIdempotencyKeyForUnsafeMethods": true,
      "budgetPercent": 0,
      "budgetMinRetriesPerSecond": 1,
      "budgetWindowSeconds": 5,
      "enabled": true
    }')"
  budget_policy_id="$(json_field "${budget_retry_json}" id)"

  control_plane_mutate "bind budget-exhaustion retry policy" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${budget_route_id}/retry-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"retryPolicyId\":\"${budget_policy_id}\"}"

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

  set_smoke_step "Positioning round-robin selector"
  position_selector_for_v1_first

  set_smoke_step "Stopping upstream-v1"
  docker compose stop upstream-v1

  demonstrate_get_retry_failover "${api_id}" "${route_id}" "${retry_policy_id}" "$(date +%s)"

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

  set_smoke_step "Testing retry-budget exhaustion"
  demonstrate_budget_exhaustion "${api_id}" "${budget_route_id}" "${budget_policy_id}"

  smoke_curl --fail "${GATEWAY_A_URL}/readyz" >/dev/null

  set_smoke_step "Restarting upstreams for recovery"
  docker compose start upstream-v1 upstream-v2
  wait_compose_service_healthy "upstream-v1" "upstream-v1 healthy after recovery restart" 30 1
  wait_compose_service_healthy "upstream-v2" "upstream-v2 healthy after recovery restart" 30 1
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
