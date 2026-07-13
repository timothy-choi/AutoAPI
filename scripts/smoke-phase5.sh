#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://localhost:8081}"
GATEWAY_A_URL="${GATEWAY_A_URL:-http://localhost:8080}"
SMOKE_SKIP_UP="${SMOKE_SKIP_UP:-false}"
HEALTH_THRESHOLD="${SMOKE_HEALTH_THRESHOLD:-2}"
EJECTION_SECONDS="${SMOKE_EJECTION_SECONDS:-5}"
EJECTION_DRIVE_MAX_ATTEMPTS="${SMOKE_EJECTION_DRIVE_MAX_ATTEMPTS:-20}"
POST_EJECTION_REQUESTS="${SMOKE_POST_EJECTION_REQUESTS:-8}"

SMOKE_HEADERS_FILE=""
SMOKE_BODY_FILE=""
SMOKE_HEALTH_FILE=""

cleanup() {
  rm -f "${SMOKE_HEADERS_FILE}" "${SMOKE_BODY_FILE}" "${SMOKE_HEALTH_FILE}"
  if [[ "${SMOKE_SKIP_UP:-false}" != "true" ]]; then
    docker compose down -v >/dev/null 2>&1 || true
  fi
}

# shellcheck source=scripts/smoke-phase5-parser-lib.sh
source "${ROOT}/scripts/smoke-phase5-parser-lib.sh"
# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-compose-lib.sh
source "${ROOT}/scripts/smoke-compose-lib.sh"

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

