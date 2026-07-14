#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CANDIDATE_IMAGE="${CANDIDATE_IMAGE:-autoapi-server:ci}"
MOCK_UPSTREAM_IMAGE="${MOCK_UPSTREAM_IMAGE:-mock-upstream:ci}"
SMOKE_NETWORK="${SMOKE_NETWORK:-autoapi-smoke}"
GATEWAY_PORT="${GATEWAY_PORT:-18081}"
CONTROL_PLANE_PORT="${CONTROL_PLANE_PORT:-18080}"
GATEWAY_ID="${GATEWAY_ID:-container-smoke-gateway}"
PEPPER="${AUTOAPI_API_KEY_PEPPER:-development-only-change-me-not-for-production-use}"
RETRY_DRIVE_MAX="${SMOKE_RETRY_DRIVE_MAX:-25}"
HEALTH_THRESHOLD="${SMOKE_HEALTH_THRESHOLD:-2}"
POSITION_MAX="${SMOKE_POSITION_MAX:-4}"

CONTROL_PLANE_URL="http://localhost:${CONTROL_PLANE_PORT}"
GATEWAY_URL="http://localhost:${GATEWAY_PORT}"

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_HEALTH_FILE=""
SMOKE_RETRY_FILE=""
SMOKE_SNAPSHOT_FILE=""
CONTAINER_API_ID=""

# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"
# shellcheck source=scripts/smoke-retry-lib.sh
source "${ROOT}/scripts/smoke-retry-lib.sh"

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_HEALTH_FILE}" "${SMOKE_RETRY_FILE}" "${SMOKE_SNAPSHOT_FILE}"
  set_smoke_step "Starting cleanup"
  timeout 120 docker rm -f autoapi-gateway autoapi-control-plane upstream-v1 upstream-v2 autoapi-postgres autoapi-redis >/dev/null 2>&1 || true
  timeout 30 docker volume rm autoapi-postgres-smoke autoapi-redis-smoke >/dev/null 2>&1 || true
  timeout 30 docker network rm "${SMOKE_NETWORK}" >/dev/null 2>&1 || true
  log_step "Cleanup completed"
}

dump_diagnostics() {
  local exit_code="$?"

  if [[ "${exit_code}" -ne 0 ]]; then
    log_step "Container smoke failed with exit code ${exit_code} at step: ${SMOKE_CURRENT_STEP}"
    dump_container_smoke_diagnostics "${GATEWAY_URL}" "${CONTROL_PLANE_URL}" "${CONTAINER_API_ID}"
    collect_gateway_thread_dump autoapi-gateway
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
  wait_until "convergence CONVERGED for API ${api_id}" 30 2 convergence_converged "${api_id}"
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
      "${GATEWAY_URL}/v1/orders/${path_suffix}"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "gateway GET /v1/orders/${path_suffix}" "${curl_exit}" "${status}"
    exit "${curl_exit}"
  fi
  printf '%s' "${status}"
}

