#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

# shellcheck source=scripts/smoke-security-env-lib.sh
source "${ROOT}/scripts/smoke-security-env-lib.sh"
# shellcheck source=scripts/smoke-curl-lib.sh
source "${ROOT}/scripts/smoke-curl-lib.sh"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"

CANDIDATE_IMAGE="${CANDIDATE_IMAGE:-autoapi-server:ci}"
TEST_NETWORK="${TEST_NETWORK:-autoapi-container-startup-test}"
CONTROL_PLANE_PORT="${CONTROL_PLANE_PORT:-18180}"
POSTGRES_NAME="${POSTGRES_NAME:-autoapi-startup-test-postgres}"
CONTROL_PLANE_NAME="${CONTROL_PLANE_NAME:-autoapi-startup-test-cp}"

cleanup_test_stack() {
  docker rm -f "${CONTROL_PLANE_NAME}" "${POSTGRES_NAME}" >/dev/null 2>&1 || true
  docker network rm "${TEST_NETWORK}" >/dev/null 2>&1 || true
}

start_postgres() {
  docker network create "${TEST_NETWORK}" >/dev/null 2>&1 || true
  docker run -d --name "${POSTGRES_NAME}" --network "${TEST_NETWORK}" \
    -e POSTGRES_DB=autoapi \
    -e POSTGRES_USER=autoapi \
    -e POSTGRES_PASSWORD=autoapi \
    --health-cmd="pg_isready -U autoapi -d autoapi" \
    --health-interval=2s \
    --health-timeout=2s \
    --health-retries=20 \
    postgres:16-alpine >/dev/null
  wait_until "startup-test postgres healthy" 30 1 wait_container_healthy "${POSTGRES_NAME}"
}

run_control_plane_with_env() {
  local extra_env=("$@")
  { set +x; } 2>/dev/null
  docker run -d --name "${CONTROL_PLANE_NAME}" --network "${TEST_NETWORK}" \
    -p "${CONTROL_PLANE_PORT}:8080" \
    -e AUTOAPI_ROLE=control-plane \
    -e AUTOAPI_CONTROLPLANE_ENABLED=true \
    -e AUTOAPI_API_KEY_PEPPER="${SMOKE_API_KEY_PEPPER}" \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://${POSTGRES_NAME}:5432/autoapi \
    -e SPRING_DATASOURCE_USERNAME=autoapi \
    -e SPRING_DATASOURCE_PASSWORD=autoapi \
    -e SPRING_R2DBC_URL=r2dbc:postgresql://${POSTGRES_NAME}:5432/autoapi?sslMode=disable \
    -e SPRING_R2DBC_USERNAME=autoapi \
    -e SPRING_R2DBC_PASSWORD=autoapi \
    "${extra_env[@]}" \
    "${CANDIDATE_IMAGE}" >/dev/null
}

wait_for_container_exit_or_ready() {
  local url="$1"
  local attempts="${2:-30}"
  local attempt

  for attempt in $(seq 1 "${attempts}"); do
    if ! assert_container_still_running "${CONTROL_PLANE_NAME}"; then
      return 0
    fi
    if wait_http_ready "${url}"; then
      return 0
    fi
    sleep 1
  done

  return 1
}

assert_logs_contain() {
  local pattern="$1"
  docker logs "${CONTROL_PLANE_NAME}" 2>&1 | smoke_redact_container_env | grep -q "${pattern}"
}

if ! docker info >/dev/null 2>&1; then
  echo "SKIP container startup tests: Docker unavailable"
  exit 0
fi

if ! docker image inspect "${CANDIDATE_IMAGE}" >/dev/null 2>&1; then
  echo "SKIP container startup tests: candidate image ${CANDIDATE_IMAGE} not found"
  exit 0
fi

load_smoke_security_env

trap cleanup_test_stack EXIT
start_postgres

echo "TEST missing management token pepper fails startup"
run_control_plane_with_env
if wait_for_container_exit_or_ready "http://localhost:${CONTROL_PLANE_PORT}" 45; then
  if assert_container_still_running "${CONTROL_PLANE_NAME}"; then
    echo "FAIL missing pepper: control plane became ready" >&2
    exit 1
  fi
else
  echo "FAIL missing pepper: timed out without exit or readiness" >&2
  exit 1
fi
assert_logs_contain "Management token pepper must be configured" || {
  echo "FAIL missing pepper: expected startup log message" >&2
  exit 1
}
echo "PASS missing management token pepper fails startup"
docker rm -f "${CONTROL_PLANE_NAME}" >/dev/null 2>&1 || true

echo "TEST too-short management token pepper fails startup"
run_control_plane_with_env \
  -e "AUTOAPI_MANAGEMENT_TOKEN_PEPPER=too-short"
if wait_for_container_exit_or_ready "http://localhost:${CONTROL_PLANE_PORT}" 45; then
  if assert_container_still_running "${CONTROL_PLANE_NAME}"; then
    echo "FAIL short pepper: control plane became ready" >&2
    exit 1
  fi
else
  echo "FAIL short pepper: timed out without exit or readiness" >&2
  exit 1
fi
assert_logs_contain "Management token pepper must be configured" || {
  echo "FAIL short pepper: expected startup log message" >&2
  exit 1
}
echo "PASS too-short management token pepper fails startup"
docker rm -f "${CONTROL_PLANE_NAME}" >/dev/null 2>&1 || true

echo "TEST valid CI management token pepper starts successfully"
run_control_plane_with_env \
  -e "AUTOAPI_MANAGEMENT_TOKEN_PEPPER=${SMOKE_MANAGEMENT_TOKEN_PEPPER}" \
  -e "AUTOAPI_BOOTSTRAP_ADMIN_TOKEN=${SMOKE_BOOTSTRAP_ADMIN_TOKEN}"
if ! wait_until "startup-test control plane ready" 60 2 \
  wait_http_ready_for_container "http://localhost:${CONTROL_PLANE_PORT}" "${CONTROL_PLANE_NAME}"; then
  echo "FAIL valid pepper: control plane did not become ready" >&2
  exit 1
fi
logs="$(docker logs "${CONTROL_PLANE_NAME}" 2>&1 | smoke_redact_container_env || true)"
if [[ "${logs}" == *"${SMOKE_MANAGEMENT_TOKEN_PEPPER}"* ]]; then
  echo "FAIL valid pepper: diagnostics leaked configured pepper" >&2
  exit 1
fi
echo "PASS valid CI management token pepper starts successfully"
echo "PASS failure diagnostics do not print configured pepper"

echo "All container startup security tests passed"