fetch_upstream_health() {
  local curl_exit=0
  local status=""
  set +e
  status="$(
    curl --silent --show-error \
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

print_ejection_diagnostics() {
  local attempts="$1"
  local count_200="$2"
  local count_502="$3"
  local target_id="$4"
  local health_json="$5"
  echo "Ejection diagnostics:" >&2
  echo "  total attempts=${attempts} http_200=${count_200} http_502=${count_502}" >&2
  echo "  failed targetId=${target_id}" >&2
  echo "  threshold=${HEALTH_THRESHOLD}" >&2
  if [[ -n "${health_json}" ]]; then
    local parsed=""
    if ! parsed="$(read_parsed_target_health "${health_json}" "${target_id}")"; then
      return 1
    fi
    IFS=$'\t' read -r state failures ejected_until category <<<"${parsed}"
    echo "  target state=${state:-unknown} consecutiveFailures=${failures:-unknown}" >&2
    echo "  ejectedUntil=${ejected_until:-null} lastFailureCategory=${category:-null}" >&2
    echo "  internal health response:" >&2
    echo "${health_json}" >&2
  fi
  if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
    echo "  gateway-a logs (tail):" >&2
    docker compose logs gateway-a --tail 40 2>&1 >&2 || true
  fi
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
  local converged=false
  local response=""
  for _ in $(seq 1 45); do
    response="$(curl --fail --silent "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/convergence")"
    state="$(json_field "${response}" derivedState)"
    if [[ "${state}" == "CONVERGED" ]]; then
      converged=true
      break
    fi
    sleep 2
  done
  if [[ "${converged}" != "true" ]]; then
    echo "Convergence did not reach CONVERGED: ${response}" >&2
    exit 1
  fi
}

wait_container_healthy() {
  local service="$1"
  local label="$2"
  local healthy=false
  local cid=""
  for _ in $(seq 1 30); do
    cid="$(docker compose ps -q "${service}" 2>/dev/null || true)"
    if [[ -z "${cid}" ]]; then
      sleep 1
      continue
    fi
    local health_status
    health_status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${cid}" 2>/dev/null || echo none)"
    if [[ "${health_status}" == "healthy" || "${health_status}" == "none" ]]; then
      if [[ "$(docker inspect -f '{{.State.Running}}' "${cid}" 2>/dev/null || echo false)" == "true" ]]; then
        healthy=true
        break
      fi
    fi
    sleep 1
  done
  if [[ "${healthy}" != "true" ]]; then
    echo "${label} did not become healthy after restart" >&2
    docker compose logs "${service}" --tail 40 >&2 || true
    exit 1
  fi
}

# Capture HTTP status without aborting on 502. Never use curl --fail here.
gateway_request() {
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
    echo "Gateway request transport error for path /v1/orders/${path_suffix} (curl exit ${curl_exit})" >&2
    exit 1
  fi
  printf '%s' "${status}"
}

main() {
  SMOKE_HEADERS_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase5-headers.XXXXXX")"
  SMOKE_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase5-body.XXXXXX")"
  SMOKE_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/smoke-phase5-health.XXXXXX")"
  trap cleanup EXIT

if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  docker compose down -v >/dev/null 2>&1 || true
  echo "== Starting Phase 5 stack =="
  build_smoke_images_once
  start_smoke_base_stack
  wait_ready "${CONTROL_PLANE_URL}" "Control plane"
fi

echo "== Creating project, API, pool, route =="
project_json="$(control_plane_json "create project" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"phase5-health","description":"Phase 5 passive health smoke"}')"
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

control_plane_mutate "create route" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/routes" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\"${pool_id}\",\"enabled\":true}"

echo "== Creating backend health policy (threshold=${HEALTH_THRESHOLD}) =="
policy_json="$(control_plane_json "create backend health policy" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/backend-health-policies" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"orders-passive-health\",\"consecutiveFailureThreshold\":${HEALTH_THRESHOLD},\"ejectionDurationSeconds\":${EJECTION_SECONDS},\"maxEjectionPercent\":50,\"enabled\":true}")"
policy_id="$(json_field "${policy_json}" id)"

control_plane_mutate "bind health policy to pool" \
  -X PUT "${CONTROL_PLANE_URL}/api/v1/upstream-pools/${pool_id}/backend-health-policy" \
  -H 'Content-Type: application/json' \
  -d "{\"backendHealthPolicyId\":\"${policy_id}\"}"

echo "== Publishing and activating =="
control_plane_mutate "validate configuration" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/validate"
control_plane_mutate "publish configuration version 1" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Phase 5 passive health"}'
control_plane_mutate "activate configuration version 1" \
  -X POST "${CONTROL_PLANE_URL}/api/v1/apis/${api_id}/config/versions/1/activate" \
  -H 'Content-Type: application/json' \
  -d '{"expectedDesiredVersion":null}'

export AUTOAPI_GATEWAY_API_ID="${api_id}"
if [[ "${SMOKE_SKIP_UP}" != "true" ]]; then
  start_smoke_gateways gateway-a
fi
wait_ready "${GATEWAY_A_URL}" "Gateway A"
wait_convergence "${api_id}"

echo "== Verifying round-robin across both upstreams =="
seen_v1=false
seen_v2=false
for i in $(seq 1 12); do
  status="$(gateway_request "rr-${i}")"
  if [[ "${status}" != "200" ]]; then
    echo "Initial request failed with HTTP ${status}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi
  service="$(service_from_body)"
  if [[ "${service}" == "upstream-v1" ]]; then
    seen_v1=true
  elif [[ "${service}" == "upstream-v2" ]]; then
    seen_v2=true
  fi
  if [[ "${seen_v1}" == "true" && "${seen_v2}" == "true" ]]; then
    break
  fi
done
[[ "${seen_v1}" == "true" && "${seen_v2}" == "true" ]] || {
  echo "Expected traffic to both upstreams before failure; v1=${seen_v1} v2=${seen_v2}" >&2
  exit 1
}

echo "== Stopping upstream-v1 =="
docker compose stop upstream-v1

echo "== Driving qualifying failures and ejection =="
ejected=false
observed_502=0
count_200=0
attempt=0
health_json=""
last_category=""

for attempt in $(seq 1 "${EJECTION_DRIVE_MAX_ATTEMPTS}"); do
  status="$(gateway_request "phase5-ejection-${attempt}")"

  case "${status}" in
    200)
      count_200=$((count_200 + 1))
      ;;
    502)
      observed_502=$((observed_502 + 1))
      ;;
    *)
      echo "Unexpected HTTP ${status} while driving ejection (attempt ${attempt})" >&2
      cat "${SMOKE_HEADERS_FILE}" >&2
      cat "${SMOKE_BODY_FILE}" >&2
      exit 1
      ;;
  esac

  health_json="$(fetch_upstream_health)"
  parsed_health=""
  if ! parsed_health="$(read_parsed_target_health "${health_json}" "${target_v1_id}")"; then
    exit 1
  fi
  IFS=$'\t' read -r state failures ejected_until category <<<"${parsed_health}"
  last_category="${category}"

  echo "  attempt=${attempt} status=${status} observed_502=${observed_502} target_state=${state} consecutiveFailures=${failures} lastFailureCategory=${category} ejectedUntil=${ejected_until:-null}"

  if [[ "${state}" == "EJECTED" ]]; then
    ejected=true
    break
  fi