bootstrap_phase6_config() {
  local project_json api_json pool_json target_v1_json route_json health_json retry_json

  set_smoke_step "Creating project, API, pool, route"
  project_json="$(control_plane_json "create project" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
    -H 'Content-Type: application/json' \
    -d '{"name":"container-smoke-retry","description":"Server Container Phase 6 candidate smoke"}')"
  local project_id
  project_id="$(json_field "${project_json}" id)"

  api_json="$(control_plane_json "create API" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/projects/${project_id}/apis" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-api","host":"api.autoapi.local","basePath":"/"}')"
  API_ID="$(json_field "${api_json}" id)"
  CONTAINER_API_ID="${API_ID}"

  pool_json="$(control_plane_json "create upstream pool" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/upstream-pools" \
    -H 'Content-Type: application/json' \
    -d '{"name":"orders-pool","loadBalancing":"ROUND_ROBIN"}')"
  POOL_ID="$(json_field "${pool_json}" id)"

  target_v1_json="$(control_plane_json "create upstream-v1 target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${POOL_ID}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v1:8080","enabled":true,"weight":1}')"
  TARGET_V1_ID="$(json_field "${target_v1_json}" id)"

  control_plane_mutate "create upstream-v2 target" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${POOL_ID}/targets" \
    -H 'Content-Type: application/json' \
    -d '{"url":"http://upstream-v2:8080","enabled":true,"weight":1}'

  route_json="$(control_plane_json "create route" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/routes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\"${POOL_ID}\",\"enabled\":true}")"
  ROUTE_ID="$(json_field "${route_json}" id)"

  set_smoke_step "Creating backend health policy"
  health_json="$(control_plane_json "create backend health policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/backend-health-policies" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"container-passive-health\",\"consecutiveFailureThreshold\":${HEALTH_THRESHOLD},\"ejectionDurationSeconds\":5,\"maxEjectionPercent\":50,\"enabled\":true}")"
  local health_policy_id
  health_policy_id="$(json_field "${health_json}" id)"

  control_plane_mutate "bind health policy" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${POOL_ID}/backend-health-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"backendHealthPolicyId\":\"${health_policy_id}\"}"

  set_smoke_step "Creating retry policy"
  retry_json="$(control_plane_json "create retry policy" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/retry-policies" \
    -H 'Content-Type: application/json' \
    -d '{
      "name": "container-smoke-retry",
      "maxAttempts": 2,
      "perAttemptTimeoutMs": 1000,
      "retryOnConnectFailure": true,
      "retryOnConnectionReset": true,
      "retryOnDnsFailure": true,
      "retryOnResponseTimeout": true,
      "retryableMethods": ["GET"],
      "requireIdempotencyKeyForUnsafeMethods": true,
      "budgetPercent": 100,
      "budgetMinRetriesPerSecond": 10,
      "budgetWindowSeconds": 10,
      "enabled": true
    }')"
  RETRY_POLICY_ID="$(json_field "${retry_json}" id)"

  control_plane_mutate "bind retry policy" \
    -X PUT "${CONTROL_PLANE_URL}/api/v1/routes/${ROUTE_ID}/retry-policy" \
    -H 'Content-Type: application/json' \
    -d "{\"retryPolicyId\":\"${RETRY_POLICY_ID}\"}"

  set_smoke_step "Publishing and activating configuration"
  control_plane_mutate "validate configuration" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/validate"
  control_plane_mutate "publish configuration version 1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions" \
    -H 'Content-Type: application/json' \
    -d '{"message":"Container smoke Phase 6"}'
  control_plane_mutate "activate configuration version 1" \
    -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions/1/activate" \
    -H 'Content-Type: application/json' \
    -d '{"expectedDesiredVersion":null}'

  smoke_curl --fail "${CONTROL_PLANE_URL}/api/v1/apis/${API_ID}/config/versions/1" >"${SMOKE_SNAPSHOT_FILE}"
  assert_published_retry_policy "$(cat "${SMOKE_SNAPSHOT_FILE}")"
}

assert_pre_failover_setup() {
  local retry_json status
  retry_json="$(smoke_curl --fail "${GATEWAY_URL}/internal/v1/retry-status")"
  assert_nonblank_gateway_id "${retry_json}"

  set_smoke_step "Priming retry budget with setup GET"
  status="$(gateway_get "container-prime-budget")"
  log_step "Setup GET completed with HTTP ${status}"
  if [[ "${status}" != "200" ]]; then
    echo "Setup request failed with HTTP ${status}" >&2
    exit 1
  fi

  retry_json="$(smoke_curl --fail "${GATEWAY_URL}/internal/v1/retry-status")"
  assert_nonblank_gateway_id "${retry_json}"
  assert_retry_budget_entry "${retry_json}" "${API_ID}" "${ROUTE_ID}" "${RETRY_POLICY_ID}"
}

