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

assert_eq() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "FAIL ${label}: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
  echo "PASS ${label}"
}

echo "== Wait-lib helper self-tests =="

assert_ok "health=healthy" compose_readiness_status_is_ready "healthy"
assert_ok "health=healthy with whitespace" compose_readiness_status_is_ready $'healthy\n'
assert_ok "no healthcheck running" compose_readiness_status_is_ready "running"
assert_fail "health=starting" compose_readiness_status_is_ready "starting"
assert_fail "health=unhealthy" compose_readiness_status_is_ready "unhealthy"
assert_fail "stopped exited" compose_readiness_status_is_ready "exited"
assert_fail "created" compose_readiness_status_is_ready "created"
assert_fail "missing status" compose_readiness_status_is_ready ""
assert_fail "quoted healthy literal" compose_readiness_status_is_ready '"healthy"'

assert_fail "missing compose service" compose_service_is_healthy "__smoke_wait_lib_missing_service__"

# Regression for the Phase 5 CI bug: healthy must pass without a separate running=true capture.
assert_ok "healthy status without separate running flag" compose_readiness_status_is_ready "healthy"

observed_missing="$(compose_service_observed_status "__smoke_wait_lib_missing_service__")"
assert_eq "observed missing service" "missing" "${observed_missing}"
assert_fail "missing after diagnostic observe" compose_service_is_healthy "__smoke_wait_lib_missing_service__"
echo "PASS diagnostic logging does not change predicate result"

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

if docker info >/dev/null 2>&1; then
  echo "== Docker-backed wait-lib checks =="
  cd "${ROOT}"
  docker compose up -d upstream-v1 >/dev/null 2>&1 || true
  healthy_exit=1
  for attempt in $(seq 1 30); do
    if compose_service_is_healthy upstream-v1; then
      healthy_exit=0
      echo "PASS direct healthy predicate after ${attempt} attempt(s)"
      break
    fi
    sleep 1
  done
  if [[ "${healthy_exit}" -ne 0 ]]; then
    observed="$(compose_service_observed_status upstream-v1)"
    echo "FAIL direct healthy predicate never succeeded (observed=${observed})" >&2
    exit 1
  fi
  set +e
  compose_service_is_healthy upstream-v1
  direct_status=$?
  set -e
  if [[ "${direct_status}" -ne 0 ]]; then
    echo "FAIL compose_service_is_healthy exit code expected 0, got ${direct_status}" >&2
    exit 1
  fi
  echo "PASS compose_service_is_healthy exit code is 0 when healthy"
else
  echo "SKIP Docker-backed wait-lib checks (Docker unavailable)"
fi

echo "All wait-lib helper self-tests passed"