done

if [[ "${ejected}" != "true" ]]; then
  echo "upstream-v1 did not become ejected within ${EJECTION_DRIVE_MAX_ATTEMPTS} attempts" >&2
  print_ejection_diagnostics "${attempt}" "${count_200}" "${observed_502}" "${target_v1_id}" "${health_json}"
  exit 1
fi

if [[ "${observed_502}" -lt "${HEALTH_THRESHOLD}" ]]; then
  echo "Expected at least ${HEALTH_THRESHOLD} observed 502 responses before ejection, got ${observed_502}" >&2
  print_ejection_diagnostics "${attempt}" "${count_200}" "${observed_502}" "${target_v1_id}" "${health_json}"
  exit 1
fi

IFS=$'\t' read -r state failures ejected_until category <<<"$(read_parsed_target_health "${health_json}" "${target_v1_id}")"
if [[ "${state}" != "EJECTED" ]]; then
  echo "Expected EJECTED state after loop, got ${state}" >&2
  print_ejection_diagnostics "${attempt}" "${count_200}" "${observed_502}" "${target_v1_id}" "${health_json}"
  exit 1
fi
if [[ -z "${ejected_until}" ]]; then
  echo "Expected non-null ejectedUntil after ejection" >&2
  print_ejection_diagnostics "${attempt}" "${count_200}" "${observed_502}" "${target_v1_id}" "${health_json}"
  exit 1
fi
if [[ "${category}" != "CONNECTION_REFUSED" && "${last_category}" != "CONNECTION_REFUSED" ]]; then
  echo "Expected lastFailureCategory CONNECTION_REFUSED, got ${category}" >&2
  print_ejection_diagnostics "${attempt}" "${count_200}" "${observed_502}" "${target_v1_id}" "${health_json}"
  exit 1
fi

curl --fail --silent "${GATEWAY_A_URL}/readyz" >/dev/null

echo "== Verifying post-ejection routing avoids upstream-v1 =="
for i in $(seq 1 "${POST_EJECTION_REQUESTS}"); do
  status="$(gateway_request "phase5-post-eject-${i}")"
  if [[ "${status}" != "200" ]]; then
    echo "Post-ejection request ${i} failed with HTTP ${status}" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi
  service="$(service_from_body)"
  if [[ "${service}" != "upstream-v2" ]]; then
    echo "Post-ejection request ${i} hit ${service}, expected upstream-v2" >&2
    cat "${SMOKE_BODY_FILE}" >&2
    exit 1
  fi
done

echo "== Restarting upstream-v1 and waiting for ejection expiry =="
docker compose start upstream-v1
wait_container_healthy "upstream-v1" "upstream-v1"

recovered=false
recovery_attempts=$((EJECTION_SECONDS + 20))
for attempt in $(seq 1 "${recovery_attempts}"); do
  for _ in $(seq 1 2); do
    status="$(gateway_request "phase5-recovery-${attempt}")"
    if [[ "${status}" == "200" ]]; then
      service="$(service_from_body)"
      if [[ "${service}" == "upstream-v1" ]]; then
        recovered=true
        break 2
      fi
    elif [[ "${status}" != "502" ]]; then
      echo "Unexpected HTTP ${status} during recovery attempt ${attempt}" >&2
      cat "${SMOKE_BODY_FILE}" >&2
      exit 1
    fi
  done
  sleep 1
done

if [[ "${recovered}" != "true" ]]; then
  health_json="$(fetch_upstream_health)"
  echo "upstream-v1 did not receive traffic after ejection expiry" >&2
  print_ejection_diagnostics "${recovery_attempts}" "0" "0" "${target_v1_id}" "${health_json}"
  exit 1
fi

health_json="$(fetch_upstream_health)"
IFS=$'\t' read -r state failures ejected_until category <<<"$(read_parsed_target_health "${health_json}" "${target_v1_id}")"
if [[ "${state}" != "HEALTHY" ]]; then
  echo "Expected upstream-v1 HEALTHY after recovery, got ${state}" >&2
  echo "${health_json}" >&2
  exit 1
fi

echo "Phase 5 passive health smoke passed"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