position_selector_for_v1_first() {
  local attempt service
  for attempt in $(seq 1 "${POSITION_MAX}"); do
    gateway_get "container-position-${attempt}" >/dev/null
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

assert_failed_target_health_recorded() {
  local health_json="$1"
  local target_id="$2"
  local parsed state failures ejected_until category

  parsed="$(read_parsed_target_health "${health_json}" "${target_id}")"
  IFS='|' read -r state failures ejected_until category <<<"${parsed}"
  if [[ "${state}" == "EJECTED" ]]; then
    echo "upstream-v1 became EJECTED before retry proof was captured" >&2
    echo "${health_json}" >&2
    exit 1
  fi
  if is_qualifying_stopped_upstream_transport_category "${category}"; then
    return 0
  fi
  if [[ "${failures:-0}" -ge 1 ]]; then
    return 0
  fi
  echo "Expected qualifying transport failure recorded for stopped upstream-v1" >&2
  echo "  state=${state} consecutiveFailures=${failures} lastFailureCategory=${category:-<empty>}" >&2
  exit 1
}

drive_retry_failover() {
  local retry_before retry_after status service health_json duration_sec duration_ms metrics

  set_smoke_step "Capturing retry status before failover request"
  retry_before="$(smoke_curl --fail "${GATEWAY_URL}/internal/v1/retry-status")"
  assert_retry_budget_entry "${retry_before}" "${API_ID}" "${ROUTE_ID}" "${RETRY_POLICY_ID}"
  log_step "Retry status before captured"

  set_smoke_step "Sending deterministic failover GET"
  set +e
  metrics="$(
    smoke_curl \
      -D "${SMOKE_HEADERS_FILE}" \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}|%{time_total}' \
      -H 'Host: api.autoapi.local' \
      "${GATEWAY_URL}/v1/orders/container-retry-failover"
  )"
  local curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "failover GET /v1/orders/container-retry-failover" "${curl_exit}" "${metrics%%|*}"
    exit "${curl_exit}"
  fi
  IFS='|' read -r status duration_sec <<<"${metrics}"
  log_step "Failover GET completed with HTTP ${status} duration=${duration_sec}s"

  if [[ "${status}" != "200" ]]; then
    echo "Expected failover GET HTTP 200, got ${status}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi

  service="$(service_from_body)"
  if [[ "${service}" != "upstream-v2" ]]; then
    echo "Expected terminal service upstream-v2, got ${service}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi

  retry_after="$(smoke_curl --fail "${GATEWAY_URL}/internal/v1/retry-status")"
  assert_retry_counter_deltas \
    "${retry_before}" \
    "${retry_after}" \
    "${API_ID}" \
    "${ROUTE_ID}" \
    "${RETRY_POLICY_ID}" \
    1 1 1 1
  log_step "Retry counter deltas verified (originalRequests+1 retriesUsed+1 retryAttempts+1 retrySuccesses+1)"

  health_json="$(smoke_curl --fail "${GATEWAY_URL}/internal/v1/upstream-health")"
  assert_failed_target_health_recorded "${health_json}" "${TARGET_V1_ID}"

  duration_ms="$(python3 - "${duration_sec}" <<'PY'
