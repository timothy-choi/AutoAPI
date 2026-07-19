#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/smoke-security-env-lib.sh
source "${ROOT}/scripts/smoke-security-env-lib.sh"

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

assert_fail() {
  local label="$1"
  shift
  if "$@"; then
    echo "FAIL ${label}: expected failure" >&2
    exit 1
  fi
  echo "PASS ${label}"
}

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

assert_ok "default CI pepper length >= 16" test "${#CI_MANAGEMENT_TOKEN_PEPPER}" -ge 16
assert_ok "load_smoke_security_env accepts defaults" load_smoke_security_env

SMOKE_MANAGEMENT_TOKEN_PEPPER="short"
assert_fail "too-short pepper rejected" load_smoke_security_env

SMOKE_MANAGEMENT_TOKEN_PEPPER="autoapi-container-ci-pepper-2026"
SMOKE_BOOTSTRAP_ADMIN_TOKEN="short"
assert_fail "too-short bootstrap token rejected" load_smoke_security_env

SMOKE_BOOTSTRAP_ADMIN_TOKEN="smoke-bootstrap-token-change-me-for-local-dev-only"
assert_ok "valid security env loads" load_smoke_security_env

env_output="$(control_plane_smoke_security_env_args)"
[[ "${env_output}" == *"AUTOAPI_MANAGEMENT_TOKEN_PEPPER="* ]] || {
  echo "FAIL control-plane env args missing management pepper" >&2
  exit 1
}
echo "PASS control-plane env args include management pepper"
[[ "${env_output}" == *"AUTOAPI_BOOTSTRAP_ADMIN_TOKEN="* ]] || {
  echo "FAIL control-plane env args missing bootstrap token" >&2
  exit 1
}
echo "PASS control-plane env args include bootstrap token"

redacted="$(printf '%s\n' \
  'AUTOAPI_MANAGEMENT_TOKEN_PEPPER=autoapi-container-ci-pepper-2026' \
  'AUTOAPI_BOOTSTRAP_ADMIN_TOKEN=smoke-bootstrap-token-change-me-for-local-dev-only' \
  | smoke_redact_container_env)"
[[ "${redacted}" != *"autoapi-container-ci-pepper-2026"* ]] || {
  echo "FAIL redaction leaked management pepper" >&2
  exit 1
}
[[ "${redacted}" != *"smoke-bootstrap-token-change-me-for-local-dev-only"* ]] || {
  echo "FAIL redaction leaked bootstrap token" >&2
  exit 1
}
echo "PASS smoke_redact_container_env redacts secrets"

echo "All smoke-security-env-lib tests passed"
