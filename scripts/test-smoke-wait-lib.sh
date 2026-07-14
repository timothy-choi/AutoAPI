#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/smoke-wait-lib.sh
source "${ROOT}/scripts/smoke-wait-lib.sh"

assert_ok() {
  local label="$1"
  shift
  if "$@"; then
    echo "PASS ${label}"
  else
    echo "FAIL ${label}" >&2
    exit 1
  fi
}

assert_fail() {
  local label="$1"
  shift
  if "$@"; then
    echo "FAIL ${label}: expected failure" >&2
    exit 1
  else
    echo "PASS ${label}"
  fi
}

echo "== Wait-lib helper self-tests =="

assert_ok "running + healthy" compose_health_status_is_ready "true" "healthy"
assert_ok "running without healthcheck" compose_health_status_is_ready "true" "running"
assert_fail "running + starting" compose_health_status_is_ready "true" "starting"
assert_fail "unhealthy" compose_health_status_is_ready "true" "unhealthy"
assert_fail "exited" compose_health_status_is_ready "false" "exited"
assert_fail "not running healthy" compose_health_status_is_ready "false" "healthy"

assert_fail "missing compose service" compose_service_is_healthy "__smoke_wait_lib_missing_service__"

wait_attempts=0
always_fail() {
  wait_attempts="$((wait_attempts + 1))"
  return 1
}

set +e
wait_until "bounded timeout probe" 3 0 always_fail >/dev/null 2>&1
timeout_status=$?
set -e
if [[ ${timeout_status} -eq 0 ]]; then
  echo "FAIL wait_until should time out" >&2
  exit 1
fi
if [[ "${wait_attempts}" -ne 3 ]]; then
  echo "FAIL wait_until attempts expected 3, got ${wait_attempts}" >&2
  exit 1
fi
echo "PASS timeout remains bounded (${wait_attempts} attempts)"

echo "All wait-lib helper self-tests passed"