import sys
print(int(float(sys.argv[1]) * 1000))
PY
)"
  if [[ "${duration_ms}" -lt 750 ]]; then
    log_step "Failover request duration ${duration_ms}ms (<750ms); counter proof is authoritative"
  else
    log_step "Failover request duration ${duration_ms}ms supports retry-after-timeout pattern"
  fi
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/container-smoke-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/container-smoke-body.XXXXXX")"
  SMOKE_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/container-smoke-health.XXXXXX")"
  SMOKE_RETRY_FILE="$(mktemp "${TMPDIR:-/tmp}/container-smoke-retry.XXXXXX")"
  SMOKE_SNAPSHOT_FILE="$(mktemp "${TMPDIR:-/tmp}/container-smoke-snapshot.XXXXXX")"
  trap dump_diagnostics EXIT

  cleanup

  if ! docker image inspect "${CANDIDATE_IMAGE}" >/dev/null 2>&1; then
    echo "Candidate image ${CANDIDATE_IMAGE} not found locally" >&2
    exit 1
  fi
  if ! docker image inspect "${MOCK_UPSTREAM_IMAGE}" >/dev/null 2>&1; then
    docker build -t "${MOCK_UPSTREAM_IMAGE}" tests/mock-upstream
  fi

  set_smoke_step "Starting control-plane stack"
  docker network create "${SMOKE_NETWORK}"

  docker run -d --name autoapi-postgres --network "${SMOKE_NETWORK}" \
    -e POSTGRES_DB=autoapi \
    -e POSTGRES_USER=autoapi \
    -e POSTGRES_PASSWORD=autoapi \
    -v autoapi-postgres-smoke:/var/lib/postgresql/data \
    --health-cmd="pg_isready -U autoapi -d autoapi" \
    --health-interval=5s \
    --health-timeout=3s \
    --health-retries=20 \
    postgres:16-alpine

  docker run -d --name autoapi-redis --network "${SMOKE_NETWORK}" \
    -v autoapi-redis-smoke:/data \
    --health-cmd="redis-cli ping" \
    --health-interval=5s \
    --health-timeout=3s \
    --health-retries=10 \
    redis:7-alpine

  wait_until "PostgreSQL healthy" 30 2 wait_container_healthy autoapi-postgres

  docker run -d --name upstream-v1 --network "${SMOKE_NETWORK}" \
    -e UPSTREAM_ID=upstream-v1 "${MOCK_UPSTREAM_IMAGE}"
  docker run -d --name upstream-v2 --network "${SMOKE_NETWORK}" \
    -e UPSTREAM_ID=upstream-v2 "${MOCK_UPSTREAM_IMAGE}"

  docker run -d --name autoapi-control-plane --network "${SMOKE_NETWORK}" -p "${CONTROL_PLANE_PORT}:8080" \
    -e AUTOAPI_ROLE=control-plane \
    -e AUTOAPI_CONTROLPLANE_ENABLED=true \
    -e AUTOAPI_API_KEY_PEPPER="${PEPPER}" \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://autoapi-postgres:5432/autoapi \
    -e SPRING_DATASOURCE_USERNAME=autoapi \
    -e SPRING_DATASOURCE_PASSWORD=autoapi \
    -e SPRING_R2DBC_URL=r2dbc:postgresql://autoapi-postgres:5432/autoapi?sslMode=disable \
    -e SPRING_R2DBC_USERNAME=autoapi \
    -e SPRING_R2DBC_PASSWORD=autoapi \
    "${CANDIDATE_IMAGE}"

  wait_until "control-plane ready" 30 2 wait_http_ready "${CONTROL_PLANE_URL}"
  log_step "Control plane ready"

  bootstrap_phase6_config

  docker run -d --name autoapi-gateway --network "${SMOKE_NETWORK}" -p "${GATEWAY_PORT}:8080" \
    -e AUTOAPI_ROLE=gateway \
    -e AUTOAPI_CONTROLPLANE_ENABLED=false \
    -e AUTOAPI_GATEWAY_CONFIG_SOURCE=control-plane \
    -e AUTOAPI_GATEWAY_ID="${GATEWAY_ID}" \
    -e AUTOAPI_GATEWAY_GROUP=default \
    -e AUTOAPI_CONTROL_PLANE_BASE_URL=http://autoapi-control-plane:8080 \
    -e AUTOAPI_GATEWAY_API_ID="${API_ID}" \
    -e AUTOAPI_GATEWAY_POLL_INTERVAL=2s \
    -e AUTOAPI_GATEWAY_HEARTBEAT_INTERVAL=3s \
    -e AUTOAPI_GATEWAY_INITIAL_LOAD_TIMEOUT=45s \
    -e AUTOAPI_API_KEY_PEPPER="${PEPPER}" \
    -e AUTOAPI_REDIS_URL=redis://autoapi-redis:6379 \
    "${CANDIDATE_IMAGE}"

  wait_until "gateway ready" 45 2 wait_http_ready "${GATEWAY_URL}"
  wait_convergence "${API_ID}"
  log_step "Gateway ready"

  set_smoke_step "Verifying baseline proxy"
  smoke_curl --fail -H 'Host: api.autoapi.local' "${GATEWAY_URL}/v1/orders/baseline" | grep -q path

  set_smoke_step "Verifying retry policy activation"
  assert_pre_failover_setup

  set_smoke_step "Positioning round-robin selector"
  position_selector_for_v1_first

  set_smoke_step "Stopping upstream-v1"
  docker stop upstream-v1

  drive_retry_failover
  log_step "Candidate retry smoke passed"

  set_smoke_step "Testing unsafe POST without idempotency key"
  local post_status curl_exit
  set +e
  post_status="$(
    smoke_curl \
      -o "${SMOKE_BODY_FILE}" \
      -w '%{http_code}' \
      -H 'Host: api.autoapi.local' \
      -H 'Content-Type: application/json' \
      -d '{"smoke":true}' \
      "${GATEWAY_URL}/v1/orders/container-post-no-key"
  )"
  curl_exit=$?
  set -e
  if [[ ${curl_exit} -ne 0 ]]; then
    report_curl_failure "POST without Idempotency-Key" "${curl_exit}" "${post_status}"
    exit "${curl_exit}"
  fi
  if [[ "${post_status}" != "502" && "${post_status}" != "504" ]]; then
    echo "Expected POST without Idempotency-Key to fail without retry, got HTTP ${post_status}" >&2
    exit 1
  fi

  smoke_curl --fail "${GATEWAY_URL}/internal/v1/upstream-health" | grep -q HEALTHY
  log_step "Container candidate Phase 6 smoke passed"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
