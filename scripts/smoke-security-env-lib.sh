#!/usr/bin/env bash
# Shared non-production security defaults for smoke and container-candidate flows.
# Never echo pepper or bootstrap token values to stdout/stderr.

SMOKE_API_KEY_PEPPER="${AUTOAPI_API_KEY_PEPPER:-development-only-change-me-not-for-production-use}"
CI_MANAGEMENT_TOKEN_PEPPER="${CI_MANAGEMENT_TOKEN_PEPPER:-autoapi-container-ci-pepper-2026}"
SMOKE_MANAGEMENT_TOKEN_PEPPER="${AUTOAPI_MANAGEMENT_TOKEN_PEPPER:-${CI_MANAGEMENT_TOKEN_PEPPER}}"
SMOKE_BOOTSTRAP_ADMIN_TOKEN="${AUTOAPI_BOOTSTRAP_ADMIN_TOKEN:-smoke-bootstrap-token-change-me-for-local-dev-only}"

load_smoke_security_env() {
  if ((${#SMOKE_MANAGEMENT_TOKEN_PEPPER} < 16)); then
    echo "CI management token pepper must contain at least 16 characters" >&2
    return 1
  fi

  if ((${#SMOKE_BOOTSTRAP_ADMIN_TOKEN} < 16)); then
    echo "Smoke bootstrap admin token must contain at least 16 characters" >&2
    return 1
  fi

  export AUTOAPI_BOOTSTRAP_ADMIN_TOKEN="${SMOKE_BOOTSTRAP_ADMIN_TOKEN}"
  return 0
}

smoke_redact_container_env() {
  sed -E \
    -e 's/(AUTOAPI_MANAGEMENT_TOKEN_PEPPER=)[^[:space:]]+/\1[REDACTED]/g' \
    -e 's/(AUTOAPI_BOOTSTRAP_ADMIN_TOKEN=)[^[:space:]]+/\1[REDACTED]/g' \
    -e 's/(AUTOAPI_API_KEY_PEPPER=)[^[:space:]]+/\1[REDACTED]/g' \
    -e 's/("pepper"[[:space:]]*:[[:space:]]*")[^"]+/\1[REDACTED]/g'
}

control_plane_smoke_security_env_args() {
  printf '%s\n' \
    "-e" "AUTOAPI_MANAGEMENT_TOKEN_PEPPER=${SMOKE_MANAGEMENT_TOKEN_PEPPER}" \
    "-e" "AUTOAPI_BOOTSTRAP_ADMIN_TOKEN=${SMOKE_BOOTSTRAP_ADMIN_TOKEN}"
}
